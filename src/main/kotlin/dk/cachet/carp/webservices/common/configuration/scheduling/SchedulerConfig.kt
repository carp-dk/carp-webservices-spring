package dk.cachet.carp.webservices.common.configuration.scheduling

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling

/**
 * The Configuration Class [SchedulerConfig].
 * The [SchedulerConfig] enables Spring's scheduled task execution capability.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = ["spring.task.scheduling.enabled"], havingValue = "true", matchIfMissing = true)
class SchedulerConfig {
    //
}
