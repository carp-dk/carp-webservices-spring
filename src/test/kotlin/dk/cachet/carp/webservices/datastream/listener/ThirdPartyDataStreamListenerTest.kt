package dk.cachet.carp.webservices.datastream.listener

import dk.cachet.carp.common.application.UUID
import dk.cachet.carp.common.infrastructure.serialization.CustomData
import dk.cachet.carp.data.application.DataStreamBatch
import dk.cachet.carp.webservices.common.queue.ListenerRetryConfig
import dk.cachet.carp.webservices.datastream.service.core.CoreDataStreamService
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.amqp.AmqpRejectAndDontRequeueException
import org.springframework.amqp.core.Message
import org.springframework.amqp.core.MessageProperties
import org.springframework.boot.retry.RetryPolicySettings
import org.springframework.dao.DataAccessResourceFailureException
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ThirdPartyDataStreamListenerTest {
    companion object {
        val DEPLOYMENT_ID = UUID("a866a1f2-953e-45bb-80ab-18fb1a380bb9")

        /**
         * A daily-activity append as CARP Gardener publishes it: `AppendToDataStreams` encoded with
         * `createDefaultJSON(COMMON_SERIAL_MODULE)`, device role "Wearables", the Withings response as custom data.
         */
        val GARDENER_PAYLOAD =
            """
            {"apiVersion":"1.1","studyDeploymentId":"a866a1f2-953e-45bb-80ab-18fb1a380bb9","batch":[{"dataStream":
            {"studyDeploymentId":"a866a1f2-953e-45bb-80ab-18fb1a380bb9","deviceRoleName":"Wearables",
            "dataType":"com.withings.daily_activity"},"firstSequenceId":0,"measurements":[{"sensorStartTime":
            1635416130000000,"data":{"__type":"com.withings.daily_activity","body":{"activities":[{"date":"2021-10-28",
            "steps":1962,"timezone":"Europe/Copenhagen"}]},"status":0}}],"triggerIds":[0]}]}
            """.trimIndent()

        fun message(body: String) = Message(body.toByteArray(), MessageProperties())
    }

    private val dataStreamService: CoreDataStreamService = mockk()
    private val listener = ThirdPartyDataStreamListener(dataStreamService)

    @Nested
    inner class Receive {
        @Test
        fun `appends a Gardener payload to the deployment's data streams`() {
            val batch = slot<DataStreamBatch>()
            coEvery { dataStreamService.appendToDataStreams(DEPLOYMENT_ID, capture(batch)) } returns Unit

            listener.receive(message(GARDENER_PAYLOAD))

            val sequence = batch.captured.sequences.single()
            assertEquals(DEPLOYMENT_ID, sequence.dataStream.studyDeploymentId)
            assertEquals("Wearables", sequence.dataStream.deviceRoleName)
            assertEquals("com.withings.daily_activity", sequence.dataStream.dataType.toString())
            assertEquals(0L, sequence.firstSequenceId)
            assertEquals(listOf(0), sequence.triggerIds)
            val measurement = sequence.measurements.single()
            assertEquals(1635416130000000L, measurement.sensorStartTime)
            assertTrue(assertIs<CustomData>(measurement.data).jsonSource.contains("\"steps\":1962"))
        }

        @Test
        fun `rejects malformed JSON without calling the service`() {
            assertThrows<AmqpRejectAndDontRequeueException> { listener.receive(message("{not json")) }

            coVerify(exactly = 0) { dataStreamService.appendToDataStreams(any(), any()) }
        }

        @Test
        fun `rejects a message that is not an AppendToDataStreams request`() {
            assertThrows<AmqpRejectAndDontRequeueException> {
                listener.receive(message("""{"apiVersion":"1.1","studyDeploymentId":"not-a-uuid"}"""))
            }

            coVerify(exactly = 0) { dataStreamService.appendToDataStreams(any(), any()) }
        }

        @Test
        fun `rejects data for an unknown deployment or unconfigured data stream`() {
            coEvery { dataStreamService.appendToDataStreams(any(), any()) } throws
                IllegalArgumentException("No configuration was found for studyDeploymentId $DEPLOYMENT_ID.")

            assertThrows<AmqpRejectAndDontRequeueException> { listener.receive(message(GARDENER_PAYLOAD)) }
        }

        @Test
        fun `rejects data for closed data streams`() {
            coEvery { dataStreamService.appendToDataStreams(any(), any()) } throws
                IllegalStateException("Data streams for study deployment with ID $DEPLOYMENT_ID have been closed.")

            assertThrows<AmqpRejectAndDontRequeueException> { listener.receive(message(GARDENER_PAYLOAD)) }
        }

        @Test
        fun `lets other failures through so they are retried`() {
            coEvery { dataStreamService.appendToDataStreams(any(), any()) } throws
                DataAccessResourceFailureException("database unavailable")

            assertThrows<DataAccessResourceFailureException> { listener.receive(message(GARDENER_PAYLOAD)) }
        }
    }

    @Nested
    inner class RetryPolicy {
        private val policy =
            RetryPolicySettings()
                .also { ListenerRetryConfig().skipRetryOnRejectCustomizer().customize(it) }
                .createRetryPolicy()

        // The container hands the retry policy the listener's exception wrapped, so match through the cause chain.
        private fun failed(cause: Throwable) = RuntimeException("listener threw", cause)

        @Test
        fun `does not retry a rejected message`() {
            assertFalse(policy.shouldRetry(failed(AmqpRejectAndDontRequeueException("bad message"))))
        }

        @Test
        fun `still retries other failures`() {
            assertTrue(policy.shouldRetry(failed(DataAccessResourceFailureException("database unavailable"))))
        }
    }
}
