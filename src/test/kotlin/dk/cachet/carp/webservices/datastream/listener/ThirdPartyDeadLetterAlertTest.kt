package dk.cachet.carp.webservices.datastream.listener

import dk.cachet.carp.webservices.common.exception.advices.CarpErrorResponse
import dk.cachet.carp.webservices.common.exception.advices.ErrorContext
import dk.cachet.carp.webservices.common.notification.service.INotificationService
import dk.cachet.carp.webservices.datastream.listener.ThirdPartyDataStreamListenerTest.Companion.DEPLOYMENT_ID
import dk.cachet.carp.webservices.datastream.listener.ThirdPartyDataStreamListenerTest.Companion.GARDENER_PAYLOAD
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.aopalliance.intercept.MethodInvocation
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.amqp.AmqpRejectAndDontRequeueException
import org.springframework.amqp.core.Message
import org.springframework.amqp.core.MessageProperties
import org.springframework.amqp.listener.ListenerExecutionFailedException
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ThirdPartyDeadLetterAlertTest {
    private val notificationService: INotificationService = mockk(relaxed = true)
    private val alert = ThirdPartyDeadLetterAlert(notificationService)

    private fun message(body: String) =
        Message(body.toByteArray(), MessageProperties().apply { consumerQueue = "third-party-queue-test" })

    /** The container's invocation of the listener for [message], failing with [failure] when given. */
    private fun invocation(
        message: Message,
        failure: Throwable? = null,
    ): MethodInvocation =
        mockk {
            every { arguments } returns arrayOf(mockk<com.rabbitmq.client.Channel>(), message)
            if (failure == null) every { proceed() } returns null else every { proceed() } throws failure
        }

    /** What a rejected message looks like by the time it leaves the retry advice. */
    private fun rejected(cause: Exception) =
        ListenerExecutionFailedException("Retry Policy Exhausted", AmqpRejectAndDontRequeueException("rejected", cause))

    @Test
    fun `does not report a message that was stored`() {
        alert.invoke(invocation(message(GARDENER_PAYLOAD)))

        verify(exactly = 0) { notificationService.sendExceptionNotification(any(), any()) }
    }

    @Test
    fun `reports the underlying failure with the deployment and data streams, and rethrows`() {
        val failure = rejected(IllegalArgumentException("The batch contains a sequence with a data stream ..."))
        val response = slot<CarpErrorResponse>()
        val context = slot<ErrorContext>()
        every { notificationService.sendExceptionNotification(capture(response), capture(context)) } returns Unit

        val thrown = assertThrows<ListenerExecutionFailedException> {
            alert.invoke(invocation(message(GARDENER_PAYLOAD), failure))
        }

        assertSame(failure, thrown)
        assertEquals(500, response.captured.statusCode)
        assertEquals(IllegalArgumentException::class.java.name, response.captured.exception)
        assertTrue(response.captured.message.contains("The batch contains a sequence"))
        assertEquals("AMQP third-party-queue-test", response.captured.path)
        assertEquals(response.captured.ref, context.captured.ref)
        assertEquals(
            mapOf(
                "deploymentId" to DEPLOYMENT_ID.stringRepresentation,
                "dataStreams" to "Wearables/com.withings.daily_activity",
            ),
            context.captured.ids,
        )
    }

    @Test
    fun `reports a malformed message without ids`() {
        val context = slot<ErrorContext>()
        every { notificationService.sendExceptionNotification(any(), capture(context)) } returns Unit

        assertThrows<ListenerExecutionFailedException> {
            alert.invoke(invocation(message("{not json"), rejected(IllegalArgumentException("bad json"))))
        }

        assertEquals(emptyMap(), context.captured.ids)
    }

    @Test
    fun `still rethrows when the notification fails`() {
        val failure = rejected(IllegalStateException("closed"))
        every { notificationService.sendExceptionNotification(any(), any()) } throws RuntimeException("Teams is down")

        val thrown = assertThrows<ListenerExecutionFailedException> {
            alert.invoke(invocation(message(GARDENER_PAYLOAD), failure))
        }

        assertSame(failure, thrown)
    }
}
