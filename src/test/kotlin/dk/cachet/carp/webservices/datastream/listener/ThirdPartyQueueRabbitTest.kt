package dk.cachet.carp.webservices.datastream.listener

import com.rabbitmq.client.BuiltinExchangeType
import dk.cachet.carp.webservices.common.exception.advices.CarpErrorResponse
import dk.cachet.carp.webservices.common.notification.service.INotificationService
import dk.cachet.carp.webservices.common.queue.ListenerRetryConfig
import dk.cachet.carp.webservices.common.queue.Queue
import dk.cachet.carp.webservices.datastream.listener.ThirdPartyDataStreamListenerTest.Companion.DEPLOYMENT_ID
import dk.cachet.carp.webservices.datastream.listener.ThirdPartyDataStreamListenerTest.Companion.GARDENER_PAYLOAD
import dk.cachet.carp.webservices.datastream.service.core.CoreDataStreamService
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.serialization.SerializationException
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.amqp.core.Message
import org.springframework.amqp.core.MessageListener
import org.springframework.amqp.core.MessageProperties
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerEndpoint
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory
import org.springframework.amqp.rabbit.core.RabbitAdmin
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.boot.amqp.autoconfigure.RabbitAutoConfiguration
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.dao.DataAccessResourceFailureException
import org.springframework.mock.env.MockEnvironment
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Runs the third-party queue against a real broker: the topology CAWS declares must be accepted next to the one
 * CARP Gardener declares, and failed messages must end up in Gardener's dead-letter queue, reported exactly once.
 */
@Testcontainers(disabledWithoutDocker = true)
class ThirdPartyQueueRabbitTest {
    companion object {
        private const val PROFILE = "test"
        private const val QUEUE = "third-party-queue-$PROFILE"
        private const val EXCHANGE = "third-party-direct-exchange-$PROFILE"
        private const val DLQ = "$QUEUE.dlq"
        private const val DLX = "$QUEUE.dlx"
        private const val MAX_RETRIES = 3L
        private const val RECEIVE_TIMEOUT_MS = 10_000L

        @Container
        @JvmStatic
        val rabbit: GenericContainer<*> =
            GenericContainer("rabbitmq:3")
                .withEnv("RABBITMQ_DEFAULT_USER", "admin")
                .withEnv("RABBITMQ_DEFAULT_PASS", "admin")
                .withExposedPorts(5672)
                .waitingFor(Wait.forLogMessage(".*Server startup complete.*", 1))
    }

    private val dataStreamService: CoreDataStreamService = mockk()
    private val notificationService: INotificationService = mockk(relaxed = true)
    private lateinit var connectionFactory: CachingConnectionFactory
    private lateinit var template: RabbitTemplate

