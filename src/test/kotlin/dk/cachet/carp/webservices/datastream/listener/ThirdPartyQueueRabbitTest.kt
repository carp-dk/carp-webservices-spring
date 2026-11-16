package dk.cachet.carp.webservices.datastream.listener

import com.rabbitmq.client.BuiltinExchangeType
import dk.cachet.carp.webservices.common.queue.ListenerRetryConfig
import dk.cachet.carp.webservices.common.queue.Queue
import dk.cachet.carp.webservices.datastream.listener.ThirdPartyDataStreamListenerTest.Companion.DEPLOYMENT_ID
import dk.cachet.carp.webservices.datastream.listener.ThirdPartyDataStreamListenerTest.Companion.GARDENER_PAYLOAD
import dk.cachet.carp.webservices.datastream.service.core.CoreDataStreamService
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.amqp.core.Message
import org.springframework.amqp.core.MessageProperties
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory
import org.springframework.amqp.rabbit.core.RabbitAdmin
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer
import org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer
import org.springframework.boot.retry.RetryPolicySettings
import org.springframework.dao.DataAccessResourceFailureException
import org.springframework.mock.env.MockEnvironment
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Runs the third-party queue against a real broker: the topology CAWS declares must be accepted next to the one
 * CARP Gardener declares, and failed messages must end up in Gardener's dead-letter queue.
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
    private lateinit var connectionFactory: CachingConnectionFactory
    private lateinit var template: RabbitTemplate
    private lateinit var container: SimpleMessageListenerContainer

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

        container =
            SimpleMessageListenerContainer(connectionFactory).apply {
                setQueueNames(QUEUE)
                setMessageListener { ThirdPartyDataStreamListener(dataStreamService).receive(it) }
                setAdviceChain(bootLikeRetry())
                start()
            }
    }

    @AfterEach
    fun teardown() {
        container.stop()
        RabbitAdmin(connectionFactory).apply {
            deleteQueue(QUEUE)
            deleteQueue(DLQ)
            deleteExchange(EXCHANGE)
            deleteExchange(DLX)
        }
        connectionFactory.destroy()
    }

    @Test
    fun `stores a valid message and dead-letters nothing`() {
        coEvery { dataStreamService.appendToDataStreams(any(), any()) } returns Unit

        publish(GARDENER_PAYLOAD)

        coVerify(timeout = RECEIVE_TIMEOUT_MS) { dataStreamService.appendToDataStreams(DEPLOYMENT_ID, any()) }
        assertNull(template.receive(DLQ, 500))
    }

    @Test
    fun `dead-letters a malformed message`() {
        publish("{not json")

        assertDeadLettered("{not json")
        coVerify(exactly = 0) { dataStreamService.appendToDataStreams(any(), any()) }
    }

    @Test
    fun `dead-letters data for closed data streams without retrying`() {
        coEvery { dataStreamService.appendToDataStreams(any(), any()) } throws IllegalStateException("closed")

        publish(GARDENER_PAYLOAD)

        assertDeadLettered(GARDENER_PAYLOAD)
        coVerify(exactly = 1) { dataStreamService.appendToDataStreams(any(), any()) }
    }

    @Test
    fun `retries other failures before dead-lettering`() {
        coEvery { dataStreamService.appendToDataStreams(any(), any()) } throws
            DataAccessResourceFailureException("database unavailable")

        publish(GARDENER_PAYLOAD)

        assertDeadLettered(GARDENER_PAYLOAD)
        coVerify(exactly = 1 + MAX_RETRIES.toInt()) { dataStreamService.appendToDataStreams(any(), any()) }
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

    /** What Spring Boot builds from `spring.rabbitmq.listener.simple.retry`, with short delays. */
    private fun bootLikeRetry() =
        RetryInterceptorBuilder.stateless()
            .retryPolicy(
                RetryPolicySettings()
                    .apply {
                        maxRetries = MAX_RETRIES
                        delay = Duration.ofMillis(10)
                    }
                    .also { ListenerRetryConfig().skipRetryOnRejectCustomizer().customize(it) }
                    .createRetryPolicy(),
            )
            .recoverer(RejectAndDontRequeueRecoverer())
            .build()

    /** Publishes like Gardener: to the direct exchange, routed by the queue name, without message properties. */
    private fun publish(body: String) = template.send(EXCHANGE, QUEUE, Message(body.toByteArray(), MessageProperties()))

    private fun assertDeadLettered(body: String) {
        val deadLetter = assertNotNull(template.receive(DLQ, RECEIVE_TIMEOUT_MS), "Expected a message in $DLQ.")
        assertEquals(body, deadLetter.body.decodeToString())
        assertNotNull(deadLetter.messageProperties.headers["x-death"], "Expected the broker to have dead-lettered it.")
    }
}
