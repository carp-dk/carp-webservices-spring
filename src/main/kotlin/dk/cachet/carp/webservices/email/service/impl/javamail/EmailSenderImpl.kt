package dk.cachet.carp.webservices.email.service.impl.javamail

import dk.cachet.carp.webservices.common.exception.email.EmailException
import dk.cachet.carp.webservices.common.notification.domain.TeamsChannel
import dk.cachet.carp.webservices.common.notification.service.INotificationService
import dk.cachet.carp.webservices.email.domain.EmailSendResult
import jakarta.mail.MessagingException
import jakarta.mail.internet.MimeMessage
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.core.env.Environment
import org.springframework.mail.MailSendException
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mail.javamail.MimeMessageHelper
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import java.nio.charset.StandardCharsets
import java.util.concurrent.CompletableFuture

/**
 * The Class [EmailSenderImpl].
 * The [EmailSenderImpl] implements the functionality logic for email sending.
 */
@Service
class EmailSenderImpl(
    @Qualifier("mailConfig")
    private val mailSender: JavaMailSender,
    private val environment: Environment,
    private val notificationService: INotificationService,
) {
    companion object {
        private val LOGGER: Logger = LogManager.getLogger()
        private const val DEFAULT_SUBJECT = "Welcome to the CARP Research Platform"
    }

    /**
     * The function [invoke] sends a new email with a QR code.
     *
     * @param destinationEmail The [destinationEmail] of the recipient.
     * @param subject The [subject] of the study.
     * @param content The [content] of the email body.
     * @param cc The [cc] list of email addresses to be copied.
     * @return The status code of the response.
     */
    @Async
    fun invoke(
        destinationEmail: String,
        subject: String,
        content: String,
        cc: List<String>,
    ): CompletableFuture<Int> {
        val response = send(destinationEmail, subject, content, cc)
        return CompletableFuture.completedFuture(response)
    }

    /**
     * The function [send] builds a [MimeMessage] enabling sending an email.
     *
     * @param recipientEmailAddress The [recipientEmailAddress] email address of the recipient.
     * @param studyNameAsSubject The [studyNameAsSubject] subject of the email.
     * @param mailContent The [mailContent] of the email.
     * @param cc The [cc] list of email addresses to be copied.
     * @return The status code of the response.
     */
    @Throws(EmailException::class)
    @Suppress("TooGenericExceptionCaught")
    fun send(
        recipientEmailAddress: String,
        studyNameAsSubject: String,
        mailContent: String,
        cc: List<String>,
    ): Int {
        try {
            val mimeMessage: MimeMessage = mailSender.createMimeMessage()
            mimeMessage.setContent(mailContent, "text/html; charset=utf-8")

            val mimeMessageHelper =
                MimeMessageHelper(
                    mimeMessage,
                    MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED,
                    StandardCharsets.UTF_8.name(),
                )
            mimeMessageHelper.setTo(recipientEmailAddress)
            mimeMessageHelper.setText(mailContent, true)
            mimeMessageHelper.setSubject(ifNullOrEmpty(studyNameAsSubject))
            mimeMessageHelper.setFrom(environment.getProperty("spring.mail.from")!!)
            mimeMessageHelper.setCc(cc.toTypedArray())

            this.mailSender.send(mimeMessage)
        } catch (ex: MailSendException) {
            LOGGER.warn("Email message not sent.", ex)
            notificationService.sendAlertOrGeneralNotification(
                "Email message not sent. Exception: $ex.",
                TeamsChannel.SERVER_ERRORS,
            )
            return EmailSendResult.FAILURE.status
        } catch (ex: MessagingException) {
            LOGGER.warn("Email message not sent.", ex)
            notificationService.sendAlertOrGeneralNotification(
                "Email message not sent. Exception: $ex.",
                TeamsChannel.SERVER_ERRORS,
            )
            return EmailSendResult.FAILURE.status
        } catch (ex: NullPointerException) {
            LOGGER.warn("No invitation email was found.", ex)
            notificationService.sendAlertOrGeneralNotification(
                "No invitation email was found. Exception: $ex.",
                TeamsChannel.SERVER_ERRORS,
            )
            return EmailSendResult.FAILURE.status
        } catch (ex: Exception) {
            LOGGER.warn("Failed to send mail, mailAddress= $recipientEmailAddress", ex)
            notificationService.sendAlertOrGeneralNotification(
                "Failed to send mail, mailAddress= $recipientEmailAddress. Exception: $ex.",
                TeamsChannel.SERVER_ERRORS,
            )
            return EmailSendResult.FAILURE.status
        }
        return EmailSendResult.SUCCESS.status
    }

    private fun ifNullOrEmpty(value: String): String {
        return if (value.isEmpty() or value.isBlank()) DEFAULT_SUBJECT else value
    }
}
