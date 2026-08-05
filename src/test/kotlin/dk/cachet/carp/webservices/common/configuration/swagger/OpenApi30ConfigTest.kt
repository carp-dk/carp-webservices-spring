package dk.cachet.carp.webservices.common.configuration.swagger

import io.mockk.mockk
import io.swagger.v3.core.util.Json
import org.junit.jupiter.api.Test
import org.springframework.core.io.ClassPathResource
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class OpenApi30ConfigTest {
    private val sut =
        OpenApi30Config(
            moduleName = "carp",
            apiVersion = "1.0",
            docResource = ClassPathResource("openapi/description.txt"),
            environmentUtil = mockk(relaxed = true),
        )

    /**
     * Guards the Jackson-2-vs-3 pitfall: the hand-written openapi doc files must be read with
     * swagger's own mapper so schema references survive. Read with the app's Jackson 3 ObjectMapper,
     * every ref collapses to an empty schema ("any") in Swagger UI. See OpenApi30Config.swaggerMapper.
     */
    @Test
    fun `loadOperationsDocumentation preserves ref-based schemas for queryDataStreamByTime`() {
        val operations = sut.loadOperationsDocumentation()

        val operation = operations["dataStream/queryDataStreamByTime.json"]
        assertNotNull(operation, "queryDataStreamByTime.json should be loaded")

        // Re-serialize with swagger's mapper and assert the $ref references survived deserialization
        // ($ref is not addressable as a Kotlin property, so we inspect the rendered JSON).
        val rendered = Json.mapper().writeValueAsString(operation)
        assertTrue(
            rendered.contains("#/components/schemas/DataStreamId"),
            "request body should reference DataStreamId, but was: $rendered",
        )
        assertTrue(
            rendered.contains("#/components/schemas/DataStreamBatch"),
            "200 response should reference DataStreamBatch, but was: $rendered",
        )
    }
}
