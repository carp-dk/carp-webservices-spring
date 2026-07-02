package dk.cachet.carp.webservices.common.actuator.config

import dk.cachet.carp.webservices.common.actuator.service.IDatabaseConnection
import dk.cachet.carp.webservices.common.notification.domain.TeamsChannel
import dk.cachet.carp.webservices.common.notification.service.INotificationService
import dk.cachet.carp.webservices.email.service.EmailService
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled

/**
 * The Configuration Class [DatabaseConnectionAlert].
 * The [DatabaseConnectionAlert] sends a notification to the System Admins if the database connection is down.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(value = ["database.connection.alert.enabled"], havingValue = "true")
@ConditionalOnProperty(name = ["spring.task.scheduling.enabled"], havingValue = "true", matchIfMissing = true)
class DatabaseConnectionAlert(
    private var database: IDatabaseConnection,
    private val notificationService: INotificationService,
    private val emailNotificationService: EmailService,
    @param:Value("\${alert.admin-email}") private val alertEmail: String,
    @param:Value("\${alert.subject}") private val alertWarning: String,
) {
    companion object {
        private val LOGGER: Logger = LogManager.getLogger()
    }

    @Scheduled(fixedDelay = 30 * 60 * 1000)
    fun checkRegularDatabaseConnection() {
        if ("DOWN" == database.statusHealth().code) {
            LOGGER.warn("Database connection is DOWN!")
            val databaseConnectionStatusNotification =
                "Database connection is ${database.statusHealth().code}!." +
                    " More details...: ${database.statusDetails()}"

            notificationService.sendAlertOrGeneralNotification(
                databaseConnectionStatusNotification,
                TeamsChannel.HEARTBEAT,
            )

            emailNotificationService.sendNotificationEmail(
                alertEmail,
                alertWarning,
                databaseConnectionStatusNotification,
            )
        }
    }
}
