/*
 * Copyright (c) 2020-2025 Airbyte, Inc., all rights reserved.
 */

package io.airbyte.server.apis.publicapi.helpers

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PathHelperTest {
  @Test
  fun testRemovePublicApiPathPrefix() {
    assertEquals(
      "/v1/health",
      removePublicApiPathPrefix("/api/public/v1/health"),
    )
  }
}
