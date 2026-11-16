package dk.cachet.carp.webservices.common.queue

import org.springframework.amqp.AmqpRejectAndDontRequeueException
import org.springframework.boot.amqp.autoconfigure.RabbitListenerRetrySettingsCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Tunes the listener retry configured under `spring.rabbitmq.listener.simple.retry`.
 */
@Configuration
class ListenerRetryConfig {
    /**
     * By default every listener exception is retried, so a listener that already knows a message can never succeed
     * (and throws [AmqpRejectAndDontRequeueException] to say so) would still wait out all retries before the message
     * is dead-lettered. Skip the retries for it; the exception is matched anywhere in the cause chain.
     */
    @Bean
    fun skipRetryOnRejectCustomizer(): RabbitListenerRetrySettingsCustomizer =
        RabbitListenerRetrySettingsCustomizer { settings ->
            settings.exceptionExcludes = settings.exceptionExcludes + AmqpRejectAndDontRequeueException::class.java
        }
}
