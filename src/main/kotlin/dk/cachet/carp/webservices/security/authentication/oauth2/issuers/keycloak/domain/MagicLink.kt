package dk.cachet.carp.webservices.security.authentication.oauth2.issuers.keycloak.domain

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy
import com.fasterxml.jackson.databind.annotation.JsonNaming

/**
 * A subset of the response representation of the
 * [magic link resource](https://github.com/p2-inc/keycloak-magic-link) for the anonymous participants feature.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class MagicLinkResponse(
    @JsonProperty("userId")
    val userId: String?,
    val link: String?,
)

/**
 * A subset of the request representation of the
 * [magic link resource](https://github.com/p2-inc/keycloak-magic-link) for the anonymous participants feature.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(SnakeCaseStrategy::class)
data class MagicLinkRequest(
    var email: String? = null,
    var username: String? = null,
    var clientId: String? = null,
    var expirationSeconds: Long? = null,
    var redirectUri: String? = null,
)
