/*
 * Copyright (c) 2020-2025 Airbyte, Inc., all rights reserved.
 */

package io.airbyte.workers.internal;

import io.airbyte.commons.json.Jsons;
import io.airbyte.protocol.models.v0.AirbyteMessage;
import java.io.BufferedWriter;
import java.io.IOException;

/**
 * Default writer that serializes airbyte messages to JSON.
 */
public class DefaultAirbyteMessageBufferedWriter implements AirbyteMessageBufferedWriter {

  protected final BufferedWriter writer;

  public DefaultAirbyteMessageBufferedWriter(final BufferedWriter writer) {
    this.writer = writer;
  }

  @Override
  public void write(final AirbyteMessage message) throws IOException {
    writer.write(Jsons.serialize(message));
    writer.newLine();
  }

  @Override
  public void flush() throws IOException {
    writer.flush();
  }

  @Override
  public void close() throws IOException {
    writer.close();
  }

}
