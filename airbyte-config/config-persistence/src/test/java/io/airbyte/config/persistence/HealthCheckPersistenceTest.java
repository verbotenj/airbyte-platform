/*
 * Copyright (c) 2020-2025 Airbyte, Inc., all rights reserved.
 */

package io.airbyte.config.persistence;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.airbyte.data.services.HealthCheckService;
import io.airbyte.data.services.impls.jooq.HealthCheckServiceJooqImpl;
import io.airbyte.db.Database;
import java.sql.SQLException;
import org.jooq.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HealthCheckPersistenceTest {

  private Database database;
  private HealthCheckService healthCheckService;

  @BeforeEach
  void beforeEach() throws Exception {
    database = mock(Database.class);
    healthCheckService = new HealthCheckServiceJooqImpl(database);
  }

  @Test
  void testHealthCheckSuccess() throws SQLException {
    final var mResult = mock(Result.class);
    when(database.query(any())).thenReturn(mResult);

    final var check = healthCheckService.healthCheck();
    assertTrue(check);
  }

  @Test
  void testHealthCheckFailure() throws SQLException {
    when(database.query(any())).thenThrow(RuntimeException.class);

    final var check = healthCheckService.healthCheck();
    assertFalse(check);
  }

}
