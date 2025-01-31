package dk.cachet.carp.webservices.export.scheduler

import dk.cachet.carp.webservices.export.service.ExportService
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class ExportCleanup(
    private val exportService: ExportService,
) {
    companion object {
        private val LOGGER: Logger = LogManager.getLogger()
    }

    @Suppress("MagicNumber")
    @Scheduled(cron = "0 5 9 * * ?")
    fun cleanup() {
        LOGGER.info("Cleaning up exports...")
        exportService.deleteAllOlderThan(7)
    }
}
