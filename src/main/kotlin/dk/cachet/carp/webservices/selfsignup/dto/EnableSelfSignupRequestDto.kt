package dk.cachet.carp.webservices.selfsignup.dto

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Positive

/** Enables (or reconfigures) self-signup for a study. Supplying this once at enable-time keeps these
 * Keycloak/protocol parameters out of the public signup call, which only ever needs a short code. */
data class EnableSelfSignupRequestDto(
    @field:Positive
    val maxParticipants: Int,
    @field:NotBlank
    val participantRoleName: String,
    @field:NotEmpty
    val clientId: String,
    @field:NotEmpty
    val redirectUri: String,
    val subdomain: String?,
    // Capped well below Int.MAX_VALUE (not just "positive"): KeycloakFacade.createAnonymousAccountsBulk
    // narrows this to an Int via Math.toIntExact, which throws rather than silently wrapping above that
    // range - this cap is defense-in-depth so an oversized value is rejected here with a clean validation
    // error instead of that exception surfacing from the Keycloak call. 30 days is already generous for a
    // magic link meant to be used right after signup, and matches the existing anonymous-account
    // CLEANUP_BUFFER.
    @field:Positive
    @field:Max(MAX_EXPIRATION_SECONDS)
    val expirationSeconds: Long = 86_400,
) {
    companion object {
        const val MAX_EXPIRATION_SECONDS = 30L * 24 * 60 * 60
    }
}
