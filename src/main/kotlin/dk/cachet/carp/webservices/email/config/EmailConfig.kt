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
    @Value("\${spring.mail.port}") private val port: Int,
    @Value("\${spring.mail.username:}") private val username: String?,
    @Value("\${spring.mail.password:}") private val password: String?,
    @Value("\${spring.mail.properties.mail.smtp.auth:false}") private val smtpAuth: Boolean,
    @Value("\${spring.mail.properties.mail.smtp.starttls.enable:false}") private val startTls: Boolean,
    @Value("\${spring.mail.properties.mail.transport.protocol:smtp}") private val protocol: String,
) {

    @Bean
    fun mailConfig(): JavaMailSender {
        val mailSender = JavaMailSenderImpl()
        mailSender.host = host
        mailSender.port = port

        if (!username.isNullOrBlank()) {
            mailSender.username = username
            mailSender.password = password
        }

        val props = mailSender.javaMailProperties
        props["mail.smtp.auth"] = smtpAuth
        props["mail.smtp.starttls.enable"] = startTls
        props["mail.transport.protocol"] = protocol

        return mailSender
    }
}
