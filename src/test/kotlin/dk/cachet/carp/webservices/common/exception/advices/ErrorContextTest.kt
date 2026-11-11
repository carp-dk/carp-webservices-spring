package dk.cachet.carp.webservices.common.exception.advices

import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.web.context.request.ServletWebRequest
import org.springframework.web.servlet.HandlerMapping
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class ErrorContextTest {
    private fun request(configure: MockHttpServletRequest.() -> Unit = {}) =
        ServletWebRequest(MockHttpServletRequest("POST", "/api/deployment-service").apply(configure))

    @Test
    fun `reads the user from the JWT subject`() {
        val jwt = Jwt.withTokenValue("token").header("alg", "none").subject("account-1").build()

        val context = ErrorContext.of(request { userPrincipal = JwtAuthenticationToken(jwt) })

        assertEquals("account-1", context.userId)
    }

    @Test
    fun `reads ids from the URL`() {
        val context =
            ErrorContext.of(
                request {
                    setAttribute(
                        HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE,
                        mapOf("study-id" to "s-1", "deployment-id" to "d-1"),
                    )
                },
            )

        assertEquals(mapOf("study-id" to "s-1", "deployment-id" to "d-1"), context.ids)
    }

    @Test
    fun `reads the request type and top-level ids from a captured RPC body`() {
        val body =
            """
            {
                "__type": "dk.cachet.carp.deployments.infrastructure.DeploymentServiceRequest.GetStudyDeploymentStatus",
                "apiVersion": "1.3",
                "studyDeploymentId": "d-1",
                "nested": { "studyId": "ignored" },
                "deviceRoleNameId": 5
            }
            """.trimIndent()

        val context = ErrorContext.of(request { setAttribute(ErrorContext.BODY_ATTRIBUTE, body) })

        assertEquals("GetStudyDeploymentStatus", context.requestType)
        assertEquals(mapOf("studyDeploymentId" to "d-1"), context.ids)
    }

    @Test
    fun `falls back to just a reference when nothing else can be read`() {
        val context = ErrorContext.of(request { setAttribute(ErrorContext.BODY_ATTRIBUTE, "not json") })

        assertNull(context.userId)
        assertNull(context.requestType)
        assertEquals(emptyMap(), context.ids)
        assertEquals(8, context.ref.length)
    }

    @Test
    fun `gives every error its own reference`() {
        assertNotEquals(ErrorContext.of(request()).ref, ErrorContext.of(request()).ref)
    }

    @Test
    fun `lists everything known in its log form`() {
        val context = ErrorContext("ab12cd34", "account-1", "GetStatus", mapOf("study-id" to "s-1"))

        assertEquals("ref=ab12cd34, user=account-1, request=GetStatus, study-id=s-1", context.toString())
    }
}
