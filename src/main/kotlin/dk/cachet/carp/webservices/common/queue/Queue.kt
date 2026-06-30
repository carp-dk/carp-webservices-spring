package dk.cachet.carp.webservices.common.queue

import org.springframework.amqp.core.*
import org.springframework.amqp.core.Queue
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.Environment

/**
 * The Class [Queue].
 * It contains the AMPQ Queue, Exchange and Binding configurations.
 * */
@Configuration
@Suppress("TooManyFunctions")
class Queue(environment: Environment) {
    /** Email queues */
    val emailSendingQueue = environment.getProperty("rabbit.email.sending.queue")!!
    val emailSendingDirectExchange = environment.getProperty("rabbit.email.sending.direct-ex")!!
    val emailSendingDlq = environment.getProperty("rabbit.email.sending.dlq")!!
    val emailSendingDlx = environment.getProperty("rabbit.email.sending.dlx")!!
    val emailSendingPlq = environment.getProperty("rabbit.email.sending.plq")!!
    val emailSendingPlx = environment.getProperty("rabbit.email.sending.plx")!!

    /** Study queues */
    val studiesQueue = environment.getProperty("rabbit.study.queue")!!
    val studiesDirectExchange = environment.getProperty("rabbit.study.direct-ex")!!
    val studiesDlq = environment.getProperty("rabbit.study.dlq")!!
    val studiesDlx = environment.getProperty("rabbit.study.dlx")!!

    /** Deployment queues */
    val deploymentsQueue = environment.getProperty("rabbit.deployment.queue")!!
    val deploymentsDirectExchange = environment.getProperty("rabbit.deployment.direct-ex")!!
    val deploymentsDlq = environment.getProperty("rabbit.deployment.dlq")!!
    val deploymentsDlx = environment.getProperty("rabbit.deployment.dlx")!!

    /**
     * Exchange declarations
     */
    @Bean
    fun emailSendingDirectExchange(): DirectExchange {
        return DirectExchange(emailSendingDirectExchange)
    }

    @Bean
    fun emailSendingDLX(): FanoutExchange {
        return FanoutExchange(emailSendingDlx)
    }

    @Bean
    fun emailSendingPLX(): FanoutExchange {
        return FanoutExchange(emailSendingPlx)
    }

    @Bean
    fun studyDirectExchange(): DirectExchange {
        return DirectExchange(studiesDirectExchange)
    }

    @Bean
    fun studyDLX(): FanoutExchange {
        return FanoutExchange(studiesDlx)
    }

    @Bean
    fun deploymentDirectExchange(): DirectExchange {
        return DirectExchange(deploymentsDirectExchange)
    }

    @Bean
    fun deploymentDLX(): FanoutExchange {
        return FanoutExchange(deploymentsDlx)
    }

    /**
     * Queue declarations
     */
    @Bean
    fun emailSendingQueue(): Queue {
        return QueueBuilder
            .durable(emailSendingQueue)
            .withArgument("x-dead-letter-exchange", emailSendingDlx)
            .build()
    }

    @Bean
    fun emailSendingDLQ(): Queue {
        return QueueBuilder.durable(emailSendingDlq).build()
    }

    @Bean
    fun emailSendingPLQ(): Queue {
        return QueueBuilder.durable(emailSendingPlq).build()
    }

    @Bean
    fun studyQueue(): Queue {
        return QueueBuilder
            .durable(studiesQueue)
            .withArgument("x-dead-letter-exchange", studiesDlx)
            .build()
    }

    @Bean
    fun studyDLQ(): Queue {
        return QueueBuilder.durable(studiesDlq).build()
    }

    @Bean
    fun deploymentQueue(): Queue {
        return QueueBuilder
            .durable(deploymentsQueue)
            .withArgument("x-dead-letter-exchange", deploymentsDlx)
            .build()
    }

    @Bean
    fun deploymentDLQ(): Queue {
        return QueueBuilder.durable(deploymentsDlq).build()
    }

    /**
     * Binding declarations
     */
    @Bean
    fun emailSendingBinding(): Binding {
        return BindingBuilder.bind(emailSendingQueue()).to(emailSendingDirectExchange()).with(emailSendingQueue)
    }

    @Bean
    fun emailSendingQueueDeadLetterBinding(): Binding {
        return BindingBuilder.bind(emailSendingDLQ()).to(emailSendingDLX())
    }

    @Bean
    fun emailSendingParkingLotBinding(): Binding {
        return BindingBuilder.bind(emailSendingPLQ()).to(emailSendingPLX())
    }

    @Bean
    fun studyBinding(): Binding {
        return BindingBuilder.bind(studyQueue()).to(studyDirectExchange()).with(studiesQueue)
    }

    @Bean
    fun studyQueueDeadLetterBinding(): Binding {
        return BindingBuilder.bind(studyDLQ()).to(studyDLX())
    }

    @Bean
    fun deploymentBinding(): Binding {
        return BindingBuilder.bind(deploymentQueue()).to(deploymentDirectExchange()).with(deploymentsQueue)
    }

    @Bean
    fun deploymentQueueDeadLetterBinding(): Binding {
        return BindingBuilder.bind(deploymentDLQ()).to(deploymentDLX())
    }
}
