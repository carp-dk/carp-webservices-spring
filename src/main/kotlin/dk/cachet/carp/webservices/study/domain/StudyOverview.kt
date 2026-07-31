package dk.cachet.carp.webservices.study.domain

import dk.cachet.carp.common.application.UUID
import kotlin.time.Instant

data class StudyOverview(
    /** StudyStatus */
    val studyId: UUID,
    val name: String,
    val createdOn: Instant,
    val studyProtocolId: UUID?,
    val canSetInvitation: Boolean,
    val canSetStudyProtocol: Boolean,
    val canDeployToParticipants: Boolean,
    /** Additional Info */
    val description: String?,
    val createdBy: String?,
)
