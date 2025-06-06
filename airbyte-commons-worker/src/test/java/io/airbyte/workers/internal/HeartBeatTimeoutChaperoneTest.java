/*
 * Copyright (c) 2020-2025 Airbyte, Inc., all rights reserved.
 */

package io.airbyte.workers.internal;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.airbyte.featureflag.ShouldFailSyncIfHeartbeatFailure;
import io.airbyte.metrics.MetricAttribute;
import io.airbyte.metrics.MetricClient;
import io.airbyte.metrics.OssMetricsRegistry;
import io.airbyte.metrics.lib.MetricTags;
import io.airbyte.workers.context.ReplicationInputFeatureFlagReader;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class HeartBeatTimeoutChaperoneTest {

  private final HeartbeatMonitor heartbeatMonitor = mock(HeartbeatMonitor.class);
  private final Duration timeoutCheckDuration = Duration.ofMillis(1);

  private final ReplicationInputFeatureFlagReader replicationInputFeatureFlagReader = mock(ReplicationInputFeatureFlagReader.class);
  private final UUID connectionId = UUID.randomUUID();
  private final MetricClient metricClient = mock(MetricClient.class);

  @Test
  void testFailHeartbeat() {
    when(replicationInputFeatureFlagReader.read(eq(ShouldFailSyncIfHeartbeatFailure.INSTANCE))).thenReturn(true);
    when(heartbeatMonitor.getHeartbeatFreshnessThreshold()).thenReturn(Duration.ofSeconds(1));

    final HeartbeatTimeoutChaperone heartbeatTimeoutChaperone = new HeartbeatTimeoutChaperone(
        heartbeatMonitor,
        timeoutCheckDuration,
        replicationInputFeatureFlagReader,
        Optional.of(() -> {}),
        connectionId,
        metricClient);

    final var thrown = assertThrows(HeartbeatTimeoutChaperone.HeartbeatTimeoutException.class,
        () -> heartbeatTimeoutChaperone.runWithHeartbeatThread(CompletableFuture.runAsync(() -> {
          try {
            Thread.sleep(Long.MAX_VALUE);
          } catch (final InterruptedException e) {
            throw new RuntimeException(e);
          }
        })));

    assertEquals("Last record seen 0 seconds ago, exceeding the threshold of 1 second.", thrown.getMessage());

    verify(metricClient, times(1)).count(OssMetricsRegistry.SOURCE_HEARTBEAT_FAILURE,
        new MetricAttribute(MetricTags.CONNECTION_ID, connectionId.toString()),
        new MetricAttribute(MetricTags.KILLED, "true"),
        new MetricAttribute(MetricTags.SOURCE_IMAGE, "docker image"));
  }

  @Test
  void testNotFailingHeartbeat() {
    final HeartbeatTimeoutChaperone heartbeatTimeoutChaperone = new HeartbeatTimeoutChaperone(
        heartbeatMonitor,
        timeoutCheckDuration,
        replicationInputFeatureFlagReader,
        Optional.of(() -> {
          try {
            Thread.sleep(Long.MAX_VALUE);
          } catch (final InterruptedException e) {
            throw new RuntimeException(e);
          }
        }),
        connectionId,
        metricClient);
    assertDoesNotThrow(() -> heartbeatTimeoutChaperone.runWithHeartbeatThread(CompletableFuture.runAsync(() -> {})));
  }

  @Test
  void testNotFailingHeartbeatIfFalseFlag() {
    when(replicationInputFeatureFlagReader.read(eq(ShouldFailSyncIfHeartbeatFailure.INSTANCE))).thenReturn(false);
    final HeartbeatTimeoutChaperone heartbeatTimeoutChaperone = new HeartbeatTimeoutChaperone(
        heartbeatMonitor,
        timeoutCheckDuration,
        replicationInputFeatureFlagReader,
        Optional.of(() -> {}),
        connectionId,
        metricClient);
    assertDoesNotThrow(() -> heartbeatTimeoutChaperone.runWithHeartbeatThread(CompletableFuture.runAsync(() -> {
      try {
        Thread.sleep(1000);
      } catch (final InterruptedException e) {
        throw new RuntimeException(e);
      }
    })));
  }

  @Test
  void testMonitor() {
    final HeartbeatTimeoutChaperone heartbeatTimeoutChaperone = new HeartbeatTimeoutChaperone(
        heartbeatMonitor,
        timeoutCheckDuration,
        replicationInputFeatureFlagReader,
        connectionId,
        "docker image",
        metricClient);
    when(replicationInputFeatureFlagReader.read(eq(ShouldFailSyncIfHeartbeatFailure.INSTANCE))).thenReturn(true);
    when(heartbeatMonitor.isBeating()).thenReturn(Optional.of(false));
    assertDoesNotThrow(() -> CompletableFuture.runAsync(heartbeatTimeoutChaperone::monitor).get(1000, TimeUnit.MILLISECONDS));
  }

  @Test
  void testMonitorDontFailIfDontStopBeating() {
    final HeartbeatTimeoutChaperone heartbeatTimeoutChaperone = new HeartbeatTimeoutChaperone(
        heartbeatMonitor,
        timeoutCheckDuration,
        replicationInputFeatureFlagReader,
        connectionId,
        "docker image",
        metricClient);
    when(replicationInputFeatureFlagReader.read(eq(ShouldFailSyncIfHeartbeatFailure.INSTANCE))).thenReturn(false);
    when(heartbeatMonitor.isBeating()).thenReturn(Optional.of(true), Optional.of(false));

    assertDoesNotThrow(() -> CompletableFuture.runAsync(heartbeatTimeoutChaperone::monitor).get(1000, TimeUnit.MILLISECONDS));
  }

}
