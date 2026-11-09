package dk.cachet.carp.webservices.admin.dto

import java.time.Instant

data class AdminStudyDto(
    val studyId: String,
    val name: String?,
    val description: String?,
    val isLive: Boolean,
    val applicationName: String?,
    val owner: StudyOwnerDto,
    val createdAt: Instant?,
    val updatedAt: Instant?,
    /** When the first participant group was deployed; the closest stored proxy for when the study started. */
    val firstDeploymentAt: Instant?,
    val participantCount: Long,
    val deploymentCount: Long,
    val fileCount: Long,
)

/** The study owner; [email] and [fullName] are null if the account could not be found. */
data class StudyOwnerDto(
    val id: String?,
    val email: String?,
    val fullName: String?,
)
