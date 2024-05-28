package dk.cachet.carp.webservices.common.serialisers.serdes

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.SerializerProvider
import dk.cachet.carp.common.application.UUID
import dk.cachet.carp.common.infrastructure.serialization.JSON
import dk.cachet.carp.webservices.common.configuration.internationalisation.service.MessageBase
import dk.cachet.carp.webservices.common.exception.serialization.SerializationException
import kotlinx.serialization.encodeToString
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger

/**
 * The Class [UUIDSerializer].
 * The [UUIDSerializer] implements the serialization mechanism for the [UUID] class.
 */
class UUIDSerializer(private val validationMessages: MessageBase) : JsonSerializer<UUID>() {
    companion object {
        private val LOGGER: Logger = LogManager.getLogger()
    }

    /**
     * The function [serialize] used to serialize the core [uuid].
     *
     * @param uuid The [uuid] of the users.
     * @param jsonGenerator The json [jsonGenerator] to write serialized core [uuid].
     * @param serializers The [serializers] for serializing the core [uuid].
     * @return The serialized core [uuid].
     */
    override fun serialize(
        uuid: UUID?,
        jsonGenerator: JsonGenerator?,
        serializers: SerializerProvider?,
    ) {
        if (uuid == null) {
            LOGGER.error("The core UUID is null.")
            throw SerializationException(validationMessages.get("serialization.uuid.empty"))
        }

        val serialized: String
        try {
            serialized = JSON.encodeToString(uuid)
        } catch (ex: Exception) {
            LOGGER.error("The core UUID is not valid. Exception: $ex")
            throw SerializationException(validationMessages.get("serialization.uuid.bad_format", ex.message.toString()))
        }

        jsonGenerator!!.writeRawValue(serialized)
    }
}
