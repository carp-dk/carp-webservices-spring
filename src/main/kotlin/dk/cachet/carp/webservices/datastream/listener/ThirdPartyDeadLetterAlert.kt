package dk.cachet.carp.webservices.datastream.listener

import dk.cachet.carp.data.infrastructure.DataStreamServiceRequest
import dk.cachet.carp.webservices.common.exception.advices.CarpErrorResponse
import dk.cachet.carp.webservices.common.exception.advices.ErrorContext
import dk.cachet.carp.webservices.common.input.WS_JSON
import dk.cachet.carp.webservices.common.notification.service.INotificationService
import org.aopalliance.intercept.MethodInterceptor
import org.aopalliance.intercept.MethodInvocation
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.springframework.amqp.AmqpRejectAndDontRequeueException
import org.springframework.amqp.core.Message
import org.springframework.amqp.listener.ListenerExecutionFailedException
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * Reports third-party messages that are dead-lettered, as a server error in Teams and in the log.
 *
 * It wraps the listener's retry advice (see [ThirdPartyListenerConfig]), so it sees each delivery's final outcome
 * once: retries have already happened, and every failure that reaches it is rejected to the dead-letter queue.
 * The notification service throttles repeats, so a misconfigured study posts once plus a periodic digest.
 */
@Component
class ThirdPartyDeadLetterAlert(
    private val notificationService: INotificationService,
) : MethodInterceptor {
    companion object {
        private val LOGGER: Logger = LogManager.getLogger()
        private const val REF_LENGTH = 8
        private const val SERVER_ERROR = 500
    }

    // Any failure that leaves the retries dead-letters the message, so all are reported; it is always rethrown.
    @Suppress("TooGenericExceptionCaught")
    override fun invoke(invocation: MethodInvocation): Any? =
        try {
            invocation.proceed()
        } catch (ex: Throwable) {
            report(invocation.arguments.filterIsInstance<Message>().firstOrNull(), ex)
            throw ex
        }

    private fun report(
        message: Message?,
        failure: Throwable,
    ) {
        val cause = underlyingCause(failure)
        val queue = message?.messageProperties?.consumerQueue ?: "third-party queue"
        val request =
            message?.let {
                runCatching {
                    WS_JSON.decodeFromString(
                        DataStreamServiceRequest.AppendToDataStreams.serializer(),
                        it.body.decodeToString(),
                    )
                }.getOrNull()
            }
        val context =
            ErrorContext(
                ref = UUID.randomUUID().toString().take(REF_LENGTH),
                requestType = "AppendToDataStreams",
                ids =
                    listOfNotNull(
                        request?.let { "deploymentId" to it.studyDeploymentId.stringRepresentation },
                        request?.let { r ->
                            "dataStreams" to
                                r.batch.sequences
                                    .map { "${it.dataStream.deviceRoleName}/${it.dataStream.dataType}" }
                                    .distinct()
                                    .joinToString(", ")
                        },
                    ).toMap(),
            )

        LOGGER.error("Third-party message from $queue dead-lettered: ${cause.message} ($context)", cause)
        try {
            notificationService.sendExceptionNotification(
                CarpErrorResponse(
                    statusCode = SERVER_ERROR,
                    exception = cause.javaClass.name,
                    message = "Message dead-lettered: ${cause.message}",
                    path = "AMQP $queue",
                    ref = context.ref,
                ),
                context,
            )
        } catch (
            @Suppress("TooGenericExceptionCaught") ex: RuntimeException,
        ) {
            // The message must still be dead-lettered when Teams can't be reached.
            LOGGER.error("Could not send the dead-letter notification (ref ${context.ref}): ${ex.message}")
        }
    }

    /** The exception that made the listener fail, unwrapped from the container's and listener's rejections. */
    private fun underlyingCause(failure: Throwable): Throwable =
        generateSequence(failure) { it.cause }
            .firstOrNull { it !is ListenerExecutionFailedException && it !is AmqpRejectAndDontRequeueException }
            ?: failure
}
