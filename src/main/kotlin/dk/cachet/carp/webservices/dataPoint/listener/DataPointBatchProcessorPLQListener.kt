package dk.cachet.carp.webservices.dataPoint.listener

import dk.cachet.carp.webservices.common.notification.domain.TeamsChannel
import dk.cachet.carp.webservices.common.notification.service.INotificationService
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.springframework.amqp.core.Message
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

/**
 * The Parking-Lot-Queue listener for datapoint processing.
 * It updated the email entity in the database to the status of FAILED
 * and sends notification.
 */
@Deprecated("DataPoint is deprecated. Use DataStream instead.")
@Component
class DataPointBatchProcessorPLQListener {
    companion object {
        private val LOGGER: Logger = LogManager.getLogger()
    }

    @Autowired
    lateinit var notificationService: INotificationService

    @RabbitListener(queues = ["\${rabbit.data-point.processing.plq}"])
    fun receive(failedMessage: Message) {
        LOGGER.info("New Data Point message has arrived in the Parking Lot.")
        LOGGER.debug(failedMessage)
        notificationService.sendAlertOrGeneralNotification(
            "New Data Point message has arrived in the DataPoint Parking Lot.",
            TeamsChannel.SERVER_ERRORS,
        )
    }
}
