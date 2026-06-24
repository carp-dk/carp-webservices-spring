package dk.cachet.carp.webservices.datastream.serdes

import dk.cachet.carp.data.application.DataStreamBatch
import dk.cachet.carp.webservices.common.configuration.internationalisation.service.MessageBase
import dk.cachet.carp.webservices.common.exception.serialization.SerializationException
import dk.cachet.carp.webservices.common.input.WS_JSON
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.springframework.util.StringUtils
import tools.jackson.core.JsonParser
import tools.jackson.databind.DeserializationContext
import tools.jackson.databind.ValueDeserializer

@Suppress("TooGenericExceptionCaught", "SwallowedException")
class DataStreamBatchDeserializer(private val validationMessages: MessageBase) : ValueDeserializer<DataStreamBatch>() {
    companion object {
        private val LOGGER: Logger = LogManager.getLogger()
    }

    override fun deserialize(
        p: JsonParser,
        ctxt: DeserializationContext,
    ): DataStreamBatch {
        val dataStreamBatch: String
        try {
            dataStreamBatch = ctxt.readTree(p).toString()

            if (!StringUtils.hasLength(dataStreamBatch)) {
                LOGGER.error("The dataStreamBatch cannot be blank or empty.")
                throw SerializationException(validationMessages.get("dataStreamConfig.deserialization.empty"))
            }
        } catch (ex: Exception) {
            LOGGER.error("The dataStreamBatch contains bad format. Exception: ${ex.message}")
            throw SerializationException(
                validationMessages.get("dataStreamBatch.deserialization.bad_format", ex.message.toString()),
            )
        }

        val parsed: DataStreamBatch
        try {
            parsed = WS_JSON.decodeFromString(dataStreamBatch)
        } catch (ex: Exception) {
            LOGGER.error("The core dataStreamBatch serializer is not valid. Exception: ${ex.message}")
            throw SerializationException(
                validationMessages.get("dataStreamBatch.deserialization.error", ex.message.toString()),
            )
        }

        return parsed
    }
}
