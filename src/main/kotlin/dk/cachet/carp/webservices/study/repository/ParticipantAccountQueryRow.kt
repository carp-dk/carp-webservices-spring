package dk.cachet.carp.webservices.study.repository

import kotlin.time.Instant

/**
 * [deploymentId] and [invitedOn] describe the participant's most recently created deployment; both are
 * null when the participant is not deployed, and [invitedOn] is also null when that deployment has no
 * stored deployment row.
 */
data class ParticipantAccountQueryRow(
    val participantJson: String,
    val isDeployed: Boolean,
    val deploymentId: String?,
    val invitedOn: Instant? = null,
)
