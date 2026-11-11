package dk.cachet.carp.webservices.common.notification.service.impl

import dk.cachet.carp.webservices.common.environment.EnvironmentProfile
import dk.cachet.carp.webservices.common.exception.advices.CarpErrorResponse
import dk.cachet.carp.webservices.common.exception.advices.ErrorContext
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class NotificationServiceImplTest {
    @Nested
    inner class FormatException {
        private val error = CarpErrorResponse(500, "java.lang.IllegalStateException", "Boom", "POST /api/x", "ab12cd34")

        @Test
        fun `includes the request type, user, ids and reference`() {
            val context =
                ErrorContext(
                    ref = "ab12cd34",
                    userId = "user-1",
                    requestType = "GetStudyDeploymentStatus",
                    ids = mapOf("study-id" to "s-1", "studyDeploymentId" to "d-1"),
                )

            val lines =
                NotificationServiceImpl.formatException(error, context, EnvironmentProfile.PRODUCTION).split("\n\n")

            assertEquals(
                listOf(
                    "- Exception Code: 500",
                    "- Exception: java.lang.IllegalStateException",
                    "- Message: Boom",
                    "- Path: POST /api/x",
                    "- Request: GetStudyDeploymentStatus",
                    "- User: user-1",
                    "- IDs: study-id=s-1, studyDeploymentId=d-1",
                    "- Ref: ab12cd34",
                    "- Environment: PRODUCTION",
                ),
                lines,
            )
        }

        @Test
        fun `leaves out context that is unknown`() {
            val lines =
                NotificationServiceImpl.formatException(error, ErrorContext("ab12cd34"), EnvironmentProfile.PRODUCTION)
                    .split("\n\n")

            assertEquals(
                listOf(
                    "- Exception Code: 500",
                    "- Exception: java.lang.IllegalStateException",
                    "- Message: Boom",
                    "- Path: POST /api/x",
                    "- Ref: ab12cd34",
                    "- Environment: PRODUCTION",
                ),
                lines,
            )
        }
    }

    @Nested
    inner class FormatDigest {
        @Test
        fun `lists groups by frequency`() {
            val repeats = mapOf("401 A GET /x" to 3, "403 B GET /y" to 7)

            val lines = NotificationServiceImpl.formatDigest(repeats, EnvironmentProfile.PRODUCTION).split("\n\n")
            assertEquals("- 7 more: 403 B GET /y", lines[1])
            assertEquals("- 3 more: 401 A GET /x", lines[2])
            assertEquals("Environment: PRODUCTION", lines.last())
        }

        @Test
        fun `summarises groups beyond the line limit`() {
            val repeats = (1..25).associate { "group $it" to it }

            val lines = NotificationServiceImpl.formatDigest(repeats, EnvironmentProfile.PRODUCTION).split("\n\n")

            assertEquals(1 + 20 + 1 + 1, lines.size)
            assertEquals("- 15 more across 5 other error groups", lines[21])
        }
    }
}
