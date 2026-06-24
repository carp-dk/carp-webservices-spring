package dk.cachet.carp.webservices.datastream.serdes

import dk.cachet.carp.common.application.data.Data
import dk.cachet.carp.data.application.Measurement
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
class MeasurementDeserializer(private val validationMessages: MessageBase) : ValueDeserializer<Measurement<Data>>() {
    companion object {
        private val LOGGER: Logger = LogManager.getLogger()
    }

    override fun deserialize(
        p: JsonParser,
        ctxt: DeserializationContext,
    ): Measurement<Data> {
        val syncPoint: String
        try {
            syncPoint = ctxt.readTree(p).toString()

            if (!StringUtils.hasLength(syncPoint)) {
                LOGGER.error("dataStreamServiceRequest.measurement cannot be blank or empty.")
                throw SerializationException(validationMessages.get("data.stream.measurement.deserialization.empty"))
            }
        } catch (ex: Exception) {
            LOGGER.error("The dataStreamServiceRequest.measurement contains bad format. Exception: ${ex.message}")
            throw SerializationException(
                validationMessages.get("data.stream.measurement.deserialization.bad_format", ex.message.toString()),
            )
        }

        val parsed: Measurement<Data>
        try {
            parsed = WS_JSON.decodeFromString(dk.cachet.carp.data.application.MeasurementSerializer, syncPoint)
        } catch (ex: Exception) {
            LOGGER.error(
                "The dataStreamServiceRequest.measurement deserializer is not valid. Exception: $ex",
            )
            throw SerializationException(
                validationMessages.get("data.stream.measurement.deserialization.error", ex.message.toString()),
            )
        }

        return parsed
    }
}
