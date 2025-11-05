package dk.cachet.carp.webservices.common.configuration.properties

import org.springframework.context.annotation.*
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer

@Configuration
@ComponentScan(basePackages = ["dk.cachet.carp.webservices"])
@PropertySources(PropertySource(value = ["classpath:config/application.yml"]))
class PropertiesConfig {
    @Bean
    fun propertySourcesPlaceholderConfigurer(): PropertySourcesPlaceholderConfigurer? {
        return PropertySourcesPlaceholderConfigurer()
    }
}