    /** The application's listener setup: Boot's Rabbit auto-configuration with CAWS's retry tweak and factory. */
    private val contextRunner =
        ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(RabbitAutoConfiguration::class.java))
            .withUserConfiguration(ListenerRetryConfig::class.java, ThirdPartyListenerConfig::class.java)
            .withBean(ThirdPartyDeadLetterAlert::class.java, { ThirdPartyDeadLetterAlert(notificationService) })
            .withPropertyValues(
                "spring.rabbitmq.host=${rabbit.host}",
                "spring.rabbitmq.port=${rabbit.getMappedPort(5672)}",
                "spring.rabbitmq.username=admin",
                "spring.rabbitmq.password=admin",
                "spring.rabbitmq.listener.simple.retry.enabled=true",
                "spring.rabbitmq.listener.simple.retry.max-retries=$MAX_RETRIES",
                "spring.rabbitmq.listener.simple.retry.initial-interval=10ms",
            )

    @BeforeEach
    fun setup() {
        connectionFactory =
            CachingConnectionFactory(rabbit.host, rabbit.getMappedPort(5672)).apply {
                username = "admin"
                setPassword("admin")
            }
        template = RabbitTemplate(connectionFactory)

        declareAsGardener()
        declareAsCaws()
    }

    @AfterEach
    fun teardown() {
        RabbitAdmin(connectionFactory).apply {
            deleteQueue(QUEUE)
            deleteQueue(DLQ)
            deleteExchange(EXCHANGE)
            deleteExchange(DLX)
        }
        connectionFactory.destroy()
    }

    @Test
    fun `stores a valid message and dead-letters nothing`() =
        withListener {
            coEvery { dataStreamService.appendToDataStreams(any(), any()) } returns Unit

            publish(GARDENER_PAYLOAD)

            coVerify(timeout = RECEIVE_TIMEOUT_MS) { dataStreamService.appendToDataStreams(DEPLOYMENT_ID, any()) }
            assertNull(template.receive(DLQ, 500))
            verify(exactly = 0) { notificationService.sendExceptionNotification(any(), any()) }
        }

    @Test
    fun `dead-letters and reports a malformed message`() =
        withListener {
            publish("{not json")

            assertDeadLettered("{not json")
            coVerify(exactly = 0) { dataStreamService.appendToDataStreams(any(), any()) }
            assertReportedOnce(SerializationException::class.java)
        }

    @Test
    fun `dead-letters and reports data for closed data streams without retrying`() =
        withListener {
            coEvery { dataStreamService.appendToDataStreams(any(), any()) } throws IllegalStateException("closed")

            publish(GARDENER_PAYLOAD)

            assertDeadLettered(GARDENER_PAYLOAD)
            coVerify(exactly = 1) { dataStreamService.appendToDataStreams(any(), any()) }
            assertReportedOnce(IllegalStateException::class.java)
        }

    @Test
    fun `retries other failures, then dead-letters and reports them once`() =
        withListener {
            coEvery { dataStreamService.appendToDataStreams(any(), any()) } throws
                DataAccessResourceFailureException("database unavailable")

            publish(GARDENER_PAYLOAD)

            assertDeadLettered(GARDENER_PAYLOAD)
            coVerify(exactly = 1 + MAX_RETRIES.toInt()) { dataStreamService.appendToDataStreams(any(), any()) }
            assertReportedOnce(DataAccessResourceFailureException::class.java)
        }

    /** Runs [block] with the listener consuming the queue through the application's container factory. */
    private fun withListener(block: () -> Unit) {
        contextRunner.run { context ->
            val factory =
                context.getBean(
                    ThirdPartyListenerConfig.CONTAINER_FACTORY,
                    SimpleRabbitListenerContainerFactory::class.java,
                )
            val endpoint =
                SimpleRabbitListenerEndpoint().apply {
                    setId("third-party-test")
                    setQueueNames(QUEUE)
                    setMessageListener(MessageListener { ThirdPartyDataStreamListener(dataStreamService).receive(it) })
                }
            val container = factory.createListenerContainer(endpoint)
            container.afterPropertiesSet()
            container.start()
            try {
                block()
            } finally {
                container.stop()
            }
        }
    }

    /** Mirrors RabbitMqTopology in carp-gardener, which may run before CAWS on a fresh broker. */
    private fun declareAsGardener() {
        connectionFactory.createConnection().createChannel(false).use { channel ->
            channel.exchangeDeclare(EXCHANGE, BuiltinExchangeType.DIRECT, true)
            channel.exchangeDeclare(DLX, BuiltinExchangeType.FANOUT, true)
            channel.queueDeclare(DLQ, true, false, false, null)
            channel.queueBind(DLQ, DLX, "")
            channel.queueDeclare(QUEUE, true, false, false, mapOf("x-dead-letter-exchange" to DLX))
            channel.queueBind(QUEUE, EXCHANGE, QUEUE)
        }
    }

    /** Declares CAWS's beans; RabbitMQ throws PRECONDITION_FAILED if any differs from Gardener's. */
    private fun declareAsCaws() {
        val environment =
            MockEnvironment().apply {
                setProperty("rabbit.third-party.processing.queue", QUEUE)
                setProperty("rabbit.third-party.processing.direct-ex", EXCHANGE)
                setProperty("rabbit.third-party.processing.dlq", DLQ)
                setProperty("rabbit.third-party.processing.dlx", DLX)
                listOf("email.sending", "study", "deployment").forEach { prefix ->
                    listOf("queue", "direct-ex", "dlq", "dlx", "plq", "plx").forEach {
                        setProperty("rabbit.$prefix.$it", "unused")
                    }
                }
            }
        val beans = Queue(environment)
        RabbitAdmin(connectionFactory).apply {
            declareExchange(beans.thirdPartyDirectExchange())
            declareExchange(beans.thirdPartyDLX())
            declareQueue(beans.thirdPartyDLQ())
            declareQueue(beans.thirdPartyQueue())
            declareBinding(beans.thirdPartyBinding())
            declareBinding(beans.thirdPartyQueueDeadLetterBinding())
        }
    }

    /** Publishes like Gardener: to the direct exchange, routed by the queue name, without message properties. */
    private fun publish(body: String) = template.send(EXCHANGE, QUEUE, Message(body.toByteArray(), MessageProperties()))

    private fun assertDeadLettered(body: String) {
        val deadLetter = assertNotNull(template.receive(DLQ, RECEIVE_TIMEOUT_MS), "Expected a message in $DLQ.")
        assertEquals(body, deadLetter.body.decodeToString())
        assertNotNull(deadLetter.messageProperties.headers["x-death"], "Expected the broker to have dead-lettered it.")
    }

    private fun assertReportedOnce(exception: Class<out Throwable>) {
        val reported = slot<CarpErrorResponse>()
        verify(exactly = 1, timeout = RECEIVE_TIMEOUT_MS) {
            notificationService.sendExceptionNotification(capture(reported), any())
        }
        assertTrue(exception.isAssignableFrom(Class.forName(reported.captured.exception)), reported.captured.exception)
        assertEquals("AMQP $QUEUE", reported.captured.path)
    }
}
