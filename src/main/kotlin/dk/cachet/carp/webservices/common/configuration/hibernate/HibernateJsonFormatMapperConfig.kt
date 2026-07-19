package dk.cachet.carp.webservices.common.configuration.hibernate

import org.hibernate.cfg.MappingSettings
import org.hibernate.type.format.jackson.Jackson3JsonFormatMapper
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import tools.jackson.databind.json.JsonMapper

/**
 * Both Jackson 2 and Jackson 3 are on the classpath (Spring Boot still pulls Jackson 2 for e.g.
 * TOML/YAML config parsing), and Hibernate defaults to Jackson 2 when both are present. Our jsonb
 * columns are Jackson 3 (`tools.jackson`) `JsonNode`, so point Hibernate's JSON format mapper at
 * the app's Jackson 3 [JsonMapper] explicitly, using Hibernate's built-in [Jackson3JsonFormatMapper]
 * (available since Hibernate 7.3). Reusing the app mapper keeps Hibernate's internal JSON handling
 * (e.g. the deep-copy merge() does for dirty-checking) consistent with the rest of the app.
 */
@Configuration
class HibernateJsonFormatMapperConfig {
    @Bean
    fun hibernateJacksonFormatMapperCustomizer(jsonMapper: JsonMapper): HibernatePropertiesCustomizer =
        HibernatePropertiesCustomizer { properties ->
            properties[MappingSettings.JSON_FORMAT_MAPPER] = Jackson3JsonFormatMapper(jsonMapper)
        }
}
