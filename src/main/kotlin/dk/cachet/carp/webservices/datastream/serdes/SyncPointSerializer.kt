package dk.cachet.carp.webservices.datastream.serdes

import dk.cachet.carp.data.application.SyncPoint
import dk.cachet.carp.webservices.common.configuration.internationalisation.service.MessageBase
import dk.cachet.carp.webservices.common.exception.serialization.SerializationException
import dk.cachet.carp.webservices.common.input.WS_JSON
import kotlinx.serialization.encodeToString
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import tools.jackson.core.JsonGenerator
import tools.jackson.databind.SerializationContext
import tools.jackson.databind.ValueSerializer

@Suppress("TooGenericExceptionCaught", "SwallowedException")
class SyncPointSerializer(private val validationMessages: MessageBase) : ValueSerializer<SyncPoint>() {
    companion object {
        private val LOGGER: Logger = LogManager.getLogger()
    }

    override fun serialize(
        value: SyncPoint,
        gen: JsonGenerator,
        serializers: SerializationContext,
    ) {
        if (value == null) {
            LOGGER.error("The SyncPoint is null.")
            throw SerializationException(validationMessages.get("data.stream.syncPoint.serialization.empty"))
        }

        val serialized: String
        try {
            serialized = WS_JSON.encodeToString(value)
        } catch (ex: Exception) {
            LOGGER.error("The syncPoint request is not valid. Exception: ${ex.message}")
            throw SerializationException(
                validationMessages.get("data.stream.syncPoint.serialization.error", ex.message.toString()),
            )
        }

        gen!!.writeRawValue(serialized)
    }
}
