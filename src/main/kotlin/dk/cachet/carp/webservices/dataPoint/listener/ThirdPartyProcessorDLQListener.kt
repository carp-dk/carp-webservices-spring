package dk.cachet.carp.webservices.dataPoint.listener

import dk.cachet.carp.webservices.common.queue.DLQListener
import org.springframework.amqp.core.Message
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.core.env.Environment
import org.springframework.stereotype.Component

/**
 * The Dead-Letter-Queue listener for 3rd-party datapoint processing.
 * It requeues the messages when the messages did not surpass the retry threshold
 * or redirects them to the Parking-Lot-Queue.
 */
@Deprecated("DataPoint is deprecated. Use DataStream instead.")
@Component
class ThirdPartyProcessorDLQListener(
    private val rabbitTemplate: RabbitTemplate,
    private val environment: Environment,
) : DLQListener() {
    @RabbitListener(queues = ["\${rabbit.third-party.processing.dlq}"])
    fun receive(failedMessage: Message) {
        if (!assertAndIncrementRetriesHeader(failedMessage)) {
            rabbitTemplate.send(
                environment.getProperty("rabbit.third-party.processing.plx")!!,
                failedMessage.messageProperties.receivedRoutingKey,
                failedMessage,
            )
            return
        }

        rabbitTemplate.send(
            environment.getProperty("rabbit.third-party.processing.direct-ex")!!,
            failedMessage.messageProperties.receivedRoutingKey,
            failedMessage,
        )
    }
}
