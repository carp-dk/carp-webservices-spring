package dk.cachet.carp.webservices.study.dto

import io.swagger.v3.oas.annotations.media.Schema
import kotlin.time.Instant

/**
 * Participant-centered row returned by the participant accounts query endpoint.
 *
 * [participantId] is the recruitment participant ID, not the CARP account ID.
 * [accountIdentity] is the resolved identity string shown to clients.
 * [invitedOn] is the creation time of the participant's most recent deployment; null when the
 * participant is not deployed.
 * [carpUser] is `true` when the participant identity resolves to an existing CARP account.
 */
data class ParticipantAccountSummaryDto(
    val participantId: String,
    val firstName: String?,
    val lastName: String?,
    val accountIdentity: String?,
    // Named explicitly so the OpenAPI schema matches the JSON; see AdminStudyDto.isLive.
    @get:Schema(name = "isDeployed")
    val isDeployed: Boolean,
    val invitedOn: Instant?,
    val carpUser: Boolean,
)
