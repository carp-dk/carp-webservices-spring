package dk.cachet.carp.webservices.dataPoint.service

import dk.cachet.carp.webservices.dataPoint.domain.DataPoint
import dk.cachet.carp.webservices.deployment.dto.DeploymentStatisticsResponseDto
import org.springframework.data.domain.PageRequest

@Deprecated("DataPoint is deprecated. Use DataStream instead.")
interface DataPointService {
    suspend fun getAll(
        deploymentId: String,
        pageRequest: PageRequest,
        query: String?,
    ): List<DataPoint>

    fun getNumberOfDataPoints(
        deploymentId: String,
        query: String?,
    ): Long

    fun getStatistics(deploymentIds: List<String>): DeploymentStatisticsResponseDto

    fun getOne(id: Int): DataPoint

    fun delete(id: Int)
}
