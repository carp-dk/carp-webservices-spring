package dk.cachet.carp.webservices.export.domain.dto

import dk.cachet.carp.common.application.UUID

data class SummaryRequest(
    val deploymentIds: Set<UUID>?,
    val activeDeploymentsOnly: Boolean?,
)
