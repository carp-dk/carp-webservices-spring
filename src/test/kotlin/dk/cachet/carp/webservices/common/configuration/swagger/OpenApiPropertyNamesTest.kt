package dk.cachet.carp.webservices.common.configuration.swagger

import dk.cachet.carp.webservices.admin.dto.AdminStudyDto
import dk.cachet.carp.webservices.study.dto.ParticipantAccountSummaryDto
import dk.cachet.carp.webservices.study.dto.ParticipantAccountsRequestDto
import io.swagger.v3.core.converter.ModelConverters
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import kotlin.test.assertTrue

/**
 * swagger-core reads models with Jackson 2 and no Kotlin module, so a Kotlin `isX: Boolean` property is
 * documented as `x` while the app's Jackson 3 Kotlin mapper reads and writes it as `isX`. These DTOs pin the
 * documented name with `@get:Schema(name = ...)`; this guards that the spec keeps matching the JSON.
 */
class OpenApiPropertyNamesTest {
    companion object {
        @JvmStatic
        fun isPrefixedBooleans(): List<Arguments> =
            listOf(
                Arguments.of(AdminStudyDto::class.java, "isLive"),
                Arguments.of(ParticipantAccountSummaryDto::class.java, "isDeployed"),
                Arguments.of(ParticipantAccountsRequestDto::class.java, "isDeployed"),
            )
    }

    @ParameterizedTest
    @MethodSource("isPrefixedBooleans")
    fun `is-prefixed booleans keep their JSON name in the OpenAPI schema`(
        type: Class<*>,
        property: String,
    ) {
        val schema = ModelConverters.getInstance().read(type).getValue(type.simpleName)
        val names = schema.properties.keys

        assertTrue(property in names, "${type.simpleName} schema should document `$property`, but has $names")
        val stripped = property.removePrefix("is").replaceFirstChar { it.lowercase() }
        assertTrue(stripped !in names, "${type.simpleName} schema should not document `$stripped`")
    }
}
