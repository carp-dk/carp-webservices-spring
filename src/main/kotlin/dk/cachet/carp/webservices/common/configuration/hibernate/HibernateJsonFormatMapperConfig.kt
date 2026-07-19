package dk.cachet.carp.webservices.common.configuration.hibernate

import org.hibernate.cfg.MappingSettings
import org.hibernate.type.descriptor.WrapperOptions
import org.hibernate.type.descriptor.java.JavaType
import org.hibernate.type.format.FormatMapper
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import tools.jackson.databind.ObjectMapper

/**
 * Hibernate 7.2's built-in `JacksonJsonFormatMapper` only supports Jackson 2
 * (`com.fasterxml.jackson.databind`), which Hibernate's internal JSON handling falls back to
 * whenever it needs to (de)serialize a `JsonNode` column itself, e.g. the deep-copy `merge()`
 * does for dirty-checking on detached entities. Since the app has fully migrated to Jackson 3
 * (`tools.jackson.databind`), that fallback can't construct our `JsonNode` type and throws.
 *
 * Native Jackson 3 `FormatMapper` support only landed in Hibernate 7.3, which Spring Boot 4.0.7's
 * dependency management doesn't manage yet. This customizer is a temporary shim that points
 * Hibernate's internal JSON format mapper at our own Jackson 3 [ObjectMapper] instead. Remove it
 * once Spring Boot manages Hibernate 7.3+ and that combination has proven stable.
 */
@Configuration
class HibernateJsonFormatMapperConfig {
    @Bean
    fun hibernateJacksonFormatMapperCustomizer(objectMapper: ObjectMapper): HibernatePropertiesCustomizer =
        HibernatePropertiesCustomizer { properties ->
            properties[MappingSettings.JSON_FORMAT_MAPPER] = HibernateJacksonFormatMapper(objectMapper)
        }
}

private class HibernateJacksonFormatMapper(private val objectMapper: ObjectMapper) : FormatMapper {
    // Hibernate routes any jsonb-sourced column through this mapper, including native-query
    // projections whose target Java type is plain String (e.g. a column selected purely to carry
    // raw JSON text through, with no need to materialize it into an object). For those, the
    // "conversion" is the text itself - running it through Jackson would instead try to parse it
    // as a quoted string literal and fail on the very first '{'.
    @Suppress("UNCHECKED_CAST")
    override fun <T> fromString(
        charSequence: CharSequence,
        javaType: JavaType<T>,
        wrapperOptions: WrapperOptions,
    ): T =
        if (javaType.javaTypeClass == String::class.java) {
            charSequence.toString() as T
        } else {
            // Use the full reflected type, not the erased class, so parameterized jsonb columns
            // (e.g. List<Foo>, Map<String, Foo>) deserialize with their element types instead of
            // collapsing to List<LinkedHashMap>. Mirrors Hibernate's own JacksonJsonFormatMapper.
            objectMapper.readValue(charSequence.toString(), objectMapper.constructType(javaType.javaType))
        }

    override fun <T> toString(
        value: T,
        javaType: JavaType<T>,
        wrapperOptions: WrapperOptions,
    ): String = if (value is String) value else objectMapper.writeValueAsString(value)
}
