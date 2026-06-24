package dk.cachet.carp.webservices.datastream.service.core

import dk.cachet.carp.common.application.UUID
import dk.cachet.carp.common.application.intersect
import dk.cachet.carp.data.application.*
import dk.cachet.carp.webservices.common.input.WS_JSON
import dk.cachet.carp.webservices.datastream.domain.DataStreamConfiguration
import dk.cachet.carp.webservices.datastream.domain.DataStreamSequence
import dk.cachet.carp.webservices.datastream.domain.DataStreamSnapshot
import dk.cachet.carp.webservices.datastream.repository.DataStreamConfigurationRepository
import dk.cachet.carp.webservices.datastream.repository.DataStreamIdRepository
import dk.cachet.carp.webservices.datastream.repository.DataStreamSequenceRepository
import dk.cachet.carp.webservices.datastream.service.createSequence
import dk.cachet.carp.webservices.datastream.service.fetchValidatedDataStreamId
import dk.cachet.carp.webservices.datastream.service.impl.MutableDataStreamBatchDecorator
import dk.cachet.carp.webservices.datastream.service.validateConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.springframework.stereotype.Component
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper

@Component
class CoreDataStreamService(
    private val configRepository: DataStreamConfigurationRepository,
    private val dataStreamIdRepository: DataStreamIdRepository,
    private val dataStreamSequenceRepository: DataStreamSequenceRepository,
    private val objectMapper: ObjectMapper,
) : DataStreamService {
    companion object {
        private val LOGGER: Logger = LogManager.getLogger()
    }

    /**
     * Append a [batch] of data point sequences to corresponding data streams in [studyDeploymentId].
     *
     * @throws IllegalArgumentException when:
     *  - the `studyDeploymentId` of one or more sequences in [batch] does not match [studyDeploymentId]
     *  - the start of one or more of the sequences contained in [batch]
     *  precede the end of a previously appended sequence to the same data stream
     *  - [batch] contains a sequence with [DataStreamId] which wasn't configured for [studyDeploymentId]
     * @throws IllegalStateException when data streams for [studyDeploymentId] have been closed.
     */
    override suspend fun appendToDataStreams(
        studyDeploymentId: UUID,
        batch: DataStreamBatch,
    ) {
        // the `studyDeploymentId` of one or more sequences in [batch] does not match [studyDeploymentId]
        require(
            batch.sequences.all {
                it.dataStream.studyDeploymentId == studyDeploymentId
            },
        ) { "The study deployment ID of one or more sequences in `batch` doesn't match `studyDeploymentId`." }

        // whether there is a config present in the database
        val configOptional =
            withContext(Dispatchers.IO) {
                configRepository.findById(studyDeploymentId.stringRepresentation)
            }

        require(configOptional.isPresent) {
            "No configuration was found for studyDeploymentId ${studyDeploymentId.stringRepresentation}."
        }

        val config = mapToCoreConfig(configOptional.get().config!!)

        // checks whether any of the streams wasn't configured for studyDeploymentId
        require(
            batch.sequences.all {
                it.dataStream in config.expectedDataStreamIds
            },
        ) { "The batch contains a sequence with a data stream which wasn't configured for this study deployment." }

        check(
            !configOptional.get().closed,
        ) { "Data streams for study deployment with ID ${studyDeploymentId.stringRepresentation} have been closed." }

        val dataStreams = MutableDataStreamBatchDecorator()

        // appending sequences to batch
        dataStreams.appendBatch(batch)

        // save the sequences
        val dataStreamSequence =
            dataStreams.sequences.map {
                val dataStream = it.dataStream
                val dataStreamId =
                    dataStreamIdRepository.findByStudyDeploymentIdAndDeviceRoleNameAndNameAndNameSpace(
                        studyDeploymentId = dataStream.studyDeploymentId.stringRepresentation,
                        deviceRoleName = dataStream.deviceRoleName,
                        name = dataStream.dataType.name,
                        nameSpace = dataStream.dataType.namespace,
                    ).get()

                val snapshot = DataStreamSnapshot(it.measurements, it.triggerIds, it.syncPoint)

                // to ws save_to_db type
                DataStreamSequence(
                    dataStreamId = dataStreamId.id,
                    firstSequenceId = it.firstSequenceId,
                    lastSequenceId = it.range.last,
                    snapshot = objectMapper.valueToTree(snapshot),
                )
            }

        dataStreamSequenceRepository.saveAll(dataStreamSequence.asIterable())
    }

    /**
     * Stop accepting incoming data for all data streams for each of the [studyDeploymentIds].
     *
     * @throws IllegalArgumentException when no data streams were ever opened for any of the [studyDeploymentIds].
     */
    override suspend fun closeDataStreams(studyDeploymentIds: Set<UUID>) {
        val configs =
            withContext(Dispatchers.IO) {
                configRepository.getConfigurationsForIds(studyDeploymentIds.map { it.stringRepresentation })
            }

        configs.forEach { it.closed = true }

        if (configs.size != studyDeploymentIds.size) {
            LOGGER.warn("While closing configurations: Number of deployments different from number of configurations")
        }

        configRepository.saveAll(configs)
    }

    /**
     * Retrieve all data points in [dataStream] that fall within the inclusive range
     * defined by [fromSequenceId] and [toSequenceIdInclusive].
     * If [toSequenceIdInclusive] is null, all data points starting [fromSequenceId] are returned.
     *
     * In case no data for [dataStream] is stored in this repository, or is available for the specified range,
     * an empty [DataStreamBatch] is returned.
     *
     * @throws IllegalArgumentException if:
     *  - [dataStream] has never been opened
     *  - [fromSequenceId] is negative or [toSequenceIdInclusive] is smaller than [fromSequenceId]
     */
    override suspend fun getDataStream(
        dataStream: DataStreamId,
        fromSequenceId: Long,
        toSequenceIdInclusive: Long?,
    ): DataStreamBatch =
        withContext(Dispatchers.IO) {
            // Validate inputs and configuration
            validateSequenceRange(fromSequenceId, toSequenceIdInclusive)
            validateConfig(dataStream, configRepository)

            // Fetch data stream ID and sequences
            val dataStreamId = fetchValidatedDataStreamId(dataStream, dataStreamIdRepository)
            val sequenceRange = fromSequenceId..(toSequenceIdInclusive ?: Long.MAX_VALUE)
            val dataStreamSequences = fetchSequencesInRange(dataStreamId.id, sequenceRange)

            // Return empty batch if no sequences found
            if (dataStreamSequences.isEmpty()) return@withContext MutableDataStreamBatchDecorator()

            // Build and return the DataStreamBatch
            buildDataStreamBatch(dataStream, dataStreamSequences, sequenceRange)
        }

    // Validation helper methods
    private fun validateSequenceRange(
        fromSequenceId: Long,
        toSequenceIdInclusive: Long?,
    ) {
        require(fromSequenceId >= 0) {
            "Invalid [fromSequenceId]: cannot be negative."
        }
        require(toSequenceIdInclusive == null || fromSequenceId <= toSequenceIdInclusive) {
            "Invalid range: [toSequenceIdInclusive] ($toSequenceIdInclusive) is smaller than " +
                "[fromSequenceId] ($fromSequenceId)."
        }
    }

    // Data fetching helper methods

    private suspend fun fetchSequencesInRange(
        dataStreamId: Int,
        sequenceRange: LongRange,
    ): List<DataStreamSequence> {
        return withContext(Dispatchers.IO) {
            dataStreamSequenceRepository.findAllBySequenceIdRange(
                dataStreamId,
                sequenceRange.first,
                sequenceRange.last,
            )
        }
    }

    // DataStreamBatch building logic
    private fun buildDataStreamBatch(
        dataStream: DataStreamId,
        dataStreamSequences: List<DataStreamSequence>,
        sequenceRange: LongRange,
    ): DataStreamBatch {
        return dataStreamSequences
            .mapNotNull { sequence ->
                val subRange = sequence.toRange().intersect(sequenceRange)
                if (subRange.isEmpty()) null else createSequence(dataStream, sequence, subRange, objectMapper)
            }
            .fold(MutableDataStreamBatchDecorator()) { batch, sequence ->
                batch.apply { appendSequence(sequence) }
            }
    }

    // Mapping helper methods
    private fun DataStreamSequence.toRange(): LongRange {
        return firstSequenceId!!..lastSequenceId!!
    }

    /**
     * Start accepting data for a study deployment for data streams configured in [configuration].
     *
     * @throws IllegalStateException when data streams for the specified study deployment have already been configured.
     */
    override suspend fun openDataStreams(configuration: DataStreamsConfiguration) {
        val id = configuration.studyDeploymentId.stringRepresentation

        check(
            !withContext(Dispatchers.IO) {
                configRepository.existsById(id)
            },
        ) {
            "Data streams for deployment with \\\"$id\\\" have already been configured."
        }

        val node = WS_JSON.encodeToString(DataStreamsConfiguration.serializer(), configuration)
        withContext(Dispatchers.IO) {
            configRepository.save(DataStreamConfiguration(id, objectMapper.readTree(node)))
        }
        LOGGER.info("New data stream configuration is saved for deployment with id $id.")

        val ids =
            configuration.expectedDataStreamIds.map {
                dk.cachet.carp.webservices.datastream.domain.DataStreamId(
                    studyDeploymentId = it.studyDeploymentId.stringRepresentation,
                    deviceRoleName = it.deviceRoleName,
                    name = it.dataType.name,
                    nameSpace = it.dataType.namespace,
                )
            }
        dataStreamIdRepository.saveAll(ids)
        LOGGER.info("New data stream id(s) is saved for deployment with id $id.")
    }

    /**
     * Close data streams and remove all data for each of the [studyDeploymentIds].
     *
     * @return The IDs of the study deployments for which data streams were configured.
     * IDs for which no study deployment exists are ignored.
     */
    override suspend fun removeDataStreams(studyDeploymentIds: Set<UUID>): Set<UUID> {
        // close study deployments
        closeDataStreams(studyDeploymentIds)

        val deploymentIds = HashSet<UUID>()

        studyDeploymentIds.map {
            val configOptional = configRepository.findById(it.stringRepresentation)
            if (configOptional.isPresent) {
                val config = mapToCoreConfig(configOptional.get().config!!)

                val ids =
                    config.expectedDataStreamIds.map { dataStream: DataStreamId ->
                        dataStreamIdRepository.findByStudyDeploymentIdAndDeviceRoleNameAndNameAndNameSpace(
                            studyDeploymentId = dataStream.studyDeploymentId.stringRepresentation,
                            deviceRoleName = dataStream.deviceRoleName,
                            name = dataStream.dataType.name,
                            nameSpace = dataStream.dataType.namespace,
                        ).get().id
                    }
                // delete data streams
                dataStreamSequenceRepository.deleteAllByDataStreamIds(ids)
                dataStreamIdRepository.deleteAllByDataStreamIds(ids)
                deploymentIds.add(it)
            }
        }
        return deploymentIds
    }

    private fun mapToCoreConfig(node: JsonNode) =
        WS_JSON.decodeFromString(
            DataStreamsConfiguration.serializer(),
            node.toString(),
        )
}
