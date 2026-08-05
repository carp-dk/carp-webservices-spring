package dk.cachet.carp.webservices.datastream.service

import dk.cachet.carp.common.application.UUID
import dk.cachet.carp.data.application.DataStreamBatch
import dk.cachet.carp.data.application.DataStreamId
import dk.cachet.carp.data.infrastructure.DataStreamServiceDecorator
import dk.cachet.carp.webservices.datastream.dto.DataStreamsSummaryDto
import kotlin.time.Instant

interface DataStreamService {
    val core: DataStreamServiceDecorator

    fun getLatestUpdatedAt(deploymentId: UUID): Instant?

    /**
     * Retrieve all data points of [dataStream] whose local audit timestamp (`updated_at`) falls
     * within the inclusive range [[from], [to]].
     *
     * Behaves like [dk.cachet.carp.data.application.DataStreamService.getDataStream] but selects
     * rows by their database `updated_at` column instead of a sequence-id range. Returns an empty
     * [DataStreamBatch] when no data falls within the range.
     *
     * @throws IllegalArgumentException if [dataStream] was never opened or [from] is after [to].
     */
    suspend fun getDataStreamByUpdatedAt(
        dataStream: DataStreamId,
        from: Instant,
        to: Instant,
    ): DataStreamBatch

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
