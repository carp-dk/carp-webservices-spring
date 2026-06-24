package dk.cachet.carp.webservices.common.actuator.service.impl

import dk.cachet.carp.webservices.common.actuator.service.IDatabaseConnection
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.springframework.boot.health.contributor.Health
import org.springframework.boot.health.contributor.Status
import org.springframework.boot.jdbc.health.DataSourceHealthIndicator
import org.springframework.context.ApplicationContext
import org.springframework.stereotype.Service

@Service
class DataBaseConnectionImpl(private var appContext: ApplicationContext) : IDatabaseConnection {
    companion object {
        private val LOGGER: Logger = LogManager.getLogger()
    }

    override fun statusHealth(): Status {
        val dataSourceHealthIndicator = appContext.getBean(DataSourceHealthIndicator::class.java)
        val health: Health = dataSourceHealthIndicator.health()
        LOGGER.info("Database connection status: ${health.status}")
        return health.status
    }

    override fun statusDetails(): MutableMap<String, Any>? {
        val dataSourceHealthIndicator = appContext.getBean(DataSourceHealthIndicator::class.java)
        val health: Health = dataSourceHealthIndicator.health()
        LOGGER.info("Database connection details: ${health.details}")
        return health.details
    }
}
