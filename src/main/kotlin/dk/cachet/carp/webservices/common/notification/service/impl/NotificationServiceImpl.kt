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
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.WebClient
import java.io.IOException

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
    }

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
    @Suppress("MagicNumber")
    override fun sendExceptionNotification(errorResponse: CarpErrorResponse) {
        val messageBuilder = StringBuilder()
        messageBuilder.append("- Exception Code: ${errorResponse.statusCode}$NEW_LINE")
        messageBuilder.append("- Exception: ${errorResponse.exception}$NEW_LINE")
        messageBuilder.append("- Message: ${errorResponse.message}$NEW_LINE")
        messageBuilder.append("- Path: ${errorResponse.path}$NEW_LINE")
        messageBuilder.append("- Environment: ${environment.profile}$NEW_LINE")

        if (environment.profile == EnvironmentProfile.PRODUCTION) {
            if (errorResponse.statusCode in 400..499) {
                processException(messageBuilder.toString(), teamsClientChannel)
            } else if (errorResponse.statusCode in 500..599) {
                processException(messageBuilder.toString(), teamsServerChannel)
            }
        } else if (environment.profile == EnvironmentProfile.DEVELOPMENT) {
            processException(messageBuilder.toString(), teamsDevChannel)
        }
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
