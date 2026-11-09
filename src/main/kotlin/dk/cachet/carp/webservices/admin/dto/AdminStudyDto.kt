package dk.cachet.carp.webservices.admin.dto

import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant

data class AdminStudyDto(
    val studyId: String,
    val name: String?,
    val description: String?,
    // Without this the OpenAPI schema calls it `live`: swagger-core reads the `isLive()` getter bean-style
    // (Jackson 2, no Kotlin module), while the response itself is serialized as `isLive`.
    @get:Schema(name = "isLive")
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
