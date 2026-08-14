package dk.cachet.carp.webservices.email.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.thymeleaf.templatemode.TemplateMode
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver

/**
 * The Configuration Class [EmailTemplateConfig].
 * The [EmailTemplateConfig] creates a creates [ClassLoaderTemplateResolver] instances for template resources.
 */
@Configuration
class EmailTemplateConfig {
    /**
     * The function [thymeleafTemplateResolver] configures the template resolver.
     * @return The [ClassLoaderTemplateResolver].
     */
    @Bean
    fun thymeleafTemplateResolver(): ClassLoaderTemplateResolver {
        val templateResolver = ClassLoaderTemplateResolver()
        templateResolver.prefix = "/templates/email/"
        templateResolver.suffix = ".html"
        templateResolver.templateMode = TemplateMode.HTML
        templateResolver.characterEncoding = Charsets.UTF_8.name()
        templateResolver.isCacheable = false

        return templateResolver
    }
}
