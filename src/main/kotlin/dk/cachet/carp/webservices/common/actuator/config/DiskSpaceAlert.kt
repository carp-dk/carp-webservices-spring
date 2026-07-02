package dk.cachet.carp.webservices.common.actuator.config

import dk.cachet.carp.webservices.common.actuator.service.IDiskSpaceStatus
import dk.cachet.carp.webservices.common.notification.domain.TeamsChannel
import dk.cachet.carp.webservices.common.notification.service.INotificationService
import dk.cachet.carp.webservices.email.service.EmailService
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.configurationprocessor.json.JSONObject
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import java.util.*

/**
 * The Configuration Class [DiskSpaceAlert].
 * The [DiskSpaceAlert] sends an notification to the System Admins if the disk size is greater than 80% of the disk.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(value = ["disk.space.alert.enabled"], havingValue = "true")
@ConditionalOnProperty(name = ["spring.task.scheduling.enabled"], havingValue = "true", matchIfMissing = true)
class DiskSpaceAlert(
    private val diskSpace: IDiskSpaceStatus,
    private val notificationService: INotificationService,
    private val emailNotificationService: EmailService,
    @param:Value("\${alert.admin-email}") private val alertEmail: String,
    @param:Value("\${alert.subject}") private val alertWarning: String,
) {
    companion object {
        private val LOGGER: Logger = LogManager.getLogger()
        private const val LIMIT_PERCENT = 80
    }

    @Scheduled(fixedDelay = 30 * 60 * 1000)
    @Suppress("MagicNumber")
    fun checkRegularDiskSpace() {
        val statusHealth = diskSpace.statusHealth()
        val diskSpaceHealth = JSONObject(diskSpace.statusDetails())

        if ("UP" == statusHealth.code && diskSpaceHealth.length() != 0) {
            val freeStorageSpace: String = diskSpaceHealth.getString("free")
            val totalStorageSpace: String = diskSpaceHealth.getString("total")
            val usableSpace = totalStorageSpace.toDouble() - freeStorageSpace.toDouble()
            val usablePercentage = usableSpace / totalStorageSpace.toDouble()
            if (totalStorageSpace.toDouble() > 0 && (usablePercentage * 100).toInt() > LIMIT_PERCENT) {
                val spaceUsageNotification =
                    String.format(
                        Locale.getDefault(),
                        "WARNING: HDD has reached %d%% disk space usage!",
                        (usablePercentage * 100).toInt(),
                    )
                LOGGER.warn("WARNING: Disk space has reached {} of usage!", (usablePercentage * 100).toInt())
                notificationService.sendAlertOrGeneralNotification(spaceUsageNotification, TeamsChannel.HEARTBEAT)
                emailNotificationService.sendNotificationEmail(alertEmail, alertWarning, spaceUsageNotification)
            }
        }
    }
}
