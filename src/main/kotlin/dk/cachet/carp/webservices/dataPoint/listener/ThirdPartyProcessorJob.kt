package dk.cachet.carp.webservices.dataPoint.listener

import com.fasterxml.jackson.databind.ObjectMapper
import com.google.gson.JsonParseException
import dk.cachet.carp.webservices.common.exception.serialization.SerializationException
import dk.cachet.carp.webservices.dataPoint.domain.DataPoint
import dk.cachet.carp.webservices.dataPoint.service.DataPointService
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.springframework.amqp.core.Message
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.stereotype.Component

@Deprecated("DataPoint is deprecated. Use DataStream instead.")
@Component
class ThirdPartyProcessorJob(
    private val objectMapper: ObjectMapper,
    private val dataPointService: DataPointService,
) {
    companion object {
        private val LOGGER: Logger = LogManager.getLogger()
    }

    @RabbitListener(queues = ["\${rabbit.third-party.processing.queue}"])
    fun process(message: Message) {
        LOGGER.info("New message received from Third-party queue.")
        val jsonDataPoint = message.body.decodeToString()
        val datapoint = parseDataPoint(jsonDataPoint)
        datapoint.deploymentId = datapoint.carpHeader!!.studyId
        dataPointService.create(datapoint)
    }

    private fun parseDataPoint(json: String): DataPoint {
        try {
            return objectMapper.readValue(json, DataPoint::class.java)
        } catch (ex: JsonParseException) {
            LOGGER.info("Failed to parse 3rd-party data-point: ${ex.message}")
            throw SerializationException("Failed to parse 3rd-party data-point: ${ex.message}")
        }
    }
}
