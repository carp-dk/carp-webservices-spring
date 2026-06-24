package dk.cachet.carp.webservices.common.actuator.service.impl

import dk.cachet.carp.webservices.common.actuator.service.IRabbitConnection
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.springframework.boot.amqp.health.RabbitHealthIndicator
import org.springframework.boot.health.contributor.Status
import org.springframework.context.ApplicationContext
import org.springframework.stereotype.Service

@Service
class RabbitConnectionImpl(private var appContext: ApplicationContext) : IRabbitConnection {
    companion object {
        private val LOGGER: Logger = LogManager.getLogger()
    }

    override fun statusHealth(): Status {
        val rabbitHealthIndicator = appContext.getBean(RabbitHealthIndicator::class.java)
        val rabbitHealthStatus: Status = rabbitHealthIndicator.health().status
        LOGGER.info("Rabbitmq Health: $rabbitHealthStatus.")
        return rabbitHealthStatus
    }

    override fun statusDetails(): MutableMap<String, Any>? {
        val rabbitHealthIndicator = appContext.getBean(RabbitHealthIndicator::class.java)
        val rabbitHealthDetails: MutableMap<String, Any>? = rabbitHealthIndicator.health().details
        LOGGER.info("Rabbitmq Health details: $rabbitHealthDetails.")
        return rabbitHealthDetails
    }
}
