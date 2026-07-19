package dk.cachet.carp.webservices.datastream.service.impl

import dk.cachet.carp.common.application.UUID
import dk.cachet.carp.common.application.data.DataType
import dk.cachet.carp.data.application.DataStreamId
import dk.cachet.carp.studies.domain.users.ParticipantRepository
import dk.cachet.carp.webservices.common.services.CoreServiceContainer
import dk.cachet.carp.webservices.datastream.domain.DataStreamSequence
import dk.cachet.carp.webservices.datastream.domain.DateTaskQuantityTriple
import dk.cachet.carp.webservices.datastream.dto.DataStreamsSummaryDto
import dk.cachet.carp.webservices.datastream.dto.DateTaskQuantityTripleDb
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
import tools.jackson.core.JacksonException
import tools.jackson.core.JsonGenerator
import tools.jackson.databind.ObjectMapper
import java.io.IOException
import java.nio.file.Path
import java.time.ZoneOffset

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
        private const val COMPLETED_APP_TASK_NAMESPACE = "dk.cachet.carp.completedapptask"
        private const val V2_COMPLETED_APP_TASK_TYPE_PREFIX = "$COMPLETED_APP_TASK_NAMESPACE."
        private const val SEMVER_COMPONENT_COUNT = 3
        private const val SEMVER_PADDING_VALUE = 0
        private val validTypes =
            setOf(
                "informed_consent",
                "survey",
                "cognition",
                "audio",
                "image",
                "health",
                "sensing",
                "video",
                "one_time_sensing",
            )
        private val validScopes = setOf("study", "deployment", "participant")
    }

    final override val core = services.dataStreamService
    private val studyService = services.studyService

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
        val protocolApiLevel = getProtocolApiLevel(studyId)
        val isV2 = protocolApiLevel?.let { compareVersions(it, "2.0.0") }?.let { it >= 0 } ?: false
        return if (!isV2) {
            getDataStreamsSummaryV1(studyId, deploymentId, participantId, scope, type, from, to)
        } else {
            getDataStreamsSummaryV2(studyId, deploymentId, participantId, scope, type, from, to)
        }
    }

    @Suppress("LongParameterList")
    private suspend fun getDataStreamsSummaryV1(
        studyId: UUID,
        deploymentId: UUID?,
        participantId: UUID?,
        scope: String,
        type: String,
        from: Instant,
        to: Instant,
    ): DataStreamsSummaryDto {
        require(type in validTypes) { "Invalid type: $type. Allowed values: $validTypes" }
        require(from < to) { "'from' must be before 'to'." }

        val dataStreamIds = getDataStreamIds(scope, studyId, deploymentId, participantId)
        if (dataStreamIds.isEmpty()) {
            return DataStreamsSummaryDto(
                data = emptyList(),
                studyId = studyId.toString(),
                deploymentId = deploymentId?.toString(),
                participantId = participantId?.toString(),
                scope = scope,
                type = type,
                from = from,
                to = to,
            )
        }

        val dateTaskQuantityTriples =
            withContext(Dispatchers.IO) {
                dataStreamSequenceRepository.getDayKeyQuantityListByDataStreamIdsAndOtherParameters(
                    dataStreamIds = dataStreamIds,
                    from = from.toJavaInstant(),
                    to = to.toJavaInstant(),
                    taskType = type,
                )
            }.map { it.toDomain() }

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

    @Suppress("LongParameterList")
    private suspend fun getDataStreamsSummaryV2(
        studyId: UUID,
        deploymentId: UUID?,
        participantId: UUID?,
        scope: String,
        type: String,
        from: Instant,
        to: Instant,
    ): DataStreamsSummaryDto {
        require(type in validTypes) { "Invalid type: $type. Allowed values: $validTypes" }
        require(from < to) { "'from' must be before 'to'." }

        val dataStreamIds = getDataStreamIdsV2(scope, studyId, deploymentId, participantId, type)
        if (dataStreamIds.isEmpty()) {
            return DataStreamsSummaryDto(
                data = emptyList(),
                studyId = studyId.toString(),
                deploymentId = deploymentId?.toString(),
                participantId = participantId?.toString(),
                scope = scope,
                type = type,
                from = from,
                to = to,
            )
        }

        val dateTaskQuantityTriples =
            withContext(Dispatchers.IO) {
                dataStreamSequenceRepository.getDayKeyQuantityListByDataStreamIdsAndOtherParametersV2(
                    dataStreamIds = dataStreamIds,
                    from = from.toJavaInstant(),
                    to = to.toJavaInstant(),
                    completedAppTaskType = getCompletedAppTaskTypeV2(type),
                )
            }.map { it.toDomain() }

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

    private suspend fun getProtocolApiLevel(studyId: UUID): String? {
        val protocolSnapshot = studyService.getStudyDetails(studyId).protocolSnapshot
        val applicationData = protocolSnapshot?.applicationData?.data?.trim().orEmpty()
        if (applicationData.isEmpty()) return null

        return try {
            val node = objectMapper.readTree(applicationData)?.path("protocolApiLevel")
            node?.asString()?.trim()?.takeIf { it.isNotEmpty() }
        } catch (e: JacksonException) {
            LOGGER.warn("Failed to parse protocolApiLevel from applicationData for study $studyId.", e)
            null
        }
    }

    private fun compareVersions(
        left: String,
        right: String,
    ): Int? {
        val leftParts = parseSemver(left) ?: return null
        val rightParts = parseSemver(right) ?: return null

        return leftParts
            .zip(rightParts)
            .firstOrNull { (l, r) -> l != r }
            ?.let { (l, r) -> l.compareTo(r) }
            ?: 0
    }

    private fun parseSemver(value: String): List<Int>? {
        val parts = value.trim().split(".")
        if (parts.isEmpty()) return null

        val numbers =
            parts.map { it.toIntOrNull() ?: return null }
                .toMutableList()
        while (numbers.size < SEMVER_COMPONENT_COUNT) numbers.add(SEMVER_PADDING_VALUE)
        return numbers.take(SEMVER_COMPONENT_COUNT)
    }

    private fun getCompletedAppTaskTypeV2(taskType: String): String = "$V2_COMPLETED_APP_TASK_TYPE_PREFIX$taskType"

    private suspend fun getDataStreamIdsV2(
        scope: String,
        studyId: UUID,
        deploymentId: UUID?,
        participantId: UUID?,
        taskType: String,
    ): List<Int> {
        require(scope in validScopes) { "Invalid scope: $scope. Allowed values: $validScopes" }
        return when (scope) {
            "deployment" -> {
                requireNotNull(deploymentId) { "Deployment ID must be provided when scope is 'deployment'." }
                getDataStreamIdsForDeploymentV2(deploymentId, taskType)
            }
            "study" -> getDataStreamIdsForStudyV2(studyId, taskType)
            "participant" -> {
                requireNotNull(participantId) { "Participant ID must be provided when scope is 'participant'." }
                requireNotNull(deploymentId) { "Deployment ID must be provided when scope is 'participant'." }
                getDataStreamIdsForParticipantV2(participantId, deploymentId, taskType)
            }
            else -> emptyList()
        }
    }

    private fun getDataStreamIdsForDeploymentV2(
        deploymentId: UUID,
        taskType: String,
    ): List<Int> {
        return dataStreamIdRepository.getAllIdsByDeploymentIdAndNameSpaceAndName(
            deploymentId.toString(),
            COMPLETED_APP_TASK_NAMESPACE,
            taskType,
        )
    }

    private suspend fun getDataStreamIdsForStudyV2(
        studyId: UUID,
        taskType: String,
    ): List<Int> {
        val deploymentIds =
            requireNotNull(
                participantRepository.getRecruitment(studyId)?.participantGroups?.keys?.toSet(),
            ) { "Recruitment not found for study $studyId" }

        return dataStreamIdRepository.getAllIdsByDeploymentIdsAndNameSpaceAndName(
            deploymentIds.map { it.toString() },
            COMPLETED_APP_TASK_NAMESPACE,
            taskType,
        ).toSet().toList()
    }

    private suspend fun getDataStreamIdsForParticipantV2(
        participantId: UUID,
        deploymentId: UUID,
        taskType: String,
    ): List<Int> {
        val participantGroup =
            requireNotNull(
                participationService.getParticipantGroup(deploymentId),
            ) { "Participant group not found for deployment $deploymentId" }

        val participationHavingParticipantId =
            requireNotNull(
                participantGroup.participations.find { it.participation.participantId == participantId },
            ) { "Participant $participantId not assigned to deployment $deploymentId" }

        val assignedPrimaryDeviceRoleNames = participationHavingParticipantId.assignedPrimaryDeviceRoleNames
        return dataStreamIdRepository.getAllIdsByDeploymentIdAndDeviceRoleNameInAndNameSpaceAndName(
            deploymentId.toString(),
            assignedPrimaryDeviceRoleNames.toList(),
            COMPLETED_APP_TASK_NAMESPACE,
            taskType,
        ).toSet().toList()
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
        if (dataStreamIds.isEmpty()) {
            LOGGER.warn("DataStream list is empty.")
            return@withContext
        }

        val path = target.resolve(dataFileName)

        val jsonGenerator = objectMapper.createGenerator(path.toFile().outputStream())
        jsonGenerator.writeStartArray()

        val sequenceIds = dataStreamSequenceRepository.findSequenceIdsByStreamId(dataStreamIds)

        sequenceIds.forEach { sequenceId ->
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
        } catch (e: JacksonException) {
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
        return when (scope) {
            "deployment" -> {
                requireNotNull(deploymentId) { "Deployment ID must be provided when scope is 'deployment'." }
                getDataStreamIdsForDeployment(deploymentId)
            }
            "study" -> getDataStreamIdsForStudy(studyId)
            "participant" -> {
                requireNotNull(participantId) { "Participant ID must be provided when scope is 'participant'." }
                requireNotNull(deploymentId) { "Deployment ID must be provided when scope is 'participant'." }
                getDataStreamIdsForParticipant(participantId, deploymentId)
            }
            else -> emptyList() // already guarded, keeps exhaustive when
        }
    }

    private fun getDataStreamIdsForDeployment(deploymentId: UUID): List<Int> {
        return findDataStreamIdsByDeploymentId(deploymentId)
    }

    private suspend fun getDataStreamIdsForStudy(studyId: UUID): List<Int> {
        val deploymentIds =
            requireNotNull(
                participantRepository.getRecruitment(studyId)?.participantGroups?.keys?.toSet(),
            ) { "Recruitment not found for study $studyId" }

        return deploymentIds.flatMap { findDataStreamIdsByDeploymentId(it) }.toSet().toList()
    }

    private suspend fun getDataStreamIdsForParticipant(
        participantId: UUID,
        deploymentId: UUID,
    ): List<Int> {
        val participantGroup =
            requireNotNull(
                participationService.getParticipantGroup(deploymentId),
            ) { "Participant group not found for deployment $deploymentId" }

        val participationHavingParticipantId =
            requireNotNull(
                participantGroup.participations.find { it.participation.participantId == participantId },
            ) { "Participant $participantId not assigned to deployment $deploymentId" }

        val assignedPrimaryDeviceRoleNames = participationHavingParticipantId.assignedPrimaryDeviceRoleNames
        return findDataStreamIdsByDeploymentIdAndDeviceRoleNames(
            deploymentId,
            assignedPrimaryDeviceRoleNames.toList(),
        ).toSet().toList()
    }
}

private fun DateTaskQuantityTripleDb.toDomain(): DateTaskQuantityTriple =
    DateTaskQuantityTriple(
        // date is a zone-less LocalDateTime (Hibernate 7 maps the SQL ::timestamp column to it).
        // Interpreting it as UTC is correct only because the deployment runs in UTC — a
        // long-standing invariant. Don't swap this for systemDefault().
        date = date.toInstant(ZoneOffset.UTC).toKotlinInstant(),
        task = task,
        quantity = quantity,
    )
