package dk.cachet.carp.webservices.datastream.serdes

import dk.cachet.carp.common.application.data.Data
import dk.cachet.carp.data.application.Measurement
import dk.cachet.carp.webservices.common.configuration.internationalisation.service.MessageBase
import dk.cachet.carp.webservices.common.exception.serialization.SerializationException
import dk.cachet.carp.webservices.common.input.WS_JSON
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import tools.jackson.core.JsonGenerator
import tools.jackson.databind.SerializationContext
import tools.jackson.databind.ValueSerializer

@Suppress("TooGenericExceptionCaught", "SwallowedException")
class MeasurementSerializer(private val validationMessages: MessageBase) : ValueSerializer<Measurement<Data>>() {
    companion object {
        private val LOGGER: Logger = LogManager.getLogger()
    }

    override fun serialize(
        value: Measurement<Data>,
        gen: JsonGenerator,
        serializers: SerializationContext,
    ) {
        if (value == null) {
            LOGGER.error("The dataStreamServiceRequest.measurement is null.")
            throw SerializationException(validationMessages.get("data.stream.measurement.serialization.empty"))
        }

        val serialized: String
        try {
            serialized = WS_JSON.encodeToString(dk.cachet.carp.data.application.MeasurementSerializer, value)
        } catch (ex: Exception) {
            LOGGER.error("The dataStreamServiceRequest.measurement is not valid. Exception: ${ex.message}")
            throw SerializationException(
                validationMessages.get("data.stream.measurement.serialization.error", ex.message.toString()),
            )
        }

        gen!!.writeRawValue(serialized)
    }
}
