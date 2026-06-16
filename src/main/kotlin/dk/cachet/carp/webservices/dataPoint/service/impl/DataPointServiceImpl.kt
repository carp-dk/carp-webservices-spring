package dk.cachet.carp.webservices.dataPoint.service.impl

import cz.jirutka.rsql.parser.RSQLParser
import dk.cachet.carp.common.application.UUID
import dk.cachet.carp.webservices.common.configuration.internationalisation.service.MessageBase
import dk.cachet.carp.webservices.common.exception.responses.BadRequestException
import dk.cachet.carp.webservices.common.exception.responses.ResourceNotFoundException
import dk.cachet.carp.webservices.common.query.QueryUtil.validateQuery
import dk.cachet.carp.webservices.common.query.QueryVisitor
import dk.cachet.carp.webservices.dataPoint.domain.DataPoint
import dk.cachet.carp.webservices.dataPoint.dto.CreateDataPointRequestDto
import dk.cachet.carp.webservices.dataPoint.listener.DataPointBatchProcessorJob
import dk.cachet.carp.webservices.dataPoint.repository.DataPointRepository
import dk.cachet.carp.webservices.dataPoint.service.DataPointService
import dk.cachet.carp.webservices.deployment.dto.DeploymentStatisticsResponseDto
import dk.cachet.carp.webservices.deployment.dto.StatisticsDto
import dk.cachet.carp.webservices.export.service.ResourceExporter
import dk.cachet.carp.webservices.security.authentication.service.AuthenticationService
import dk.cachet.carp.webservices.security.authorization.Role
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import java.nio.file.Path
import java.time.LocalDate

@Deprecated("DataPoint is deprecated. Use DataStream instead.")
@Service
@Transactional
class DataPointServiceImpl(
    private val dataPointRepository: DataPointRepository,
    private val dataPointBatchProcessorJob: DataPointBatchProcessorJob,
    private val authenticationService: AuthenticationService,
    private val validateMessage: MessageBase,
    meterRegistry: MeterRegistry,
) : DataPointService, ResourceExporter<DataPoint> {
    companion object {
        private val LOGGER: Logger = LogManager.getLogger()
        private const val CREATED_ENTITY_COUNTER_NAME = "datapoints.created"
        private const val DATE_TAG = "date"
    }

    /**
     * TODO: needs to be revisited, not sure if micrometer is even working at all.
     * Micrometer counter for measuring how many entities are created in a day.
     */
    private val createdEntityCounter =
        Counter
            .builder(CREATED_ENTITY_COUNTER_NAME)
            .description("keeps track of datapoints created on a given day")
            .tags(DATE_TAG, LocalDate.now().toString())
            .register(meterRegistry)

    override suspend fun getAll(
        deploymentId: String,
        pageRequest: PageRequest,
        query: String?,
    ): List<DataPoint> {
        val role = authenticationService.getRole()
        val id = authenticationService.getId()

        val validatedQuery = query?.let { validateQuery(it) }

        validatedQuery?.let {
            val queryForRole =
                if (role < Role.RESEARCHER) {
                    // Return data relevant to this user only.
                    "$validatedQuery;deployment_id==$deploymentId;created_by==$id"
                } else {
                    // Return data relevant to this deployment.
                    "$validatedQuery;deployment_id==$deploymentId"
                }

            val specification =
                RSQLParser()
                    .parse(queryForRole)
                    .accept(QueryVisitor<DataPoint>())

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
            val queryForRole =
                if (role < Role.RESEARCHER) {
                    // Return data relevant to this user only.
                    "$validatedQuery;deployment_id==$deploymentId"
                } else {
                    // Return data relevant to this deployment.
                    "$validatedQuery;deployment_id==$deploymentId"
                }

            val specification =
                RSQLParser()
                    .parse(queryForRole)
                    .accept(QueryVisitor<DataPoint>())

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

    override fun create(
        deploymentId: String,
        file: MultipartFile?,
        request: CreateDataPointRequestDto,
    ): DataPoint {
        val id = authenticationService.getId()
        val dataPoint =
            DataPoint().apply {
                this.deploymentId = deploymentId
                carpHeader = request.carpHeader
                carpBody = request.carpBody
                storageName = request.storageName
                createdBy = id.stringRepresentation
                updatedBy = id.stringRepresentation
            }

        val saved = dataPointRepository.save(dataPoint, file)
        LOGGER.info("Datapoint created, id: ${saved.id}")
        createdEntityCounter.increment()
        return saved
    }

    override fun create(dataPoint: DataPoint): DataPoint {
        val saved = dataPointRepository.save(dataPoint, null)
        LOGGER.info("Datapoint created, id: ${saved.id}")
        createdEntityCounter.increment()
        return saved
    }

    override fun createMany(
        file: MultipartFile,
        deploymentId: String,
    ) {
        val id = authenticationService.getId()
        val dataPoints: Array<DataPoint> =
            dataPointBatchProcessorJob.parseBatchFile(file)
                ?: throw BadRequestException(validateMessage.get("datapoint.file.batch.failed"))
        dataPoints.forEach { d ->
            run {
                d.deploymentId = deploymentId
                d.createdBy = id.stringRepresentation
                d.updatedBy = id.stringRepresentation
            }
        }
        dataPointBatchProcessorJob.process(dataPoints)
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
