package dk.cachet.carp.webservices.study.controller

import dk.cachet.carp.common.application.UUID
import dk.cachet.carp.webservices.account.service.AccountService
import dk.cachet.carp.webservices.common.exception.responses.BadRequestException
import dk.cachet.carp.webservices.security.authentication.service.AuthenticationService
import dk.cachet.carp.webservices.study.domain.ParticipantGroupsStatus
import dk.cachet.carp.webservices.study.dto.ParticipantAccountsResponseDto
import dk.cachet.carp.webservices.study.service.RecruitmentService
import dk.cachet.carp.webservices.study.service.StudyService
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
import kotlin.test.assertFailsWith

class StudyControllerTest {
    private val authenticationService: AuthenticationService = mockk()
    private val accountService: AccountService = mockk()
    private val studyService: StudyService = mockk()
    private val recruitmentService: RecruitmentService = mockk()
    private lateinit var mockMvc: MockMvc

    @BeforeTest
    fun setup() {
        mockMvc =
            MockMvcBuilders.standaloneSetup(
                StudyController(
                    authenticationService, accountService, studyService, recruitmentService,
                ),
            ).build()
    }

    @Nested
    inner class QueryParticipantAccounts {
        @Test
        fun `should return response as DTO if specified in request body`() {
            runTest {
                val mockStudyId = UUID.randomUUID().stringRepresentation
                val url = "/api/studies/$mockStudyId/participants/accounts"

                coEvery { recruitmentService.queryParticipantAccounts(any(), any()) } returns
                    ParticipantAccountsResponseDto(null, null, 0, emptyList())

                mockMvc
                    .post(url) {
                        contentType = MediaType.APPLICATION_JSON
                        content = """{"sortBy":"accountIdentity","sortDirection":"asc"}"""
                    }.andExpect { status { isOk() } }
                coVerify(exactly = 1) { recruitmentService.queryParticipantAccounts(any(), any()) }
            }
        }

        @Test
        fun `should return dto without requiring a flag in request body`() {
            runTest {
                val mockStudyId = UUID.randomUUID().stringRepresentation
                val url = "/api/studies/$mockStudyId/participants/accounts"

                coEvery { recruitmentService.queryParticipantAccounts(any(), any()) } returns
                    ParticipantAccountsResponseDto(null, null, 0, emptyList())

                mockMvc
                    .post(url) {
                        contentType = MediaType.APPLICATION_JSON
                        content = "{}"
                    }.andExpect { status { isOk() } }
                coVerify(exactly = 1) { recruitmentService.queryParticipantAccounts(any(), any()) }
            }
        }

        @Test
        fun `should reject request when page is provided without size`() {
            runTest {
                val mockStudyId = UUID.randomUUID().stringRepresentation
                val url = "/api/studies/$mockStudyId/participants/accounts"

                mockMvc
                    .post(url) {
                        contentType = MediaType.APPLICATION_JSON
                        content = """{"page":0}"""
                    }.andExpect { status { isBadRequest() } }
                coVerify(exactly = 0) { recruitmentService.queryParticipantAccounts(any(), any()) }
            }
        }

        @Test
        fun `should reject request when size is provided without page`() {
            runTest {
                val mockStudyId = UUID.randomUUID().stringRepresentation
                val url = "/api/studies/$mockStudyId/participants/accounts"

                mockMvc
                    .post(url) {
                        contentType = MediaType.APPLICATION_JSON
                        content = """{"size":20}"""
                    }.andExpect { status { isBadRequest() } }
                coVerify(exactly = 0) { recruitmentService.queryParticipantAccounts(any(), any()) }
            }
        }

        @Test
        fun `should reject request when sort direction is provided without sort by`() {
            runTest {
                val mockStudyId = UUID.randomUUID().stringRepresentation
                val url = "/api/studies/$mockStudyId/participants/accounts"

                mockMvc
                    .post(url) {
                        contentType = MediaType.APPLICATION_JSON
                        content = """{"sortDirection":"asc"}"""
                    }.andExpect { status { isBadRequest() } }
                coVerify(exactly = 0) { recruitmentService.queryParticipantAccounts(any(), any()) }
            }
        }
    }

    @Nested
    inner class GetParticipantGroupStatus {
        // The handler is a suspend function, so validation happens in the coroutine body; invoke it
        // directly rather than through MockMvc (which would need async dispatch to observe the result).
        private val controller =
            StudyController(authenticationService, accountService, studyService, recruitmentService)

        @Test
        fun `rejects page without size`() {
            runTest {
                assertFailsWith<BadRequestException> {
                    controller.getParticipantGroupStatus(
                        UUID.randomUUID(),
                        page = 0,
                        size = null,
                        search = null,
                        status = null,
                    )
                }
                coVerify(exactly = 0) {
                    recruitmentService.getParticipantGroupsStatus(any(), any(), any(), any(), any())
                }
            }
        }

        @Test
        fun `rejects size without page`() {
            runTest {
                assertFailsWith<BadRequestException> {
                    controller.getParticipantGroupStatus(
                        UUID.randomUUID(),
                        page = null,
                        size = 20,
                        search = null,
                        status = null,
                    )
                }
                coVerify(exactly = 0) {
                    recruitmentService.getParticipantGroupsStatus(any(), any(), any(), any(), any())
                }
            }
        }

        @Test
        fun `rejects size below one`() {
            runTest {
                assertFailsWith<BadRequestException> {
                    controller.getParticipantGroupStatus(
                        UUID.randomUUID(),
                        page = 0,
                        size = 0,
                        search = null,
                        status = null,
                    )
                }
                coVerify(exactly = 0) {
                    recruitmentService.getParticipantGroupsStatus(any(), any(), any(), any(), any())
                }
            }
        }

        @Test
        fun `rejects negative page`() {
            runTest {
                assertFailsWith<BadRequestException> {
                    controller.getParticipantGroupStatus(
                        UUID.randomUUID(),
                        page = -1,
                        size = 8,
                        search = null,
                        status = null,
                    )
                }
                coVerify(exactly = 0) {
                    recruitmentService.getParticipantGroupsStatus(any(), any(), any(), any(), any())
                }
            }
        }

        @Test
        fun `accepts a valid page and size`() {
            runTest {
                val studyId = UUID.randomUUID()
                coEvery {
                    recruitmentService.getParticipantGroupsStatus(any(), any(), any(), any(), any())
                } returns ParticipantGroupsStatus(emptyList(), emptyList(), 0)

                controller.getParticipantGroupStatus(studyId, page = 0, size = 8, search = null, status = null)

                coVerify(exactly = 1) {
                    recruitmentService.getParticipantGroupsStatus(studyId, 0, 8, null, null)
                }
            }
        }
    }
}
