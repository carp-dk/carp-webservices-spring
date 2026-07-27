package dk.cachet.carp.webservices.study.repository

import java.time.Instant

/** One deployed group whose most recent data upload predates the inactivity threshold. */
data class InactiveDeploymentRow(
    val deploymentId: String,
    val lastDataUpload: Instant,
)
