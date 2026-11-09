package dk.cachet.carp.webservices.admin.domain

import java.time.Instant

/** One study with its aggregated recruitment figures, as read by `AdminStudyStore.findAll`. */
data class AdminStudyRow(
    val studyId: String,
    val name: String?,
    val description: String?,
    val ownerId: String?,
    val isLive: Boolean,
    /** Raw `protocolSnapshot.applicationData` string; the application name is extracted from it. */
    val applicationData: String?,
    val createdAt: Instant?,
    val updatedAt: Instant?,
    val firstDeploymentAt: Instant?,
    val participantCount: Long,
    val deploymentCount: Long,
    val fileCount: Long,
)
