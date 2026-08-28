package dk.cachet.carp.webservices.dataPoint.service.impl

import cz.jirutka.rsql.parser.RSQLParser
import dk.cachet.carp.common.application.UUID
import dk.cachet.carp.webservices.common.configuration.internationalisation.service.MessageBase
import dk.cachet.carp.webservices.common.exception.responses.ResourceNotFoundException
import dk.cachet.carp.webservices.common.query.QueryUtil.validateQuery
import dk.cachet.carp.webservices.common.query.QueryVisitor
import dk.cachet.carp.webservices.dataPoint.domain.DataPoint
import dk.cachet.carp.webservices.dataPoint.filter.DataPointSpecifications
import dk.cachet.carp.webservices.dataPoint.repository.DataPointRepository
import dk.cachet.carp.webservices.dataPoint.service.DataPointService
import dk.cachet.carp.webservices.deployment.dto.DeploymentStatisticsResponseDto
import dk.cachet.carp.webservices.deployment.dto.StatisticsDto
import dk.cachet.carp.webservices.export.service.ResourceExporter
import dk.cachet.carp.webservices.security.authentication.service.AuthenticationService
import dk.cachet.carp.webservices.security.authorization.Role
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.nio.file.Path

@Deprecated("DataPoint is deprecated. Use DataStream instead.")
@Service
@Transactional
class DataPointServiceImpl(
    private val dataPointRepository: DataPointRepository,
    private val authenticationService: AuthenticationService,
    private val validateMessage: MessageBase,
) : DataPointService, ResourceExporter<DataPoint> {
    companion object {
        private val LOGGER: Logger = LogManager.getLogger()
    }

    override suspend fun getAll(
        deploymentId: String,
        pageRequest: PageRequest,
        query: String?,
    ): List<DataPoint> {
        val role = authenticationService.getRole()
        val id = authenticationService.getId()

        val validatedQuery = query?.let { validateQuery(it) }

        validatedQuery?.let {
            var specification =
                RSQLParser()
                    .parse(validatedQuery)
                    .accept(QueryVisitor<DataPoint>())
                    .and(DataPointSpecifications.belongsToDeploymentId(deploymentId))

            if (role < Role.RESEARCHER) {
                // Return data relevant to this user only.
                val belongsToUserSpec = DataPointSpecifications.belongsToUserAccountId(id.stringRepresentation)
                specification = specification.and(belongsToUserSpec)
            }

            return dataPointRepository.findAll(specification, pageRequest).content
        }

        if (role < Role.RESEARCHER) {
            return withContext(Dispatchers.IO) {
                dataPointRepository.findByDeploymentId(deploymentId, pageRequest)
            }.content
        }

        return withContext(Dispatchers.IO) {
            dataPointRepository.findByDeploymentIdAndCreatedBy(
                deploymentId,
                id.stringRepresentation,
                pageRequest,
            )
        }.content
    }

    override fun getNumberOfDataPoints(
        deploymentId: String,
        query: String?,
    ): Long {
        val role = authenticationService.getRole()
        val id = authenticationService.getId()

        val validatedQuery = query?.let { validateQuery(it) }

        validatedQuery?.let {
            var specification =
                RSQLParser()
                    .parse(validatedQuery)
                    .accept(QueryVisitor<DataPoint>())
                    .and(DataPointSpecifications.belongsToDeploymentId(deploymentId))

            if (role < Role.RESEARCHER) {
                // Return data relevant to this user only.
                val belongsToUserSpec = DataPointSpecifications.belongsToUserAccountId(id.stringRepresentation)
                specification = specification.and(belongsToUserSpec)
            }

            return dataPointRepository.count(specification)
        }

        if (role < Role.RESEARCHER) {
            return dataPointRepository.countByDeploymentId(deploymentId)
        }

        return dataPointRepository.countByDeploymentIdAndCreatedBy(deploymentId, id.stringRepresentation)
    }

    /**
     * The function [getStatistics] returns statistical information about the given deployments.
     * It transforms the [DataPointRepository.Companion.Statistics] data format to the
     * [DeploymentStatisticsResponseDto].
     *
     * @param deploymentIds A list of deployment ID's
     * @return [DeploymentStatisticsResponseDto]
     */
    @Deprecated("To be removed in the future")
    @Suppress("NestedBlockDepth")
    override fun getStatistics(deploymentIds: List<String>): DeploymentStatisticsResponseDto {
        val statistics: List<DataPointRepository.Companion.Statistics> =
            dataPointRepository.getStatistics(
                deploymentIds,
            )
        // Initialize the result data structure
        val result: MutableMap<String, MutableMap<String, StatisticsDto>> = mutableMapOf()
        // Iterate through the result list
        statistics.forEach {
            // If the current deploymentId is already in the map
            if (result.containsKey(it.did)) {
                val typeMap = result[it.did]
                // If the current format/dataType is already in the map
                if (typeMap!!.containsKey(it.format)) {
                    // Update the data
                    typeMap[it.format].apply {
                        this!!.count += it.total
                        this!!.uploads[it.stamp] = it.total
                    }
                } else {
                    // Else add the new format/dataType to the Map with the current values
                    val statDto =
                        StatisticsDto().apply {
                            count += it.total
                            uploads[it.stamp] = it.total
                        }
                    typeMap[it.format] = statDto
                }
            } else {
                // Else add the new deploymentId to the map along with the current format/dataType and current values
                val statDto =
                    StatisticsDto().apply {
                        count += it.total
                        uploads[it.stamp] = it.total
                    }
                val initializedMap: MutableMap<String, StatisticsDto> = mutableMapOf(it.format to statDto)
                result[it.did] = initializedMap
            }
        }

        return DeploymentStatisticsResponseDto(result)
    }

    override fun getOne(id: Int): DataPoint {
        val optionalDataPoint = dataPointRepository.findById(id)
        if (!optionalDataPoint.isPresent) {
            LOGGER.warn("DataPoint is not found, id: $id")
            throw ResourceNotFoundException(validateMessage.get("datapoint.not_found", id))
        }
        return optionalDataPoint.get()
    }

    override fun delete(id: Int) {
        val dataPoint = getOne(id)
        dataPointRepository.delete(dataPoint)
        LOGGER.info("Datapoint deleted, id: $id")
    }

    override val dataFileName = "data-points.json"

    override suspend fun exportDataOrThrow(
        studyId: UUID,
        deploymentIds: Set<UUID>,
        target: Path,
    ) = withContext(Dispatchers.IO) {
        dataPointRepository.findAllByDeploymentIds(deploymentIds.map { it.stringRepresentation })
    }
}
