package dk.cachet.carp.webservices.common.serialisers.serdes

import dk.cachet.carp.common.application.UUID
import dk.cachet.carp.common.infrastructure.serialization.JSON
import dk.cachet.carp.webservices.common.configuration.internationalisation.service.MessageBase
import dk.cachet.carp.webservices.common.exception.serialization.SerializationException
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.springframework.util.StringUtils
import tools.jackson.core.JsonParser
import tools.jackson.databind.DeserializationContext
import tools.jackson.databind.ValueDeserializer

/**
 * The Class [UUIDDeserializer].
 * The [UUIDDeserializer] implements the deserialization logic for [UUID].
 */
@Suppress("TooGenericExceptionCaught", "SwallowedException")
class UUIDDeserializer(private val validationMessages: MessageBase) : ValueDeserializer<UUID>() {
    companion object {
        private val LOGGER: Logger = LogManager.getLogger()
    }

    /**
     * The function [deserialize] is used to deserialize the parsed object.
     *
     * @param jsonParser The [jsonParser] object containing the json object parsed.
     * @param deserializationContext The [deserializationContext] object containing the context of the deserialization.
     * @throws SerializationException If the [UUID] is blank or empty.
     * Also, if the [UUID] contains invalid format.
     * @return The deserialized account object.
     */
    override fun deserialize(
        jsonParser: JsonParser,
        deserializationContext: DeserializationContext,
    ): UUID {
        val uuid: String
        try {
            uuid = deserializationContext.readTree(jsonParser).toString()

            if (!StringUtils.hasLength(uuid)) {
                LOGGER.error("The core UUID cannot be blank or empty!")
                throw SerializationException(validationMessages.get("deserialization.uuid.empty"))
            }
        } catch (ex: Exception) {
            LOGGER.error("The core UUID contains bad format. Exception: $ex")
            throw SerializationException(
                validationMessages.get("deserialization.uuid.bad_format", ex.message.toString()),
            )
        }

        val parsed: UUID
        try {
            parsed = JSON.decodeFromString(uuid)
        } catch (ex: Exception) {
            LOGGER.error("The core UUID is not valid. Exception: $ex")
            throw SerializationException(validationMessages.get("deserialization.uuid.error", ex.message.toString()))
        }

        return parsed
    }
}
