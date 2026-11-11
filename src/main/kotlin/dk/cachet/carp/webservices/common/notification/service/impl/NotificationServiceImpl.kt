package dk.cachet.carp.webservices.common.notification.service.impl

import dk.cachet.carp.webservices.common.configuration.internationalisation.service.MessageBase
import dk.cachet.carp.webservices.common.environment.EnvironmentProfile
import dk.cachet.carp.webservices.common.environment.EnvironmentUtil
import dk.cachet.carp.webservices.common.exception.advices.CarpErrorResponse
import dk.cachet.carp.webservices.common.exception.responses.BadRequestException
import dk.cachet.carp.webservices.common.notification.domain.TeamsChannel
import dk.cachet.carp.webservices.common.notification.service.INotificationService
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.WebClient
import java.io.IOException
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * The Class [NotificationServiceImpl].
 * The [NotificationServiceImpl] enables exception notifications [CarpErrorResponse] in Teams channel.
 */
@Service
class NotificationServiceImpl(
    private val environment: EnvironmentUtil,
    private val validationMessages: MessageBase,
    @param:Value("\${teams.webhook.client}") private val teamsClientChannel: String,
    @param:Value("\${teams.webhook.server}") private val teamsServerChannel: String,
    @param:Value("\${teams.webhook.heartbeat}") private val teamsHeartbeatChannel: String,
    @param:Value("\${teams.webhook.dev}") private val teamsDevChannel: String,
) : INotificationService {
    companion object {
        private val LOGGER: Logger = LogManager.getLogger()
        private const val NEW_LINE = "\n\n"

        private const val THROTTLE_WINDOW_MINUTES = 10L
        private val THROTTLE_WINDOW: Duration = Duration.ofMinutes(THROTTLE_WINDOW_MINUTES)

        /** Keeps a digest readable; the remaining groups are summarised in one line. */
        private const val MAX_DIGEST_LINES = 20

        /** The digest message for [repeats] (error group to count), most frequent first. */
        internal fun formatDigest(
            repeats: Map<String, Int>,
            profile: EnvironmentProfile,
        ): String {
            val sorted = repeats.entries.sortedByDescending { it.value }
            val lines = sorted.take(MAX_DIGEST_LINES).map { (group, count) -> "- $count more: $group" }
            val rest = sorted.drop(MAX_DIGEST_LINES)
            val restLine =
                if (rest.isEmpty()) {
                    emptyList()
                } else {
                    listOf("- ${rest.sumOf { it.value }} more across ${rest.size} other error groups")
                }
            val header =
                "Repeated errors in the last $THROTTLE_WINDOW_MINUTES minutes (first occurrences already posted):"
            return (listOf(header) + lines + restLine + "Environment: $profile").joinToString(NEW_LINE)
        }
    }

    /** One throttle per Teams channel, so each channel gets its own digest. */
    private val throttles = ConcurrentHashMap<String, ExceptionNotificationThrottle>()

    /**
     * The [sendAlertOrGeneralNotification] function sends a notification message with the given message.
     * @param notification The [notification] containing the message to send.
     * @param channelToSendTo The value of the Teams channel the message needs to be sent to.
     */
    override fun sendAlertOrGeneralNotification(
        notification: String,
        channelToSendTo: TeamsChannel,
    ) {
        val messageBuilder = StringBuilder()

        messageBuilder.append(notification)
        messageBuilder.append(NEW_LINE)
        messageBuilder.append(NEW_LINE)
        messageBuilder.append("Environment: ${environment.profile}")
        when (channelToSendTo) {
            TeamsChannel.CLIENT_ERRORS -> processException(messageBuilder.toString(), teamsClientChannel)
            TeamsChannel.SERVER_ERRORS -> {
                // TODO: This check in not needed here, should be handled in email notification service
                if (environment.profile == EnvironmentProfile.PRODUCTION) {
                    processException(messageBuilder.toString(), teamsServerChannel)
                }
            }
            TeamsChannel.HEARTBEAT -> processException(messageBuilder.toString(), teamsHeartbeatChannel)
        }
    }

    /**
     * The [sendExceptionNotification] function sends a notification message with the given message.
     * @param errorResponse The errorResponse includes the error code, error message, and the error response.
     */
    override fun sendExceptionNotification(errorResponse: CarpErrorResponse) {
        val channel = exceptionChannelFor(errorResponse.statusCode)
        if (channel.isNullOrBlank()) return

        // Only the first occurrence of an error group per window is posted; repeats go into the digest.
        val throttle = throttles.computeIfAbsent(channel) { ExceptionNotificationThrottle(THROTTLE_WINDOW) }
        if (!throttle.tryAcquire(ExceptionNotificationThrottle.groupOf(errorResponse))) return

        val messageBuilder = StringBuilder()
        messageBuilder.append("- Exception Code: ${errorResponse.statusCode}$NEW_LINE")
        messageBuilder.append("- Exception: ${errorResponse.exception}$NEW_LINE")
        messageBuilder.append("- Message: ${errorResponse.message}$NEW_LINE")
        messageBuilder.append("- Path: ${errorResponse.path}$NEW_LINE")
        messageBuilder.append("- Environment: ${environment.profile}$NEW_LINE")
        processException(messageBuilder.toString(), channel)
    }

    /**
     * Posts one digest per channel listing the errors that repeated since the last digest (their first
     * occurrence was already posted by [sendExceptionNotification]).
     */
    @Scheduled(fixedRate = THROTTLE_WINDOW_MINUTES, timeUnit = TimeUnit.MINUTES)
    fun sendRepeatedExceptionDigests() {
        throttles.forEach { (channel, throttle) ->
            val repeats = throttle.drainRepeats()
            if (repeats.isNotEmpty()) {
                processException(formatDigest(repeats, environment.profile), channel)
            }
        }
    }

    /** The Teams channel exceptions with [statusCode] are posted to, or null when they aren't posted. */
    @Suppress("MagicNumber")
    private fun exceptionChannelFor(statusCode: Int): String? =
        when (environment.profile) {
            EnvironmentProfile.PRODUCTION ->
                when (statusCode) {
                    in 400..499 -> teamsClientChannel
                    in 500..599 -> teamsServerChannel
                    else -> null
                }
            EnvironmentProfile.DEVELOPMENT -> teamsDevChannel
            else -> null
        }

    /**
     * The [processException] function processes the exception and sends the message to the Teams channel.
     * @param message The message to send on Teams channel.
     * @throws IOException when the webhook cannot be reached.
     */
    private fun processException(
        message: String,
        channelToSend: String,
    ) {
        // A channel with no configured webhook URL is treated as disabled: skip silently rather than
        // attempting a POST to an empty URL (which would fail on every notification).
        if (channelToSend.isBlank()) {
            LOGGER.debug("Skipping Teams notification: no webhook URL configured for this channel.")
            return
        }

        // Microsoft retired the legacy Office 365 Connector webhooks (MessageCard payload) in favour of
        // Power Automate "Workflows" incoming webhooks, which expect an Adaptive Card wrapped in a
        // { "type": "message", "attachments": [...] } envelope.
        val payload =
            mapOf(
                "type" to "message",
                "attachments" to
                    listOf(
                        mapOf(
                            "contentType" to "application/vnd.microsoft.card.adaptive",
                            "content" to
                                mapOf(
                                    "\$schema" to "http://adaptivecards.io/schemas/adaptive-card.json",
                                    "type" to "AdaptiveCard",
                                    "version" to "1.5",
                                    "body" to
                                        listOf(
                                            mapOf(
                                                "type" to "TextBlock",
                                                "text" to "\ud83c\udf81 CARP-Webservices",
                                                "weight" to "Bolder",
                                                "size" to "Large",
                                                "wrap" to true,
                                            ),
                                            mapOf(
                                                "type" to "TextBlock",
                                                "text" to message,
                                                "wrap" to true,
                                            ),
                                        ),
                                ),
                        ),
                    ),
            )

        try {
            val webClient = WebClient.create()
            webClient.post()
                .uri(channelToSend)
                .body(BodyInserters.fromValue(payload))
                .retrieve()
                .bodyToMono(String::class.java)
                .subscribe(
                    { response -> LOGGER.info("Teams response -> {}", response) },
                    { error -> LOGGER.error("Error sending message to Teams: ${error.message}") },
                )
        } catch (ex: IOException) {
            LOGGER.error("Unexpected Error! Webhook: $ex")
            throw BadRequestException(validationMessages.get("notification.teams.exception", ex.message.toString()), ex)
        }
    }
}
