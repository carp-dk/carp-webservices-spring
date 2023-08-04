package dk.cachet.carp.webservices.common.configuration.multipart

import jakarta.servlet.MultipartConfigElement
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.web.servlet.MultipartConfigFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.util.unit.DataSize

/**
 * The Configuration Class [MultipartConfig].
 * The [MultipartConfig] implements the [MultipartConfigElement] to limit the file size of the request body.
 */
@Configuration
class MultipartConfig
(
    @Value("\${spring.servlet.multipart.max-file-size}") private val maxFileSize: DataSize,
    @Value("\${spring.servlet.multipart.max-request-size}") private val maxRequestSize: DataSize
)
{
    /**
     * The function [multipartConfigElement] set the max file size of the request body.
     * @return The [MultipartConfigFactory] created multipart configuration element.
     */
    @Bean
    fun multipartConfigElement(): MultipartConfigElement
    {
        val factory = MultipartConfigFactory()
        factory.setMaxFileSize(maxFileSize)
        factory.setMaxRequestSize(maxRequestSize)

        return factory.createMultipartConfig()
    }
}