package dk.cachet.carp.webservices.datastream.listener

import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory
import org.springframework.amqp.rabbit.connection.ConnectionFactory
import org.springframework.boot.amqp.autoconfigure.SimpleRabbitListenerContainerFactoryConfigurer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * The listener container factory for [ThirdPartyDataStreamListener]: Spring Boot's default listener setup
 * (including `spring.rabbitmq.listener.simple.retry`), with [ThirdPartyDeadLetterAlert] around the retries and
 * every failure rejected without requeue, so each failed message is dead-lettered and reported exactly once.
 */
@Configuration
class ThirdPartyListenerConfig {
    companion object {
        const val CONTAINER_FACTORY = "thirdPartyListenerContainerFactory"
    }

    @Bean(CONTAINER_FACTORY)
    fun thirdPartyListenerContainerFactory(
        configurer: SimpleRabbitListenerContainerFactoryConfigurer,
        connectionFactory: ConnectionFactory,
        deadLetterAlert: ThirdPartyDeadLetterAlert,
    ): SimpleRabbitListenerContainerFactory =
        SimpleRabbitListenerContainerFactory().apply {
            configurer.configure(this, connectionFactory)
            setDefaultRequeueRejected(false)
            setAdviceChain(deadLetterAlert, *adviceChain.orEmpty())
        }
}
