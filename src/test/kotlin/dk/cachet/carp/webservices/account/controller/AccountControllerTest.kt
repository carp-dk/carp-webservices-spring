package dk.cachet.carp.webservices.account.controller

import dk.cachet.carp.common.application.UUID
import dk.cachet.carp.webservices.account.domain.AccountRequest
import dk.cachet.carp.webservices.account.service.AccountService
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Nested
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import tools.jackson.databind.ObjectMapper
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import dk.cachet.carp.webservices.security.authorization.Role as AccountRole

class AccountControllerTest {
    private val accountService: AccountService = mockk()
    private val objectMapper: ObjectMapper = ObjectMapper()

    private lateinit var mockMvc: MockMvc

    @BeforeTest
    fun setup() {
        mockMvc = MockMvcBuilders.standaloneSetup(AccountController(accountService)).build()
    }

    @Nested
    inner class Invite {
        private val endpoint = "/api/accounts/invite"

        @Test
        fun `should return 400 if email is invalid`() =
            runTest {
                val accountRequest = AccountRequest("invalid", AccountRole.PARTICIPANT)

                mockMvc.perform(
                    post(endpoint)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(accountRequest)),
                )
                    .andExpect(status().isBadRequest)
            }

        @Test
        fun `should return 400 if the request body is missing`() =
            runTest {
                mockMvc.perform(
                    post(endpoint)
                        .contentType(MediaType.APPLICATION_JSON),
                )
                    .andExpect(status().isBadRequest)
            }

        @Test
        fun `should relay task to account service`() =
            runTest {
                coEvery { accountService.invite(any(), any()) } returns mockk()
                val accountRequest = AccountRequest("address@domain.org", AccountRole.PARTICIPANT)

                mockMvc.perform(
                    post(endpoint)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(accountRequest)),
                )
                    .andExpect(status().isCreated)
            }
    }

    @Nested
    inner class Role {
        private val endpoint = "/api/accounts/role"

        @Test
        fun `should return 400 if email is invalid`() =
            runTest {
                val accountRequest = AccountRequest("invalid", AccountRole.PARTICIPANT)

                mockMvc.perform(
                    post(endpoint)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(accountRequest)),
                )
                    .andExpect(status().isBadRequest)
            }

        @Test
        fun `should return 400 if the request body is missing`() =
            runTest {
                mockMvc.perform(
                    post(endpoint)
                        .contentType(MediaType.APPLICATION_JSON),
                )
                    .andExpect(status().isBadRequest)
            }

        @Test
        fun `should relay task to account service`() =
            runTest {
                coEvery { accountService.hasRoleByEmail(any(), any()) } returns mockk()
                val accountRequest = AccountRequest("address@domain.org", AccountRole.PARTICIPANT)

                val resultActions: ResultActions =
                    mockMvc.perform(
                        post(endpoint)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(accountRequest)),
                    )

                resultActions.andExpect(status().isOk)
            }

        @Test
        fun `should return 404 if role is not found`() =
            runTest {
                val accountRequest = AccountRequest("address@domain.org", AccountRole.PARTICIPANT)
                coEvery { accountService.hasRoleByEmail(any(), any()) } returns false

                val resultActions: ResultActions =
                    mockMvc.perform(
                        post(endpoint)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(accountRequest)),
                    )

                assertEquals(resultActions.andReturn().asyncResult.toString(), "<404 NOT_FOUND Not Found,[]>")
            }
    }

    @Nested
    inner class Info {
        private val validUUIDEndpoint = "/api/accounts/${UUID.randomUUID().stringRepresentation}"

        @Test
        fun `should relay task to account service`() =
            runTest {
                coEvery { accountService.findByUUID(any()) } returns mockk()

                mockMvc.perform(
                    get(validUUIDEndpoint)
                        .contentType(MediaType.APPLICATION_JSON),
                )
                    .andExpect(status().isOk)
            }
    }
}
