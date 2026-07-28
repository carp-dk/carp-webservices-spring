package dk.cachet.carp.webservices.study.domain

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Positive

data class AnonymousParticipantRequest(
    @field:Positive
    val amountOfAccounts: Int,
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
)
