/*
 * Copyright (c) 2020-2025 Airbyte, Inc., all rights reserved.
 */

package io.airbyte.server.apis.publicapi.controllers

import io.airbyte.commons.auth.AuthRoleConstants
import io.airbyte.commons.auth.OrganizationAuthRole
import io.airbyte.commons.auth.config.TokenExpirationConfig
import io.airbyte.commons.entitlements.Entitlement
import io.airbyte.commons.entitlements.LicenseEntitlementChecker
import io.airbyte.commons.json.Jsons
import io.airbyte.commons.server.authorization.ApiAuthorizationHelper
import io.airbyte.commons.server.authorization.Scope
import io.airbyte.commons.server.scheduling.AirbyteTaskExecutors
import io.airbyte.commons.server.support.CurrentUserService
import io.airbyte.persistence.job.WorkspaceHelper
import io.airbyte.publicApi.server.generated.apis.EmbeddedWidgetApi
import io.airbyte.publicApi.server.generated.models.EmbeddedWidgetRequest
import io.airbyte.server.auth.TokenScopeClaim
import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.Requires
import io.micronaut.http.annotation.Controller
import io.micronaut.http.context.ServerRequestContext
import io.micronaut.scheduling.annotation.ExecuteOn
import io.micronaut.security.annotation.Secured
import io.micronaut.security.rules.SecurityRule
import io.micronaut.security.token.jwt.generator.JwtTokenGenerator
import jakarta.inject.Named
import jakarta.ws.rs.core.Response
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.time.Clock
import java.time.temporal.ChronoUnit
import java.util.Base64
import java.util.UUID

// Airbyte Embedded and token generation requires auth,
// so if auth isn't enabled, then this controller is not available.
@Requires(property = "micronaut.security.enabled", value = "true")
@Requires(property = "micronaut.security.token.jwt.enabled", value = "true")
@Secured(SecurityRule.IS_AUTHENTICATED)
@Controller
class EmbeddedController(
  val jwtTokenGenerator: JwtTokenGenerator,
  val apiAuthorizationHelper: ApiAuthorizationHelper,
  val tokenExpirationConfig: TokenExpirationConfig,
  val currentUserService: CurrentUserService,
  val workspaceHelper: WorkspaceHelper,
  val licenseEntitlementChecker: LicenseEntitlementChecker,
  @Named("airbyteUrl") val airbyteUrl: String,
  @Property(name = "airbyte.auth.token-issuer") private val tokenIssuer: String,
) : EmbeddedWidgetApi {
  var clock: Clock = Clock.systemUTC()

  @ExecuteOn(AirbyteTaskExecutors.IO)
  override fun getEmbeddedWidget(req: EmbeddedWidgetRequest): Response {
    val organizationId = workspaceHelper.getOrganizationForWorkspace(req.workspaceId)

    licenseEntitlementChecker.ensureEntitled(
      organizationId,
      Entitlement.CONFIG_TEMPLATE_ENDPOINTS,
    )

    // Ensure the user is admin of the org that owns the requested workspace.
    apiAuthorizationHelper.ensureUserHasAnyRequiredRoleOrThrow(
      Scope.ORGANIZATION,
      listOf(organizationId.toString()),
      setOf(OrganizationAuthRole.ORGANIZATION_ADMIN),
    )

    val currentUser = currentUserService.getCurrentUser()
    val externalUserId = req.externalUserId ?: UUID.randomUUID().toString()

    val widgetUrl =
      airbyteUrl
        .toHttpUrlOrNull()!!
        .newBuilder()
        .encodedPath("/embedded-widget")
        .addQueryParameter("workspaceId", req.workspaceId.toString())
        .addQueryParameter("allowedOrigin", req.allowedOrigin)
        .toString()

    val data =
      mapOf(
        "token" to generateToken(req.workspaceId.toString(), currentUser.authUserId, externalUserId),
        "widgetUrl" to widgetUrl,
      )
    val json = Jsons.serialize(data)

    // For debugging/dev, it's sometimes easier to just have the raw JSON
    // instead of the base64-encoded string. That's available by passing "?debug" in the URL.
    if (isDebug()) {
      return json.ok()
    }

    // This endpoint is different from most – it returns an "opaque" base64-encoded string,
    // which decodes to a JSON object. This is because there is an intermediate party (Operator)
    // which needs to pass this value to the Embedded widget code.
    // Encoding this as an opaque string means the Operator is less likely to have
    // issues passing this along if the fields are changed.
    val encoded = Base64.getEncoder().encodeToString(json.toByteArray())
    return encoded.ok()
  }

  private fun isDebug(): Boolean = ServerRequestContext.currentRequest<Any>().map { it.parameters.contains("debug") }.orElse(false)

  private fun generateToken(
    workspaceId: String,
    currentUserId: String,
    externalUserId: String,
  ): String =
    jwtTokenGenerator
      .generateToken(
        mapOf(
          "iss" to tokenIssuer,
          "aud" to "airbyte-server",
          "sub" to currentUserId,
          "typ" to "io.airbyte.embedded.v1",
          "act" to mapOf("sub" to externalUserId),
          TokenScopeClaim.CLAIM_ID to TokenScopeClaim(workspaceId),
          "roles" to listOf(AuthRoleConstants.EMBEDDED_END_USER),
          "exp" to clock.instant().plus(tokenExpirationConfig.embeddedTokenExpirationInMinutes, ChronoUnit.MINUTES).epochSecond,
        ),
      ).orElseThrow {
        IllegalStateException("Could not generate token")
      }
}

private fun <T> T.ok() = Response.status(Response.Status.OK.statusCode).entity(this).build()
