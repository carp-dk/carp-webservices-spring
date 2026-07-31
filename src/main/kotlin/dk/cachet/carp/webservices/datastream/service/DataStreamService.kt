package dk.cachet.carp.webservices.datastream.service

import dk.cachet.carp.common.application.UUID
import dk.cachet.carp.data.infrastructure.DataStreamServiceDecorator
import dk.cachet.carp.webservices.datastream.dto.DataStreamsSummaryDto
import kotlin.time.Instant

interface DataStreamService {
    val core: DataStreamServiceDecorator

    fun getLatestUpdatedAt(deploymentId: UUID): Instant?

    fun findDataStreamIdsByDeploymentId(deploymentId: UUID): List<Int>

    fun findDataStreamIdsByDeploymentIdAndDeviceRoleNames(
        deploymentId: UUID,
        deviceRoleNames: List<String>,
    ): List<Int>

    @Suppress("LongParameterList")
    suspend fun getDataStreamsSummary(
        studyId: UUID,
        deploymentId: UUID?,
        participantId: UUID?,
        scope: String,
        type: String,
        from: Instant,
        to: Instant,
    ): DataStreamsSummaryDto
}
