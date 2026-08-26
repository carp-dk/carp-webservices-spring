package dk.cachet.carp.webservices.study.domain

import jakarta.validation.constraints.AssertTrue
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Positive

data class AnonymousParticipantRequest(
    @field:Positive
    val amountOfAccounts: Int,
    // No unconditional upper bound: this field is shared by both pipelines (see useFastPipeline below),
    // and only the fast/bulk one (KeycloakFacade.createAnonymousAccountsBulk) narrows it to an Int - the
    // legacy pipeline (AccountServiceImpl.generateAnonymousAccount -> KeycloakFacade.recoverAccount) passes
    // it through as a Long end-to-end with no narrowing. See isExpirationSecondsValidForPipeline below for
    // the conditional check that actually guards the fast-pipeline case.
    @field:Positive
    val expirationSeconds: Long,
    @field:NotEmpty
    val clientId: String,
    @field:NotEmpty
    val redirectUri: String,
    @field:NotBlank
    val participantRoleName: String,
    val subdomain: String?,
    // Fast (bulk) pipeline is the default; callers can still opt out with false to use the legacy per-account flow.
    val useFastPipeline: Boolean = true,
) {
    // Rejects an out-of-Int-range expirationSeconds synchronously, with a clear validation error, but only
    // for the fast pipeline that actually can't handle it - KeycloakFacade.createAnonymousAccountsBulk
    // narrows it to an Int via Math.toIntExact, which would otherwise only surface as an opaque async
    // export failure with no diagnostic detail (Export has no error-message field). The legacy pipeline
    // passes the value through as a Long with no narrowing, so it's exempt here.
    @get:AssertTrue(message = "expirationSeconds must fit within Int range when useFastPipeline is true")
    val isExpirationSecondsValidForPipeline: Boolean
        get() = !useFastPipeline || expirationSeconds <= Int.MAX_VALUE
}
