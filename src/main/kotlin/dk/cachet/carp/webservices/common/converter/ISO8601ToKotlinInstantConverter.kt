package dk.cachet.carp.webservices.common.converter

import org.springframework.core.convert.converter.Converter
import kotlin.time.Instant

class ISO8601ToKotlinInstantConverter : Converter<String, Instant> {
    override fun convert(source: String): Instant {
        return Instant.parse(source)
    }
}
