package dk.cachet.carp.webservices.email.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.*
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mail.javamail.JavaMailSenderImpl

/**
 * The Configuration Class [EmailConfig].
 * The [EmailConfig] implements the [JavaMailSender] interface to enable connecting ti [SMTP] host server.
 */
@Configuration
@ComponentScan(basePackages = ["dk.cachet.carp.webservices"])
@PropertySources(PropertySource(value = ["classpath:config/application.yml"]))
class EmailConfig(
    @Value("\${spring.mail.host}") private val host: String,
    @Value("\${spring.mail.address}") private val emailAddress: String,
    @Value("\${spring.mail.password}") private val password: String,
    @Value("\${spring.mail.port}") private val port: Int,
) {
    companion object {
        const val MAIL_SMTP_AUTH = "true"
        const val MAIL_TRANSPORT_PROTOCOL = "smtp"
        const val MAIL_SMTP_STARTTLS_ENABLE = "true"
    }

    /**
     * The function [mailConfig] sets a configuration for connecting to [SMTP] server.
     * @return A new instance of [JavaMailSender].
     */
    @Bean
    fun mailConfig(): JavaMailSender {
        val mailSender = JavaMailSenderImpl()
        mailSender.host = host
        mailSender.port = port
        mailSender.username = emailAddress
        mailSender.password = password

        val props = mailSender.javaMailProperties
        props["mail.smtp.auth"] = MAIL_SMTP_AUTH
        props["mail.transport.protocol"] = MAIL_TRANSPORT_PROTOCOL
        props["mail.smtp.starttls.enable"] = MAIL_SMTP_STARTTLS_ENABLE

        return mailSender
    }
}
