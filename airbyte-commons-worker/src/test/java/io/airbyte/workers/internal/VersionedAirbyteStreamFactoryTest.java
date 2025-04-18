/*
 * Copyright (c) 2020-2025 Airbyte, Inc., all rights reserved.
 */

package io.airbyte.workers.internal;

import static io.airbyte.workers.internal.VersionedAirbyteStreamFactory.CONNECTION_ID_NOT_PRESENT;
import static io.airbyte.workers.internal.VersionedAirbyteStreamFactory.MALFORMED_AIRBYTE_RECORD_LOG_MESSAGE;
import static io.airbyte.workers.internal.VersionedAirbyteStreamFactory.MALFORMED_NON_AIRBYTE_RECORD_LOG_MESSAGE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import io.airbyte.commons.json.Jsons;
import io.airbyte.commons.logging.MdcScope.Builder;
import io.airbyte.commons.protocol.AirbyteMessageMigrator;
import io.airbyte.commons.protocol.AirbyteMessageSerDeProvider;
import io.airbyte.commons.protocol.AirbyteProtocolVersionedMigratorFactory;
import io.airbyte.commons.protocol.ConfiguredAirbyteCatalogMigrator;
import io.airbyte.commons.protocol.serde.AirbyteMessageV0Deserializer;
import io.airbyte.commons.protocol.serde.AirbyteMessageV0Serializer;
import io.airbyte.commons.version.Version;
import io.airbyte.metrics.MetricClient;
import io.airbyte.protocol.models.v0.AirbyteLogMessage;
import io.airbyte.protocol.models.v0.AirbyteMessage;
import io.airbyte.workers.helper.GsonPksExtractor;
import io.airbyte.workers.testutils.AirbyteMessageUtils;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.platform.commons.util.ClassLoaderUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings("PMD.InvalidLogMessageFormat")
class VersionedAirbyteStreamFactoryTest {

  AirbyteMessageSerDeProvider serDeProvider;
  AirbyteProtocolVersionedMigratorFactory migratorFactory;

  private final GsonPksExtractor gsonPksExtractor = mock(GsonPksExtractor.class);

  @Nested
  @DisplayName("Test Correct AirbyteMessage Parsing Behavior")
  class ParseMessages {

    private static final String STREAM_NAME = "user_preferences";
    private static final String FIELD_NAME = "favorite_color";
    private Logger logger;

    @BeforeEach
    void setup() {
      logger = spy(LoggerFactory.getLogger(VersionedAirbyteStreamFactoryTest.class));
    }

    @Test
    void testValid() {
      final AirbyteMessage record1 = AirbyteMessageUtils.createRecordMessage(STREAM_NAME, FIELD_NAME, "green");
      final Stream<AirbyteMessage> messageStream = stringToMessageStream(Jsons.serialize(record1));
      final Stream<AirbyteMessage> expectedStream = Stream.of(record1);

      assertEquals(expectedStream.collect(Collectors.toList()), messageStream.collect(Collectors.toList()));
    }

    @Test
    void testValidBigInteger() {
      final AirbyteMessage record = AirbyteMessageUtils.createRecordMessage(STREAM_NAME, FIELD_NAME,
          BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE));

      final Stream<AirbyteMessage> messageStream = stringToMessageStream(Jsons.serialize(record));
      final Stream<AirbyteMessage> expectedStream = Stream.of(record);

      assertEquals(expectedStream.collect(Collectors.toList()), messageStream.collect(Collectors.toList()));
    }

    @Test
    void testValidBigDecimal() {
      final AirbyteMessage record = AirbyteMessageUtils.createRecordMessage(STREAM_NAME, FIELD_NAME,
          new BigDecimal("1234567890.1234567890"));

      final Stream<AirbyteMessage> messageStream = stringToMessageStream(Jsons.serialize(record));
      final Stream<AirbyteMessage> expectedStream = Stream.of(record);

      assertEquals(expectedStream.collect(Collectors.toList()), messageStream.collect(Collectors.toList()));
    }

