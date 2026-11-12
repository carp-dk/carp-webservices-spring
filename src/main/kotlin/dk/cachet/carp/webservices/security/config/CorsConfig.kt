package dk.cachet.carp.webservices.security.config

import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.Ordered
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.UrlBasedCorsConfigurationSource
import org.springframework.web.filter.CorsFilter

@Configuration
class CorsConfig {
    companion object {
        /**
         * Any origin may call the API, but never with credentials. Clients authenticate with a bearer token in the
         * `Authorization` header (the portal through @carp-dk/client, the mobile apps), not with cookies; allowing
         * credentials together with any origin would let any website make cookie-authenticated calls.
         */
        fun corsConfiguration(): CorsConfiguration =
            CorsConfiguration().apply {
                allowCredentials = false
                addAllowedOriginPattern("*")
                addAllowedHeader("*")
                addAllowedMethod("*")
            }
    }

    /** Answers CORS preflight requests and adds CORS headers, ahead of every other filter. */
    @Bean
    fun corsFilter(): FilterRegistrationBean<CorsFilter> {
        val source = UrlBasedCorsConfigurationSource()
        source.registerCorsConfiguration("/**", corsConfiguration())

        return FilterRegistrationBean(CorsFilter(source)).apply { order = Ordered.HIGHEST_PRECEDENCE }
    }
}
