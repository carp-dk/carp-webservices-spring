package dk.cachet.carp.webservices.study.domain

import jakarta.validation.Validation
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Direct bean-validation coverage for [AnonymousParticipantRequest]. `expirationSeconds`'s Int-range check
 * is conditional on `useFastPipeline`: only that pipeline reaches
 * [dk.cachet.carp.webservices.security.authentication.oauth2.issuers.keycloak.KeycloakFacade]'s Int-narrowing
 * bulk path (see [dk.cachet.carp.webservices.security.authentication.oauth2.issuers.KeycloakFacadeTest] for
 * that guard too - kept as defense-in-depth) - the legacy pipeline passes the value through as a `Long`
 * with no narrowing, so it's exempt.
 */
class AnonymousParticipantRequestTest {
    private val validator = Validation.buildDefaultValidatorFactory().validator

    private fun request(
        expirationSeconds: Long,
        useFastPipeline: Boolean = true,
    ) = AnonymousParticipantRequest(
        amountOfAccounts = 1,
        expirationSeconds = expirationSeconds,
        clientId = "client",
        redirectUri = "https://example.com",
        participantRoleName = "participant",
        subdomain = null,
        useFastPipeline = useFastPipeline,
    )

    @Test
    fun `rejects an expirationSeconds beyond Int range on the fast pipeline`() {
        assertTrue(validator.validate(request(Int.MAX_VALUE.toLong() + 1, useFastPipeline = true)).isNotEmpty())
    }

    @Test
    fun `accepts an expirationSeconds beyond Int range on the legacy pipeline`() {
        assertTrue(validator.validate(request(Int.MAX_VALUE.toLong() + 1, useFastPipeline = false)).isEmpty())
    }

    @Test
    fun `rejects a non-positive expirationSeconds`() {
        assertTrue(validator.validate(request(0)).isNotEmpty())
    }
}