    @Test
    void testLoggingLine() {
      final String invalidRecord = "invalid line";

      final Stream<AirbyteMessage> messageStream = stringToMessageStream(invalidRecord);

      assertEquals(Collections.emptyList(), messageStream.collect(Collectors.toList()));
      verify(logger).info(MALFORMED_NON_AIRBYTE_RECORD_LOG_MESSAGE, CONNECTION_ID_NOT_PRESENT, invalidRecord);
    }

    @Test
    void testLoggingLevel() {
      final AirbyteMessage logMessage = AirbyteMessageUtils.createLogMessage(AirbyteLogMessage.Level.WARN, "warning");

      final Stream<AirbyteMessage> messageStream = stringToMessageStream(Jsons.serialize(logMessage));

      assertEquals(Collections.emptyList(), messageStream.collect(Collectors.toList()));
      verify(logger).warn("warning");
    }

    @Test
    void testFailDeserializationObvious() {
      final String invalidRecord = "{ \"type\": \"abc\"}";

      final Stream<AirbyteMessage> messageStream = stringToMessageStream(invalidRecord);

      assertEquals(Collections.emptyList(), messageStream.collect(Collectors.toList()));
      verify(logger).info(MALFORMED_NON_AIRBYTE_RECORD_LOG_MESSAGE, CONNECTION_ID_NOT_PRESENT, invalidRecord);
    }

    @Test
    void testFailDeserializationSubtle() {
      final String invalidRecord = "{\"type\": \"spec\", \"data\": {}}";

      final Stream<AirbyteMessage> messageStream = stringToMessageStream(invalidRecord);

      assertEquals(Collections.emptyList(), messageStream.collect(Collectors.toList()));
      verify(logger).info(MALFORMED_NON_AIRBYTE_RECORD_LOG_MESSAGE, CONNECTION_ID_NOT_PRESENT, invalidRecord);
    }

    @Test
    void testFailValidation() {
      final String invalidRecord = "{ \"fish\": \"tuna\"}";

      final Stream<AirbyteMessage> messageStream = stringToMessageStream(invalidRecord);

      assertEquals(Collections.emptyList(), messageStream.collect(Collectors.toList()));

      verify(logger, atLeastOnce()).info(anyString(), anyString(), anyString());
      verify(logger, atLeastOnce()).error(anyString(), anyString());
    }

    @ParameterizedTest
    @ValueSource(strings = {
      // Missing closing bracket.
      "{\"type\":\"RECORD\", \"record\": {\"stream\": \"transactions\", \"data\": {\"amount\": \"100.00\"",
      // Infinity is invalid json.
      "{\"type\":\"RECORD\", \"record\": {\"stream\": \"transactions\", \"data\": {\"transaction_id\": Infinity }}}",
      // Infinity is invalid json. Python code generates json strings with a space.
      "{\"type\": \"RECORD\", \"record\": {\"stream\": \"transactions\", \"data\": {\"transaction_id\": Infinity }}}",
      // Infinity is invalid json. Lowercase types.
      "{\"type\": \"record\", \"record\": {\"stream\": \"transactions\", \"data\": {\"transaction_id\": Infinity }}}"})
    void testMalformedRecordShouldOnlyDebugLog(final String invalidRecord) {
      stringToMessageStream(invalidRecord).collect(Collectors.toList());
      verifyBlankedRecordRecordWarning();
      verify(logger).debug(MALFORMED_AIRBYTE_RECORD_LOG_MESSAGE, CONNECTION_ID_NOT_PRESENT, invalidRecord);
    }

    private VersionedAirbyteStreamFactory getFactory() {
      return VersionedAirbyteStreamFactory
          .noMigrationVersionedAirbyteStreamFactory(
              logger,
              new Builder(),
              new VersionedAirbyteStreamFactory.InvalidLineFailureConfiguration(false),
              gsonPksExtractor,
              mock(MetricClient.class));
    }

