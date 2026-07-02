package dk.cachet.carp.webservices.common.actuator.config

import dk.cachet.carp.webservices.common.actuator.service.*
import dk.cachet.carp.webservices.common.notification.domain.TeamsChannel
import dk.cachet.carp.webservices.common.notification.service.INotificationService
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled

/**
 * The Configuration Class [RegularStatusAlert].
 */
@Profile("!local & !staging & !testing & !development")
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = ["spring.task.scheduling.enabled"], havingValue = "true", matchIfMissing = true)
class RegularStatusAlert(
    private val ping: IPingConnection,
    private val rabbit: IRabbitConnection,
    private val database: IDatabaseConnection,
    private val disk: IDiskSpaceStatus,
    private val mail: IMailConnection,
    private val notificationService: INotificationService,
) {
    companion object {
        private val LOGGER: Logger = LogManager.getLogger()
        private const val NEW_LINE = "\n\n"
    }

    @Scheduled(cron = "0 30 8,12,15,17 * * *", zone = "Europe/Copenhagen")
    fun statusCheck() {
        val messageBuilder = StringBuilder()
        messageBuilder.append("== CARP Status ==")
        messageBuilder.append(NEW_LINE)

        if (ping.statusHealth().toString() == "UP") {
            messageBuilder.append("WS-Status: ${ping.statusHealth()} ✅")
        } else {
            messageBuilder.append("WS-Status: ${ping.statusHealth()} ❌ ")
        }
        messageBuilder.append(NEW_LINE)

        if (database.statusHealth().toString() == "UP") {
            messageBuilder.append("Db-Status: ${database.statusHealth()} ✅ | ${database.statusDetails()}")
        } else {
            messageBuilder.append("DB-Status: ${database.statusHealth()} ❌ | ${database.statusDetails()}")
        }
        messageBuilder.append(NEW_LINE)

        if (disk.statusHealth().toString() == "UP") {
            messageBuilder.append("Disk-Status: ${disk.statusHealth()} ✅ | ${disk.statusDetails()} ")
        } else {
            messageBuilder.append("Disk-Status: ${disk.statusHealth()} ❌ | ${disk.statusDetails()} ")
        }
        messageBuilder.append(NEW_LINE)

        if (rabbit.statusHealth().toString() == "UP") {
            messageBuilder.append("Rabbit-Status: ${rabbit.statusHealth()} ✅ | ${rabbit.statusDetails()}")
        } else {
            messageBuilder.append("Rabbit-Status: ${rabbit.statusHealth()} ❌ | ${rabbit.statusDetails()}")
        }
        messageBuilder.append(NEW_LINE)

        if (mail.mailServerConnection().toString() == "UP") {
            messageBuilder.append("Mail-Server-Status: ${mail.mailServerConnection()} ✅ ")
        } else {
            messageBuilder.append("Mail-Server-Status: ${mail.mailServerConnection()} ❌ ")
        }

        LOGGER.info("Regular status check: $messageBuilder")
        notificationService.sendAlertOrGeneralNotification(messageBuilder.toString(), TeamsChannel.HEARTBEAT)
    }
}
