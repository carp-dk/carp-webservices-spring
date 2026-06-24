package dk.cachet.carp.webservices.account.serdes

import dk.cachet.carp.protocols.application.StudyProtocolSnapshot
import dk.cachet.carp.webservices.common.configuration.internationalisation.service.MessageBase
import dk.cachet.carp.webservices.common.exception.serialization.SerializationException
import dk.cachet.carp.webservices.common.input.WS_JSON
import kotlinx.serialization.encodeToString
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import tools.jackson.core.JsonGenerator
import tools.jackson.databind.SerializationContext
import tools.jackson.databind.ValueSerializer

/**
 * The Class [StudyProtocolSnapshotSerializer].
 * The [StudyProtocolSnapshotSerializer] implements the serialization logic for [StudyProtocolSnapshot].
 */
@Suppress("TooGenericExceptionCaught", "SwallowedException")
class StudyProtocolSnapshotSerializer(
    private val validationMessages: MessageBase,
) : ValueSerializer<StudyProtocolSnapshot>() {
    companion object {
        private val LOGGER: Logger = LogManager.getLogger()
    }

    /**
     * The [serialize] function is used to serialize the parsed object.
     *
     * @param studyProtocolSnapshot The [studyProtocolSnapshot] object containing the json object parsed.
     * @throws SerializationException If the [StudyProtocolSnapshot] is blank or empty.
     * Also, if the [StudyProtocolSnapshot] contains invalid format.
     * @return The serialization [StudyProtocolSnapshot] object.
     */
    override fun serialize(
        studyProtocolSnapshot: StudyProtocolSnapshot,
        jsonGeneator: JsonGenerator,
        serializers: SerializationContext,
    ) {
        if (studyProtocolSnapshot == null) {
            LOGGER.error("The core StudyProtocolSnapshot is null.")
            throw SerializationException(validationMessages.get("protocol.snapshot.serialization.empty"))
        }

        val serialized: String
        try {
            serialized = WS_JSON.encodeToString(studyProtocolSnapshot)
        } catch (ex: Exception) {
            LOGGER.error("The core StudyProtocolSnapshot is not valid. Exception: ${ex.message}")
            throw SerializationException(
                validationMessages.get("protocol.snapshot.serialization.error", ex.message.toString()),
            )
        }

        jsonGeneator!!.writeRawValue(serialized)
    }
}
