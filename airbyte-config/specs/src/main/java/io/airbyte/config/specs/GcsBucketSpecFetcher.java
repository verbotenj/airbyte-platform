/*
 * Copyright (c) 2020-2025 Airbyte, Inc., all rights reserved.
 */

package io.airbyte.config.specs;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.api.client.util.Preconditions;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.Storage;
import com.google.common.annotations.VisibleForTesting;
import io.airbyte.commons.json.Jsons;
import io.airbyte.config.Configs;
import io.airbyte.protocol.models.AirbyteProtocolSchema;
import io.airbyte.protocol.models.v0.ConnectorSpecification;
import io.airbyte.validation.json.JsonSchemaValidator;
import io.airbyte.validation.json.JsonValidationException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Fetch connector specs from Airbyte GCS Bucket where specs are stored when connectors are
 * published.
 */
public class GcsBucketSpecFetcher {

  private static final Logger LOGGER = LoggerFactory.getLogger(GcsBucketSpecFetcher.class);

  // these filenames must match default_spec_file and cloud_spec_file in manage.sh
  public static final String DEFAULT_SPEC_FILE = "spec.json";
  public static final String CLOUD_SPEC_FILE = "spec.cloud.json";

  private final Storage storage;
  private final String bucketName;
  private final Configs.AirbyteEdition airbyteEdition;

  public GcsBucketSpecFetcher(final Storage storage, final String bucketName) {
    this.storage = storage;
    this.bucketName = bucketName;
    this.airbyteEdition = Configs.AirbyteEdition.COMMUNITY;
  }

  /**
   * This constructor is used by airbyte-cloud to fetch cloud-specific spec files.
   */
  public GcsBucketSpecFetcher(final Storage storage, final String bucketName, final Configs.AirbyteEdition airbyteEdition) {
    this.storage = storage;
    this.bucketName = bucketName;
    this.airbyteEdition = airbyteEdition;
  }

  /**
   * Get name of bucket where specs are stored.
   *
   * @return name of bucket where specs are stored
   */
  public String getBucketName() {
    return bucketName;
  }

  /**
   * Fetch spec for docker image.
   *
   * @param dockerImage of a connector
   * @return connector spec of docker image. if not found, empty optional.
   */
  public Optional<ConnectorSpecification> attemptFetch(final String dockerImage) {
    final String[] dockerImageComponents = dockerImage.split(":");
    Preconditions.checkArgument(dockerImageComponents.length == 2, "Invalidate docker image: " + dockerImage);
    final String dockerImageName = dockerImageComponents[0];
    final String dockerImageTag = dockerImageComponents[1];

    final Optional<Blob> specAsBlob = getSpecAsBlob(dockerImageName, dockerImageTag);

    if (specAsBlob.isEmpty()) {
      LOGGER.debug("Spec not found in bucket storage");
      return Optional.empty();
    }

    final String specAsString = new String(specAsBlob.get().getContent(), StandardCharsets.UTF_8);
    try {
      validateConfig(Jsons.deserialize(specAsString));
    } catch (final JsonValidationException e) {
      LOGGER.error("Received invalid spec from bucket store. {}", e.toString());
      return Optional.empty();
    }
    return Optional.of(Jsons.deserialize(specAsString, ConnectorSpecification.class));
  }

  @VisibleForTesting
  Optional<Blob> getSpecAsBlob(final String dockerImageName, final String dockerImageTag) {
    if (airbyteEdition == Configs.AirbyteEdition.CLOUD) {
      final Optional<Blob> cloudSpecAsBlob = getSpecAsBlob(dockerImageName, dockerImageTag, CLOUD_SPEC_FILE, Configs.AirbyteEdition.CLOUD);
      if (cloudSpecAsBlob.isPresent()) {
        LOGGER.info("Found cloud specific spec: {} {}", bucketName, cloudSpecAsBlob);
        return cloudSpecAsBlob;
      }
    }
    return getSpecAsBlob(dockerImageName, dockerImageTag, DEFAULT_SPEC_FILE, Configs.AirbyteEdition.COMMUNITY);
  }

  @VisibleForTesting
  Optional<Blob> getSpecAsBlob(final String dockerImageName,
                               final String dockerImageTag,
                               final String specFile,
                               final Configs.AirbyteEdition airbyteEdition) {
    final Path specPath = Path.of("specs").resolve(dockerImageName).resolve(dockerImageTag).resolve(specFile);
    LOGGER.debug("Checking path for cached {} spec: {} {}", airbyteEdition.name(), bucketName, specPath);
    final Blob specAsBlob = storage.get(bucketName, specPath.toString());
    if (specAsBlob != null) {
      return Optional.of(specAsBlob);
    }
    return Optional.empty();
  }

  private static void validateConfig(final JsonNode json) throws JsonValidationException {
    final JsonSchemaValidator jsonSchemaValidator = new JsonSchemaValidator();
    final JsonNode specJsonSchema = JsonSchemaValidator.getSchema(AirbyteProtocolSchema.PROTOCOL.getFile(), "ConnectorSpecification");
    jsonSchemaValidator.ensure(specJsonSchema, json);
  }

}
