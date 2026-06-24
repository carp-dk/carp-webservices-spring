package dk.cachet.carp.webservices.common.actuator.service.impl

import dk.cachet.carp.webservices.common.actuator.service.IPingConnection
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.springframework.boot.health.actuate.endpoint.HealthEndpoint
import org.springframework.boot.health.contributor.Status
import org.springframework.context.ApplicationContext
import org.springframework.stereotype.Service

@Service
class PingConnectionImpl(private var appContext: ApplicationContext) : IPingConnection {
    companion object {
        private val LOGGER: Logger = LogManager.getLogger()
    }

    override fun statusHealth(): Status {
        val healthEndpoint = appContext.getBean(HealthEndpoint::class.java)
        val healthEndpointStatus: Status = healthEndpoint.health().status
        LOGGER.info("Health endpoint status: $healthEndpointStatus.")
        return healthEndpointStatus
    }
}
