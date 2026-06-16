package dk.cachet.carp.webservices.dataPoint.listener

import com.fasterxml.jackson.databind.ObjectMapper
import com.google.gson.JsonParseException
import dk.cachet.carp.webservices.dataPoint.domain.DataPoint
import dk.cachet.carp.webservices.dataPoint.service.DataPointService
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.springframework.amqp.rabbit.annotation.RabbitHandler
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Lazy
import org.springframework.core.env.Environment
import org.springframework.stereotype.Component
import org.springframework.web.multipart.MultipartFile

/**
 * The Class [DataPointBatchProcessorJob].
 * The [DataPointBatchProcessorJob] process the data points using the RabbitMQ with the message queue.
 */
@Deprecated("DataPoint is deprecated. Use DataStream instead.")
@RabbitListener(queues = ["\${rabbit.data-point.processing.queue}"])
@Component
class DataPointBatchProcessorJob(private val environment: Environment) {
    companion object {
        private val LOGGER: Logger = LogManager.getLogger()
    }

    @Autowired
    lateinit var rabbitTemplate: RabbitTemplate

    @Autowired
    @Lazy
    lateinit var dataPointService: DataPointService

    @Autowired
    lateinit var objectMapper: ObjectMapper

    /**
     * The function [process] queues the data point for processing.
     * @param dataPoints The [dataPoints] to be processed.
     */
    fun process(dataPoints: Array<DataPoint>) {
        rabbitTemplate.convertAndSend(environment.getProperty("rabbit.data-point.processing.queue")!!, dataPoints)
        LOGGER.info("A new batch of data points is sent to the message queue.")
    }

    @RabbitHandler
    fun receive(dataPoints: Array<DataPoint>) {
        dataPoints.forEach { dataPoint -> dataPointService.create(dataPoint) }
    }

    /**
     * The function [parseBatchFile] parses the file requests as a batch.
     *
     * @param dataPointJsonFile The [dataPointJsonFile] file as a multiple part file.
     * @return A [DataPoint] containing the parsed batch file.
     */
    fun parseBatchFile(dataPointJsonFile: MultipartFile): Array<DataPoint>? {
        val dataPoints: Array<DataPoint>
        try {
            dataPoints = objectMapper.readValue(String(dataPointJsonFile.bytes), Array<DataPoint>::class.java)
        } catch (ex: JsonParseException) {
            LOGGER.warn("The parsed [Json] file does not have any content.")
            return null
        }
        return dataPoints
    }
}
