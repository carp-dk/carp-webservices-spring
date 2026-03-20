package dk.cachet.carp.webservices.study.repository

data class ParticipantAccountQueryRow(
    val participantJson: String,
    val isDeployed: Boolean,
    val deploymentId: String?,
)
