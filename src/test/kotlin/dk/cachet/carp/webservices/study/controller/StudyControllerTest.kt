package dk.cachet.carp.webservices.study.controller

import dk.cachet.carp.common.application.UUID
import dk.cachet.carp.webservices.account.service.AccountService
import dk.cachet.carp.webservices.security.authentication.service.AuthenticationService
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
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import kotlin.test.BeforeTest
import kotlin.test.Test

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
    inner class GetParticipantAccounts {
        @Test
        fun `should return a list`() {
            runTest {
                val mockStudyId = UUID.randomUUID().stringRepresentation
                val url = "/api/studies/$mockStudyId/participants/accounts"

                coEvery { recruitmentService.getParticipants(any(), any(), any(), any(), any(), any()) }

                mockMvc.get(url).andExpect { status { isOk() } }
                coVerify(exactly = 1) { recruitmentService.getParticipants(any(), any(), any(), any(), any(), any()) }
                coVerify(exactly = 0) { recruitmentService.countParticipants(any(), any()) }
            }
        }

        @Test
        fun `should return response as DTO if specified in query`() {
            runTest {
                val mockStudyId = UUID.randomUUID().stringRepresentation
                val url = "/api/studies/$mockStudyId/participants/accounts?response_as_dto=true"

                coEvery { recruitmentService.countParticipants(any(), any()) } returns 0
                coEvery {
                    recruitmentService.getParticipants(any(), any(), any(), any(), any(), any())
                } returns emptyList()

                mockMvc.get(url).andExpect { status { isOk() } }
                coVerify(exactly = 1) { recruitmentService.getParticipants(any(), any(), any(), any(), any(), any()) }
                coVerify(exactly = 1) { recruitmentService.countParticipants(any(), any()) }
            }
        }
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
}
