package dk.cachet.carp.webservices.export.controller

import dk.cachet.carp.common.application.UUID
import dk.cachet.carp.webservices.export.command.ExportCommandFactory
import dk.cachet.carp.webservices.export.domain.Export
import dk.cachet.carp.webservices.export.service.ExportService
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Nested
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import kotlin.test.BeforeTest
import kotlin.test.Test

/**
 * `AnonymousParticipantRequest`'s bean-validation constraints (see AnonymousParticipantRequestTest) only
 * take effect if the controller actually triggers validation - this covers that MVC-level wiring, which a
 * validator-only unit test cannot.
 */
class ExportControllerTest {
    private val exportCommandFactory: ExportCommandFactory = mockk()
    private val exportService: ExportService = mockk()
    private lateinit var mockMvc: MockMvc

    @BeforeTest
    fun setup() {
        mockMvc = MockMvcBuilders.standaloneSetup(ExportController(exportCommandFactory, exportService)).build()
    }

    private val studyId = UUID.randomUUID().stringRepresentation

    private fun body(
        expirationSeconds: String,
        useFastPipeline: Boolean? = null,
    ): String {
        val pipelineField = useFastPipeline?.let { ""","useFastPipeline":$it""" }.orEmpty()
        return """{"amountOfAccounts":1,"expirationSeconds":$expirationSeconds,"clientId":"client",
            "redirectUri":"https://example.com","participantRoleName":"participant"$pipelineField}"""
    }

    @Nested
    inner class ExportAnonymousParticipants {
        @Test
        fun `a valid request is delegated to the export service`() {
            runTest {
                coEvery { exportCommandFactory.createExportAnonymousParticipants(any(), any()) } returns mockk()
                coEvery { exportService.createExport(any()) } returns Export()

                mockMvc
                    .post("/api/studies/$studyId/exports/anonymous-participants") {
                        contentType = MediaType.APPLICATION_JSON
                        content = body("86400")
                    }.andExpect { status { isAccepted() } }
                coVerify(exactly = 1) { exportCommandFactory.createExportAnonymousParticipants(any(), any()) }
            }
        }

        @Test
        fun `rejects an expirationSeconds beyond Int range on the default fast pipeline`() {
            runTest {
                // useFastPipeline defaults to true, and that pipeline narrows expirationSeconds to an Int
                // (KeycloakFacade.createAnonymousAccountsBulk) - rejecting here, synchronously, with a clear
                // message is much better than letting it fail later as an opaque, undiagnosable async
                // export ERROR (Export has no error-message field).
                mockMvc
                    .post("/api/studies/$studyId/exports/anonymous-participants") {
                        contentType = MediaType.APPLICATION_JSON
                        content = body("9999999999")
                    }.andExpect { status { isBadRequest() } }
                coVerify(exactly = 0) { exportCommandFactory.createExportAnonymousParticipants(any(), any()) }
            }
        }

        @Test
        fun `accepts an expirationSeconds beyond Int range on the legacy pipeline`() {
            runTest {
                // The legacy pipeline (useFastPipeline=false) passes expirationSeconds through as a Long
                // with no narrowing, so it's exempt from the Int-range check.
                coEvery { exportCommandFactory.createExportAnonymousParticipants(any(), any()) } returns mockk()
                coEvery { exportService.createExport(any()) } returns Export()

                mockMvc
                    .post("/api/studies/$studyId/exports/anonymous-participants") {
                        contentType = MediaType.APPLICATION_JSON
                        content = body("9999999999", useFastPipeline = false)
                    }.andExpect { status { isAccepted() } }
                coVerify(exactly = 1) { exportCommandFactory.createExportAnonymousParticipants(any(), any()) }
            }
        }

        @Test
        fun `rejects a non-positive amountOfAccounts without ever reaching the service`() {
            runTest {
                mockMvc
                    .post("/api/studies/$studyId/exports/anonymous-participants") {
                        contentType = MediaType.APPLICATION_JSON
                        content =
                            """{"amountOfAccounts":0,"expirationSeconds":86400,"clientId":"client",
                                "redirectUri":"https://example.com","participantRoleName":"participant"}"""
                    }.andExpect { status { isBadRequest() } }
                coVerify(exactly = 0) { exportCommandFactory.createExportAnonymousParticipants(any(), any()) }
            }
        }
    }
}