    private static final String VALID_MESSAGE_TEMPLATE =
        """
        {"type":"RECORD","record":{"namespace":"public","stream":"documents","data":{"value":"%s"},"emitted_at":1695224525688}}
        """;

    @Test
    void testToAirbyteMessageValid() {
      final String messageLine = String.format(VALID_MESSAGE_TEMPLATE, "hello");
      Assertions.assertThat(getFactory().toAirbyteMessage(messageLine)).hasSize(1);
    }

    @Test
    void testToAirbyteMessageRandomLog() {
      final String randomLog = "I should not be send on the same channel than the airbyte messages";
      Assertions.assertThat(getFactory().toAirbyteMessage(randomLog))
          .isEmpty();
      verify(logger).info(MALFORMED_NON_AIRBYTE_RECORD_LOG_MESSAGE, CONNECTION_ID_NOT_PRESENT, randomLog);
    }

    @Test
    void testToAirbyteMessageMixedUpRecordShouldOnlyDebugLog() {
      final String messageLine = "It shouldn't be here" + String.format(VALID_MESSAGE_TEMPLATE, "hello");
      getFactory().toAirbyteMessage(messageLine);
      verifyBlankedRecordRecordWarning();
      verify(logger).debug(MALFORMED_AIRBYTE_RECORD_LOG_MESSAGE, CONNECTION_ID_NOT_PRESENT, messageLine);
    }

    @Test
    void testToAirbyteMessageMixedUpRecordFailureDisable() {
      final String messageLine = "It shouldn't be here" + String.format(VALID_MESSAGE_TEMPLATE, "hello");
      Assertions.assertThat(getFactory().toAirbyteMessage(messageLine)).isEmpty();
      verifyBlankedRecordRecordWarning();
      verify(logger).debug(MALFORMED_AIRBYTE_RECORD_LOG_MESSAGE, CONNECTION_ID_NOT_PRESENT, messageLine);
    }

    @Test
    void testToAirbyteMessageVeryLongMessageDontFail() {
      // This roughly corresponds to a 25_000_000 * 2 bytes string.
      final StringBuilder longStringBuilder = new StringBuilder(25_000_000);
      for (int i = 0; i < 25_000_000; i++) {
        longStringBuilder.append("a");
      }
      final String messageLine = String.format(VALID_MESSAGE_TEMPLATE, longStringBuilder);
      Assertions.assertThat(getFactory().toAirbyteMessage(messageLine)).isNotEmpty();
    }

