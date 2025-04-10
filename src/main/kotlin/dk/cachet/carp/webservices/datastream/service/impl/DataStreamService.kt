package dk.cachet.carp.webservices.datastream.service.impl

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import dk.cachet.carp.common.application.UUID
import dk.cachet.carp.common.application.data.DataType
import dk.cachet.carp.data.application.DataStreamId
import dk.cachet.carp.studies.domain.users.ParticipantRepository
import dk.cachet.carp.webservices.common.services.CoreServiceContainer
import dk.cachet.carp.webservices.datastream.domain.DataStreamSequence
import dk.cachet.carp.webservices.datastream.domain.DateTaskQuantityTriple
import dk.cachet.carp.webservices.datastream.dto.DataStreamsSummaryDto
import dk.cachet.carp.webservices.datastream.repository.DataStreamIdRepository
import dk.cachet.carp.webservices.datastream.repository.DataStreamSequenceRepository
import dk.cachet.carp.webservices.datastream.service.DataStreamService
import dk.cachet.carp.webservices.datastream.service.createSequence
import dk.cachet.carp.webservices.deployment.service.ParticipationService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import kotlinx.datetime.toJavaInstant
import kotlinx.datetime.toKotlinInstant
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.springframework.dao.DataAccessException
import org.springframework.stereotype.Service
import java.io.IOException
import java.nio.file.Path

