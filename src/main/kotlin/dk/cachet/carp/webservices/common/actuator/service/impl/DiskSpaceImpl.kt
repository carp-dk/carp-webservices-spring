package dk.cachet.carp.webservices.common.actuator.service.impl

import dk.cachet.carp.webservices.common.actuator.service.IDiskSpaceStatus
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.springframework.boot.health.application.DiskSpaceHealthIndicator
import org.springframework.boot.health.contributor.Status
import org.springframework.context.ApplicationContext
import org.springframework.stereotype.Service

@Service
class DiskSpaceImpl(private var appContext: ApplicationContext) : IDiskSpaceStatus {
    companion object {
        private val LOGGER: Logger = LogManager.getLogger()
    }

    override fun statusHealth(): Status {
        val diskSpaceHealthIndicator = appContext.getBean(DiskSpaceHealthIndicator::class.java)
        val health = diskSpaceHealthIndicator.health()
        LOGGER.info("Disk status: $health")
        return health.status
    }

    override fun statusDetails(): MutableMap<String, Any>? {
        val diskSpaceHealthIndicator = appContext.getBean(DiskSpaceHealthIndicator::class.java)
        val health = diskSpaceHealthIndicator.health()
        LOGGER.info("Disk status details: $health")
        return health.details
    }
}
