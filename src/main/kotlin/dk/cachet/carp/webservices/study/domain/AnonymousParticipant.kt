package dk.cachet.carp.webservices.study.domain

import dk.cachet.carp.common.application.UUID
import kotlinx.datetime.Instant

data class AnonymousParticipant(
    val username: UUID,
    val studyDeploymentId: UUID,
    val magicLink: String,
    val expiryDate: Instant?
)