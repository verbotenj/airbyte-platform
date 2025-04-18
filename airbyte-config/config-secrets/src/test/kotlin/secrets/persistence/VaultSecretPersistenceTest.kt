/*
 * Copyright (c) 2020-2025 Airbyte, Inc., all rights reserved.
 */

package io.airbyte.config.secrets.persistence

import io.airbyte.config.secrets.SecretCoordinate.AirbyteManagedSecretCoordinate
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.testcontainers.vault.VaultContainer
import kotlin.random.Random

internal class VaultSecretPersistenceTest {
  private lateinit var persistence: VaultSecretPersistence
  private lateinit var vaultClient: VaultClient
  private var baseCoordinate: String = "airbyte_test"
  private lateinit var vaultContainer: VaultContainer<*>

  @BeforeEach
  fun setUp() {
    vaultContainer = VaultContainer("hashicorp/vault").withVaultToken("vault-dev-token-id")
    vaultContainer.start()
    val vaultAddress = "http://${vaultContainer.host}:${vaultContainer.firstMappedPort}"
    vaultClient = VaultClient(address = vaultAddress, token = "vault-dev-token-id")
    persistence = VaultSecretPersistence(vaultClient = vaultClient, pathPrefix = "secret/testing")
    baseCoordinate = "airbyte_VaultSecretPersistenceIntegrationTest_coordinate_${Random.nextInt() % 20000}"
  }

  @AfterEach
  fun tearDown() {
    vaultContainer.stop()
  }

  @Test
  fun testReadWriteUpdate() {
    val coordinate1 =
      AirbyteManagedSecretCoordinate(baseCoordinate, 1)

    // try reading non-existent value
    val firstRead: String = persistence.read(coordinate1)
    Assertions.assertThat(firstRead.isBlank()).isTrue()

    // write
    val firstPayload = "abc"
    persistence.write(coordinate1, firstPayload)
    val secondRead: String = persistence.read(coordinate1)
    Assertions.assertThat(secondRead.isNotBlank()).isTrue()
    org.junit.jupiter.api.Assertions
      .assertEquals(firstPayload, secondRead)

    // update
    val secondPayload = "def"
    val coordinate2 =
      AirbyteManagedSecretCoordinate(baseCoordinate, 2)
    persistence.write(coordinate2, secondPayload)
    val thirdRead: String = persistence.read(coordinate2)
    Assertions.assertThat(thirdRead.isNotBlank()).isTrue()
    org.junit.jupiter.api.Assertions
      .assertEquals(secondPayload, thirdRead)
  }
}
