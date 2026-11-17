package dk.cachet.carp.webservices.datastream.listener

import dk.cachet.carp.data.infrastructure.DataStreamServiceRequest
import dk.cachet.carp.webservices.common.input.WS_JSON
import dk.cachet.carp.webservices.datastream.service.core.CoreDataStreamService
import kotlinx.coroutines.runBlocking
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.springframework.amqp.AmqpRejectAndDontRequeueException
import org.springframework.amqp.core.Message
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.stereotype.Component

/**
 * Stores third-party (wearables) data published by CARP Gardener on the third-party queue.
 *
 * Each message is a [DataStreamServiceRequest.AppendToDataStreams] serialized with CARP core's default JSON, and is
 * appended through [CoreDataStreamService], so the same checks as the data stream endpoint apply (the deployment's
 * data streams must exist, be configured for the sequences and be open).
 *
 * The core service is called without the authorizing decorator: there is no user principal on a queue message, and
 * only services with broker access on the internal network can publish to this queue.
 *
 * Messages that can never be stored (malformed, unknown or closed deployment, unconfigured data stream) are rejected
 * at once and go to the dead-letter queue. Other failures, e.g. the database being unreachable, are retried as
 * configured under `spring.rabbitmq.listener.simple.retry` and then dead-lettered too. Either way
 * [ThirdPartyDeadLetterAlert] logs it and posts it to Teams.
 */
@Component
class ThirdPartyDataStreamListener(
    private val dataStreamService: CoreDataStreamService,
) {
    companion object {
        private val LOGGER: Logger = LogManager.getLogger()
    }

    @RabbitListener(
        queues = ["\${rabbit.third-party.processing.queue}"],
        containerFactory = ThirdPartyListenerConfig.CONTAINER_FACTORY,
    )
    fun receive(message: Message) {
        val request = decode(message)
        val deploymentId = request.studyDeploymentId

        try {
            runBlocking { dataStreamService.appendToDataStreams(deploymentId, request.batch) }
        } catch (ex: IllegalArgumentException) {
            throw reject("Rejected third-party data for deployment $deploymentId: ${ex.message}", ex)
        } catch (ex: IllegalStateException) {
            throw reject("Rejected third-party data for deployment $deploymentId: ${ex.message}", ex)
        }

        LOGGER.info(
            "Stored third-party data for deployment $deploymentId " +
                "(${request.batch.sequences.count()} sequence(s)).",
        )
    }

    private fun decode(message: Message): DataStreamServiceRequest.AppendToDataStreams =
        try {
            WS_JSON.decodeFromString(
                DataStreamServiceRequest.AppendToDataStreams.serializer(),
                message.body.decodeToString(),
            )
        } catch (ex: IllegalArgumentException) {
            // kotlinx SerializationException is an IllegalArgumentException; so are invalid UUIDs and data stream ids.
            throw reject("Rejected malformed third-party message: ${ex.message}", ex)
        }

    /** Skips the retries for a message that can never be stored; [ThirdPartyDeadLetterAlert] reports it. */
    private fun reject(
        reason: String,
        cause: Exception,
    ) = AmqpRejectAndDontRequeueException(reason, cause)
}
