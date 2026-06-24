package dk.cachet.carp.webservices.protocol.repository

import dk.cachet.carp.common.application.UUID
import dk.cachet.carp.protocols.application.ProtocolVersion
import dk.cachet.carp.protocols.application.StudyProtocolSnapshot
import dk.cachet.carp.protocols.domain.StudyProtocol
import dk.cachet.carp.protocols.domain.StudyProtocolRepository
import dk.cachet.carp.webservices.common.configuration.internationalisation.service.MessageBase
import dk.cachet.carp.webservices.common.input.WS_JSON
import dk.cachet.carp.webservices.protocol.domain.Protocol
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.springframework.stereotype.Service
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper

/**
 * The Class [CoreProtocolRepository].
 * The [CoreProtocolRepository] provides the implementation for the core [StudyProtocolRepository] interface.
 */
@Service
class CoreProtocolRepository(
    private val protocolRepository: ProtocolRepository,
    private val validationMessages: MessageBase,
    private val objectMapper: ObjectMapper,
) : StudyProtocolRepository {
    companion object {
        private val LOGGER: Logger = LogManager.getLogger()
    }

    /**
     * Add the specified study [protocol] to the repository.
     *
     * @param version Identifies this first initial version of the [protocol].
     * @throws IllegalArgumentException when a [protocol] with the same owner and name already exists.
     */
    override suspend fun add(
        protocol: StudyProtocol,
        version: ProtocolVersion,
    ) = withContext(Dispatchers.IO) {
        val protocolById = protocolRepository.findAllById(protocol.id.stringRepresentation)

        check(protocolById.isEmpty()) {
            LOGGER.warn(
                "Protocol already exists, ownerID: {}, name: {}, id: {}",
                protocol.ownerId.stringRepresentation,
                protocol.name,
                protocol.id,
            )
            validationMessages.get("protocol.id.already.exists", protocol.id)
        }

        val protocolsStoredWithGivenParams =
            protocolRepository.findByParams(
                protocol.id.stringRepresentation,
                version.tag,
            )

        check(protocolsStoredWithGivenParams.isEmpty()) {
            LOGGER.warn(
                "Protocol already exists, ownerID: {}, name: {}, version: {}",
                protocol.ownerId.stringRepresentation,
                protocol.name,
                version.tag,
            )
            validationMessages.get(
                "protocol.already.exists",
                protocol.ownerId.stringRepresentation,
                protocol.name,
                version.tag,
            )
        }

        val protocolToSave = convertCoreProtocolToWSProtocol(protocol, version)

        protocolRepository.save(protocolToSave)
        LOGGER.info(
            "Protocol saved, name: ${protocol.name}, version: ${version.tag}, id: ${protocol.id.stringRepresentation}",
        )
    }

    /**
     * Find all [StudyProtocol]'s owned by the owner with [ownerId], or an empty sequence if none are found.
     *
     * @return This returns the last version of each [StudyProtocol] owned by the requested owner.
     */
    override suspend fun getAllForOwner(ownerId: UUID): Sequence<StudyProtocol> =
        withContext(Dispatchers.IO) {
            val result = protocolRepository.findAllByOwnerId(ownerId.stringRepresentation)

            result
                .map { p -> convertJsonNodeToStudyProtocol(p.snapshot!!) }
                .asSequence()
        }

    /**
     * Return the [StudyProtocol] with the specified protocol [id], or null when no such protocol is found.
     *
     * @param versionTag The tag of the specific version of the protocol to return.
     * The latest version is returned when not specified.
     */
    override suspend fun getBy(
        id: UUID,
        versionTag: String?,
    ): StudyProtocol? =
        withContext(Dispatchers.IO) {
            val result = protocolRepository.findByParams(id.stringRepresentation, versionTag)

            if (result.isEmpty()) {
                return@withContext null
            }

            convertJsonNodeToStudyProtocol(result[0].snapshot!!)
        }

    /**
     * Returns all stored versions for the [StudyProtocol] with the specified [Protocol].
     *
     * @throws IllegalArgumentException when a protocol with the specified [Protocol] does not exist.
     */
    override suspend fun getVersionHistoryFor(id: UUID): List<ProtocolVersion> =
        withContext(Dispatchers.IO) {
            val protocols = protocolRepository.findByParams(id.stringRepresentation, null)
            check(protocols.isNotEmpty()) {
                LOGGER.warn("Protocol is not found with id: {}", id.stringRepresentation)
                validationMessages.get("protocol.version_history.not_found", id.stringRepresentation)
            }
            protocols.map {
                ProtocolVersion(it.versionTag, Instant.fromEpochMilliseconds(it.createdAt!!.toEpochMilli()))
            }
        }

    /**
     * Replace a [version] of a [protocol], of which a previous version with the same owner and name is already stored.
     *
     * @throws IllegalArgumentException when the [protocol] with [version] to replace is not found.
     */
    override suspend fun replace(
        protocol: StudyProtocol,
        version: ProtocolVersion,
    ) = withContext(Dispatchers.IO) {
        val storedProtocols = protocolRepository.findByParams(protocol.id.stringRepresentation, version.tag)

        check(storedProtocols.isNotEmpty()) {
            LOGGER.warn(
                "Protocol is not found with id: {} and name: {}.",
                protocol.id.stringRepresentation,
                protocol.name,
            )
            validationMessages.get("protocol.get.not_found", protocol.id.stringRepresentation, protocol.name)
        }

        val oldVersion = storedProtocols[0]
        protocolRepository.delete(oldVersion)

        val newVersion = convertCoreProtocolToWSProtocol(protocol, version)
        protocolRepository.save(newVersion)
        LOGGER.info(
            "Protocol(${protocol.ownerId.stringRepresentation}, ${protocol.name}) replace successful! " +
                "Deleted version: ${oldVersion.versionTag}, new version: ${version.tag}",
        )
    }

    /**
     * Add a new [version] for the specified study [protocol] in the repository,
     * of which a previous version with the same owner and name is already stored.
     *
     * @throws IllegalArgumentException when:
     *   - the [protocol] is not yet stored in the repository
     *   - the tag specified in [version] is already in use
     */
    override suspend fun addVersion(
        protocol: StudyProtocol,
        version: ProtocolVersion,
    ) = withContext(Dispatchers.IO) {
        val protocolsStoredWithGivenParams = protocolRepository.findAllById(protocol.id.stringRepresentation)

        if (protocolsStoredWithGivenParams.isEmpty()) {
            LOGGER.warn("Protocol is not found,  id: {}, name: {}", protocol.id.stringRepresentation, protocol.name)
            throw IllegalArgumentException(
                validationMessages.get("protocol.update.not_found", protocol.id.stringRepresentation, protocol.name),
            )
        } else {
            protocolsStoredWithGivenParams.forEach {
                if (it.versionTag == version.tag) {
                    LOGGER.warn(
                        "Protocol with owner ID: {}, name: {} already has a version tag with {}",
                        protocol.ownerId.stringRepresentation,
                        protocol.name,
                        version.tag,
                    )
                    throw IllegalArgumentException(
                        validationMessages.get(
                            "protocol.update.already_exists",
                            protocol.ownerId.stringRepresentation,
                            protocol.name,
                            version.tag,
                        ),
                    )
                }
            }
        }

        val update = convertCoreProtocolToWSProtocol(protocol, version)

        // update the protocol
        protocolRepository.save(update)
        LOGGER.info("New version added to protocol with name: ${protocol.name} and version: ${version.tag}")
    }

    /**
     * The [convertJsonNodeToStudyProtocol] function converts a [JsonNode] to a [StudyProtocol].
     *
     * @param node The [JsonNode] to convert to a study protocol.
     * @return A [StudyProtocol] object containing the protocol.
     */
    private fun convertJsonNodeToStudyProtocol(node: JsonNode): StudyProtocol {
        val snapshot = WS_JSON.decodeFromString(StudyProtocolSnapshot.serializer(), node.toString())
        return StudyProtocol.fromSnapshot(snapshot)
    }

    /**
     * The [convertJsonNodeToStudyProtocol] function converts a [JsonNode] to a webservice [StudyProtocol].
     *
     * @param protocol The study [protocol].
     * @param version A unique label used to identify this specific version of the [protocol].
     *
     * @return A [Protocol] object containing the protocol.
     */
    private fun convertCoreProtocolToWSProtocol(
        protocol: StudyProtocol,
        version: ProtocolVersion,
    ): Protocol {
        val wsProtocol = Protocol()
        wsProtocol.versionTag = version.tag

        val snapshot = WS_JSON.encodeToString(StudyProtocolSnapshot.serializer(), protocol.getSnapshot())
        wsProtocol.snapshot = objectMapper.readTree(snapshot)

        return wsProtocol
    }
}