    private Stream<AirbyteMessage> stringToMessageStream(final String inputString) {
      final InputStream inputStream = new ByteArrayInputStream(inputString.getBytes(StandardCharsets.UTF_8));
      final BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));

      final var stream = VersionedAirbyteStreamFactory
          .noMigrationVersionedAirbyteStreamFactory(
              logger,
              new Builder(),
              new VersionedAirbyteStreamFactory.InvalidLineFailureConfiguration(false),
              gsonPksExtractor,
              mock(MetricClient.class))
          .create(bufferedReader);
      verifyStreamHeader();
      return stream;
    }

    private void verifyBlankedRecordRecordWarning() {
      verify(logger).warn("Could not parse the string received from source, it seems to be a record message");
    }

    private void verifyStreamHeader() {
      verify(logger).info("Reading messages from protocol version {}{}", "0.2.0", "");
    }

  }

  @Nested
  @DisplayName("Test Correct Protocol Migration Behavior")
  class MigrateMessages {

    @BeforeEach
    void beforeEach() {
      serDeProvider = spy(new AirbyteMessageSerDeProvider(
          List.of(new AirbyteMessageV0Deserializer()),
          List.of(new AirbyteMessageV0Serializer())));
      serDeProvider.initialize();

      final AirbyteMessageMigrator airbyteMessageMigrator = new AirbyteMessageMigrator(
          // TODO once data types v1 is re-enabled, this test should contain the migration
          List.of(/* new AirbyteMessageMigrationV1() */));
      airbyteMessageMigrator.initialize();
      final ConfiguredAirbyteCatalogMigrator configuredAirbyteCatalogMigrator = new ConfiguredAirbyteCatalogMigrator(
          // TODO once data types v1 is re-enabled, this test should contain the migration
          List.of(/* new ConfiguredAirbyteCatalogMigrationV1() */));
      configuredAirbyteCatalogMigrator.initialize();
      migratorFactory = spy(new AirbyteProtocolVersionedMigratorFactory(airbyteMessageMigrator, configuredAirbyteCatalogMigrator));
    }

    @Test
    void testCreate() {
      final Version initialVersion = new Version("0.1.2");
      final VersionedAirbyteStreamFactory<?> streamFactory =
          new VersionedAirbyteStreamFactory<>(serDeProvider, migratorFactory, initialVersion, Optional.empty(), Optional.empty(),
              new VersionedAirbyteStreamFactory.InvalidLineFailureConfiguration(false),
              gsonPksExtractor,
              mock(MetricClient.class));

      final BufferedReader bufferedReader = new BufferedReader(new StringReader(""));
      streamFactory.create(bufferedReader);

      verify(migratorFactory).getAirbyteMessageMigrator(initialVersion);
    }

    @Test
    void testCreateWithVersionDetection() {
      final Version initialVersion = new Version("0.0.0");
      final VersionedAirbyteStreamFactory<?> streamFactory =
          new VersionedAirbyteStreamFactory<>(serDeProvider, migratorFactory, initialVersion, Optional.empty(), Optional.empty(),
              new VersionedAirbyteStreamFactory.InvalidLineFailureConfiguration(false),
              gsonPksExtractor,
              mock(MetricClient.class))
                  .withDetectVersion(true);

      final BufferedReader bufferedReader =
          getBuffereredReader("version-detection/logs-with-version.jsonl");
      final Stream<AirbyteMessage> stream = streamFactory.create(bufferedReader);

      final long messageCount = stream.toList().size();
      assertEquals(1, messageCount);
    }

    @Test
    void testCreateWithVersionDetectionFallback() {
      final Version initialVersion = new Version("0.0.6");
      final VersionedAirbyteStreamFactory<?> streamFactory =
          new VersionedAirbyteStreamFactory<>(serDeProvider, migratorFactory, initialVersion, Optional.empty(), Optional.empty(),
              new VersionedAirbyteStreamFactory.InvalidLineFailureConfiguration(false),
              gsonPksExtractor,
              mock(MetricClient.class))
                  .withDetectVersion(true);

      final BufferedReader bufferedReader =
          getBuffereredReader("version-detection/logs-without-version.jsonl");
      final Stream<AirbyteMessage> stream = streamFactory.create(bufferedReader);

      final long messageCount = stream.toList().size();
      assertEquals(1, messageCount);
    }

    @Test
    void testCreateWithVersionDetectionWithoutSpecMessage() {
      final Version initialVersion = new Version("0.0.1");
      final VersionedAirbyteStreamFactory<?> streamFactory =
          new VersionedAirbyteStreamFactory<>(serDeProvider, migratorFactory, initialVersion, Optional.empty(), Optional.empty(),
              new VersionedAirbyteStreamFactory.InvalidLineFailureConfiguration(false),
              gsonPksExtractor,
              mock(MetricClient.class))
                  .withDetectVersion(true);

      final BufferedReader bufferedReader =
          getBuffereredReader("version-detection/logs-without-spec-message.jsonl");
      final Stream<AirbyteMessage> stream = streamFactory.create(bufferedReader);

      final long messageCount = stream.toList().size();
      assertEquals(2, messageCount);
    }

    BufferedReader getBuffereredReader(final String resourceFile) {
      return new BufferedReader(
          new InputStreamReader(
              ClassLoaderUtils.getDefaultClassLoader().getResourceAsStream(resourceFile),
              Charset.defaultCharset()));
    }

  }

}
