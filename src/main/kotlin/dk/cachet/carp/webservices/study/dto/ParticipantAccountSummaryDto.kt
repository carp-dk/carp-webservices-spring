package dk.cachet.carp.webservices.study.dto

import kotlinx.datetime.Instant

/**
 * Participant-centered row returned by the participant accounts query endpoint.
 *
 * [participantId] is the recruitment participant ID, not the CARP account ID.
 * [accountIdentity] is the resolved identity string shown to clients.
 * [invitedOn] is populated for deployed participants when the recruitment snapshot includes
 * a deployment invitation time.
 * [carpUser] is `true` when the participant identity resolves to an existing CARP account.
 */
data class ParticipantAccountSummaryDto(
    val participantId: String,
    val firstName: String?,
    val lastName: String?,
    val accountIdentity: String?,
    val isDeployed: Boolean,
    val invitedOn: Instant?,
    val carpUser: Boolean,
)