@Service
class DataStreamService(
    private val dataStreamIdRepository: DataStreamIdRepository,
    private val dataStreamSequenceRepository: DataStreamSequenceRepository,
    private val objectMapper: ObjectMapper,
    private val participantRepository: ParticipantRepository,
    private val participationService: ParticipationService,
    services: CoreServiceContainer,
) : DataStreamService {
    companion object {
        private val LOGGER: Logger = LogManager.getLogger()
        private val validTypes = setOf("informed_consent", "survey", "cognition", "audio", "video", "image", "health", "sensing", "one_time_sensing")
        private val validScopes = setOf("study", "deployment", "participant")
    }

    final override val core = services.dataStreamService

    /**
     * Retrieves the latest update timestamp for a given deployment.
     *
     * @param deploymentId The ID of the deployment for which to retrieve the latest update timestamp.
     * @return The latest update timestamp as an `Instant`,
     * or null if no data stream inputs are found for the given deployment ID.
     */

    override fun getLatestUpdatedAt(deploymentId: UUID): Instant? {
        val dataStreamIds = findDataStreamIdsByDeploymentId(deploymentId)
        return findLatestUpdatedAtByDataStreamIds(dataStreamIds)
    }

    override fun findDataStreamIdsByDeploymentId(deploymentId: UUID): List<Int> {
        return dataStreamIdRepository.getAllByDeploymentId(deploymentId.toString()).map { it.id }
    }

    override fun findDataStreamIdsByDeploymentIdAndDeviceRoleNames(
        deploymentId: UUID,
        deviceRoleNames: List<String>,
    ): List<Int> {
        return dataStreamIdRepository.getAllByStudyDeploymentIdAndDeviceRoleNameIn(
            deploymentId.toString(),
            deviceRoleNames.toMutableList(),
        )
            .map { it.id }
    }

    override suspend fun getDataStreamsSummary(
        studyId: UUID,
        deploymentId: UUID?,
        participantId: UUID?,
        scope: String,
        type: String,
        from: Instant,
        to: Instant,
    ): DataStreamsSummaryDto {
        require(type in validTypes) { "Invalid type: $type. Allowed values: $validTypes" }

        val dateTaskQuantityTriplesDb =
            withContext(Dispatchers.IO) {
                dataStreamSequenceRepository.getDayKeyQuantityListByDataStreamIdsAndOtherParameters(
                    dataStreamIds = getDataStreamIds(scope, studyId, deploymentId, participantId),
                    from = from.toJavaInstant(),
                    to = to.toJavaInstant(),
                    studyId = studyId.toString(),
                    taskType = type,
                )
            }

        val dateTaskQuantityTriples =
            dateTaskQuantityTriplesDb.map {
                DateTaskQuantityTriple(
                    date = Instant.fromEpochMilliseconds(it.date.time),
                    task = it.task,
                    quantity = it.quantity,
                )
            }

        return DataStreamsSummaryDto(
            data = dateTaskQuantityTriples,
            studyId = studyId.toString(),
            deploymentId = deploymentId?.toString(),
            participantId = participantId?.toString(),
            scope = scope,
            type = type,
            from = from,
            to = to,
        )
    }

    fun findLatestUpdatedAtByDataStreamIds(dataStreamIds: List<Int>): Instant? {
        return if (dataStreamIds.isEmpty()) {
            null
        } else {
            dataStreamSequenceRepository.findMaxUpdatedAtByDataStreamIds(dataStreamIds)?.toKotlinInstant()
        }
    }

    val dataFileName = "data-streams.json"

    suspend fun exportDataOrThrow(
        deploymentIds: Set<UUID>,
        target: Path,
    ): Unit =
        withContext(Dispatchers.IO) {
            val dataStreamIds =
                dataStreamIdRepository.getAllByDeploymentIds(
                    deploymentIds.map { it.toString() },
                )

            val path = target.resolve(dataFileName)

            try {
                getDataStreams(dataStreamIds, target)
                LOGGER.info("A new file is created for zipping with name ${path.fileName}.")
            } catch (e: IOException) {
                LOGGER.error("An error occurred while storing the file ${path.fileName}", e)
            } catch (e: IllegalArgumentException) {
                LOGGER.error("An error occurred while storing the file (empty dataStreamList) ${path.fileName}", e)
            }
        }

    private fun DataStreamSequence.toRange(): LongRange {
        return firstSequenceId!!..lastSequenceId!!
    }

    suspend fun getDataStreams(
        dataStreamIds: List<Int>,
        target: Path,
    ) = withContext(Dispatchers.IO) {
        // Validate inputs
        require(dataStreamIds.isNotEmpty()) { "DataStream list cannot be empty." }

        val path = target.resolve(dataFileName)

        val jsonGenerator = objectMapper.factory.createGenerator(path.toFile().outputStream())
        jsonGenerator.writeStartArray()

        val sequenceIds = dataStreamSequenceRepository.findSequenceIdsByStreamId(dataStreamIds)

        sequenceIds.map { sequenceId ->
            try {
                // Return empty if no sequences found
                val sequence = dataStreamSequenceRepository.findById(sequenceId).orElse(null)

                buildDataStreamBatch(sequence, jsonGenerator)
            } catch (e: IllegalArgumentException) {
                LOGGER.info(
                    "Failed to process dataStream " +
                        "$sequenceId: ${e.message}",
                )
            }
        }
        jsonGenerator.writeEndArray()
        jsonGenerator.close()
    }

    private fun buildDataStreamBatch(
        dataStreamSequence: DataStreamSequence,
        jsonGenerator: JsonGenerator,
    ) {
        val id =
            dataStreamIdRepository.findByDataStreamId(dataStreamSequence.dataStreamId!!)
        check(id != null) { "DataStreamId not found for ID: ${dataStreamSequence.dataStreamId}" }

        val dataStreamId =
            DataStreamId(
                studyDeploymentId =
                    UUID(
                        id.studyDeploymentId ?: error("StudyDeploymentId not found"),
                    ),
                deviceRoleName = id.deviceRoleName ?: error("DeviceRoleName is null"),
                dataType =
                    DataType(
                        namespace = id.nameSpace ?: error("NameSpace is null"),
                        name = id.name ?: error("Name is null"),
                    ),
            )

        try {
            val sequenceRange = dataStreamSequence.toRange()
            val sequence = createSequence(dataStreamId, dataStreamSequence, sequenceRange, objectMapper)

            val batch = MutableDataStreamBatchDecorator()
            batch.appendSequence(sequence)

            batch.toList().map { dataStreamPoint ->
                objectMapper.writeValue(jsonGenerator, dataStreamPoint)
            }
        } catch (e: IllegalStateException) {
            LOGGER.error("State error while processing sequence ID: ${dataStreamSequence.id} - ${e.message}", e)
        } catch (e: JsonProcessingException) {
            LOGGER.error("JSON serialization error for sequence ID: ${dataStreamSequence.id} - ${e.message}", e)
        } catch (e: DataAccessException) {
            LOGGER.error("Database access error for sequence ID: ${dataStreamSequence.id} - ${e.message}", e)
        }
    }

    private suspend fun getDataStreamIds(
        scope: String,
        studyId: UUID,
        deploymentId: UUID?,
        participantId: UUID?,
    ): List<Int> {
        require(scope in validScopes) { "Invalid scope: $scope. Allowed values: $validScopes" }
        if (scope == "deployment") {
            requireNotNull(deploymentId) { "Deployment ID must be provided when scope is 'deployment'." }
            return getDataStreamIdsForDeployment(deploymentId)
        } else if (scope == "study") {
            return getDataStreamIdsForStudy(studyId)
        } else {
            requireNotNull(participantId) { "Participant ID must be provided when scope is 'participant'." }
            requireNotNull(deploymentId) { "Deployment ID must be provided when scope is 'participant'." }
            return getDataStreamIdsForParticipant(participantId, deploymentId)
        }
    }

    private fun getDataStreamIdsForDeployment(deploymentId: UUID): List<Int> {
        return findDataStreamIdsByDeploymentId(deploymentId)
    }

    private suspend fun getDataStreamIdsForStudy(studyId: UUID): List<Int> {
        val deploymentIds = participantRepository.getRecruitment(studyId)?.participantGroups?.keys?.toSet()

        return deploymentIds!!.flatMap { findDataStreamIdsByDeploymentId(it) }.toSet().toList()
    }

    private suspend fun getDataStreamIdsForParticipant(
        participantId: UUID,
        deploymentId: UUID,
    ): List<Int> {
        val participantGroup = participationService.getParticipantGroup(deploymentId)

        val participationHavingParticipantId =
            participantGroup!!.participations.find { it.participation.participantId == participantId }

        val assignedPrimaryDeviceRoleNames = participationHavingParticipantId!!.assignedPrimaryDeviceRoleNames
        return findDataStreamIdsByDeploymentIdAndDeviceRoleNames(
            deploymentId,
            assignedPrimaryDeviceRoleNames.toList(),
        ).toSet().toList()
    }
}
