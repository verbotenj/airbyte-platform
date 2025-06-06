/*
 * Copyright (c) 2020-2025 Airbyte, Inc., all rights reserved.
 */

package io.airbyte.config.secrets.test.cases

import io.airbyte.config.secrets.SecretCoordinate.AirbyteManagedSecretCoordinate
import io.airbyte.config.secrets.SecretsTestCase
import io.airbyte.config.secrets.persistence.SecretPersistence
import java.util.function.Consumer

class ArrayTestCase : SecretsTestCase {
  override val name: String
    get() = "array"

  override val firstSecretMap: Map<AirbyteManagedSecretCoordinate, String>
    get() =
      mapOf(
        AirbyteManagedSecretCoordinate(SecretsTestCase.buildBaseCoordinate(uuidIndex = 0), 1) to KEY1,
        AirbyteManagedSecretCoordinate(SecretsTestCase.buildBaseCoordinate(uuidIndex = 1), 1) to KEY2,
        AirbyteManagedSecretCoordinate(SecretsTestCase.buildBaseCoordinate(uuidIndex = 2), 1) to KEY3,
        AirbyteManagedSecretCoordinate(SecretsTestCase.buildBaseCoordinate(uuidIndex = 3), 1) to KEY1,
        AirbyteManagedSecretCoordinate(SecretsTestCase.buildBaseCoordinate(uuidIndex = 4), 1) to KEY2,
        AirbyteManagedSecretCoordinate(SecretsTestCase.buildBaseCoordinate(uuidIndex = 5), 1) to KEY3,
      )
  override val secondSecretMap: Map<AirbyteManagedSecretCoordinate, String>
    get() {
      return mapOf(
        AirbyteManagedSecretCoordinate(SecretsTestCase.buildBaseCoordinate(uuidIndex = 0), 2) to "key8",
        AirbyteManagedSecretCoordinate(SecretsTestCase.buildBaseCoordinate(uuidIndex = 1), 2) to "key9",
        AirbyteManagedSecretCoordinate(SecretsTestCase.buildBaseCoordinate(uuidIndex = 2), 2) to "key10",
        AirbyteManagedSecretCoordinate(SecretsTestCase.buildBaseCoordinate(uuidIndex = 3), 2) to "key5",
        AirbyteManagedSecretCoordinate(SecretsTestCase.buildBaseCoordinate(uuidIndex = 4), 2) to "key6",
        AirbyteManagedSecretCoordinate(SecretsTestCase.buildBaseCoordinate(uuidIndex = 5), 2) to "key7",
      )
    }
  override val persistenceUpdater: Consumer<SecretPersistence>
    get() {
      return Consumer<SecretPersistence> { secretPersistence: SecretPersistence ->
        secretPersistence.write(AirbyteManagedSecretCoordinate(SecretsTestCase.buildBaseCoordinate(uuidIndex = 0), 1), KEY1)
        secretPersistence.write(AirbyteManagedSecretCoordinate(SecretsTestCase.buildBaseCoordinate(uuidIndex = 1), 1), KEY2)
        secretPersistence.write(AirbyteManagedSecretCoordinate(SecretsTestCase.buildBaseCoordinate(uuidIndex = 2), 1), KEY3)
        secretPersistence.write(AirbyteManagedSecretCoordinate(SecretsTestCase.buildBaseCoordinate(uuidIndex = 3), 1), KEY1)
        secretPersistence.write(AirbyteManagedSecretCoordinate(SecretsTestCase.buildBaseCoordinate(uuidIndex = 4), 1), KEY2)
        secretPersistence.write(AirbyteManagedSecretCoordinate(SecretsTestCase.buildBaseCoordinate(uuidIndex = 5), 1), KEY3)
      }
    }

  companion object {
    private const val KEY1: String = "key1"
    private const val KEY2: String = "key2"
    private const val KEY3: String = "key3"
  }
}
