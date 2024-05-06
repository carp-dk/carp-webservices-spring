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
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.BodyInserters
import java.io.IOException
/**
 * The Class [NotificationServiceImpl].
 * The [NotificationServiceImpl] enables exception notifications [CarpErrorResponse] in Slack channel.
 */
@Service
class NotificationServiceImpl
(
    private val environment: EnvironmentUtil,
    private val validationMessages: MessageBase,
    @Value("\${teams.webhook.client}") private val teamsClientChannel: String,
    @Value("\${teams.webhook.server}") private val teamsServerChannel: String,
    @Value("\${teams.webhook.heartbeat}") private val teamsHeartbeatChannel: String,
    @Value("\${teams.webhook.dev}") private val teamsDevChannel: String
): INotificationService
{
    companion object
    {
        private val LOGGER: Logger = LogManager.getLogger()
        private const val NEW_LINE = "\n\n"
    }

    /**
     * The [sendAlertOrNotification] function sends a notification message with the given message.
     * @param notification The [notification] containing the message to send.
     * @param channelToSendTo The value of the Slack channel the message needs to be sent to.
     */
    override fun sendAlertOrNotification(notification: String, channelToSendTo: TeamsChannel)
    {
        val messageBuilder = StringBuilder()

        messageBuilder.append(notification)
        messageBuilder.append(NEW_LINE)
        messageBuilder.append(NEW_LINE)
        messageBuilder.append("Environment: ${environment.profile}")
        when (channelToSendTo)
        {
            TeamsChannel.CLIENT_ERRORS -> processException(messageBuilder.toString(), teamsClientChannel)
            TeamsChannel.SERVER_ERRORS -> processException(messageBuilder.toString(), teamsServerChannel)
            TeamsChannel.HEARTBEAT -> processException(messageBuilder.toString(), teamsHeartbeatChannel)
        }
    }

    /**
     * The [sendExceptionNotification] function sends a notification message with the given message.
     * @param errorResponse The errorResponse includes the error code, error message, and the error response.
     */
    override fun sendExceptionNotification(errorResponse: CarpErrorResponse)
    {
        val messageBuilder = StringBuilder()
        messageBuilder.append("- Exception Code: ${errorResponse.statusCode}$NEW_LINE")
        messageBuilder.append("- Exception: ${errorResponse.exception}$NEW_LINE")
        messageBuilder.append("- Message: ${errorResponse.message}$NEW_LINE")
        messageBuilder.append("- Path: ${errorResponse.path}$NEW_LINE")
        messageBuilder.append("- Environment: ${environment.profile}$NEW_LINE")

        if(environment.profile == EnvironmentProfile.PRODUCTION)
        {
            if (errorResponse.statusCode in 400..499)
            {
                processException(messageBuilder.toString(), teamsClientChannel)
            }
            else if (errorResponse.statusCode in 500..599)
            {
                processException(messageBuilder.toString(), teamsServerChannel)
            }
        } else if (environment.profile == EnvironmentProfile.DEVELOPMENT)
        {
            processException(messageBuilder.toString(), teamsDevChannel)
        }
    }

    /**
     * The [processException] function processes the exception and sends the message to the Slack channel.
     * @param message The message to send on Slack channel.
     * @throws IOException when the webhook cannot be reached.
     */
    private fun processException(message: String, channelToSend: String) {
        val payload = mapOf(
            "text" to message,
            "title" to "\ud83c\udf81 CARP-Webservices",
            "themeColor" to "8f4742")

        try {
            val webClient = WebClient.create()
            webClient.post()
                .uri(channelToSend)
                .body(BodyInserters.fromValue(payload))
                .retrieve()
                .bodyToMono(String::class.java)
                .subscribe(
                    { response -> LOGGER.info("Teams response -> {}", response) },
                    { error -> LOGGER.error("Error sending message to Teams: ${error.message}") }
                )
        } catch (ex: IOException) {
            LOGGER.error("Unexpected Error! WebHook: $ex")
            throw BadRequestException(validationMessages.get("notification.teams.exception", ex.message.toString()))
        }
    }
}