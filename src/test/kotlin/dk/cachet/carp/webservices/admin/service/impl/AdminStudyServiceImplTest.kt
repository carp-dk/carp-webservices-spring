package dk.cachet.carp.webservices.admin.service.impl

import dk.cachet.carp.common.application.UUID
import dk.cachet.carp.webservices.account.service.AccountService
import dk.cachet.carp.webservices.admin.domain.AdminStudyRow
import dk.cachet.carp.webservices.admin.dto.StudyOwnerDto
import dk.cachet.carp.webservices.admin.repository.AdminStudyStore
import dk.cachet.carp.webservices.common.input.ApplicationDataService
import dk.cachet.carp.webservices.security.authentication.domain.Account
import dk.cachet.carp.webservices.security.authentication.domain.AccountSummary
import dk.cachet.carp.webservices.security.authentication.oauth2.IssuerFacade
import dk.cachet.carp.webservices.security.authorization.Role
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import tools.jackson.databind.ObjectMapper
import kotlin.test.Test
import kotlin.test.assertEquals

class AdminStudyServiceImplTest {
    private val store = mockk<AdminStudyStore>()
    private val issuerFacade = mockk<IssuerFacade>()
    private val accountService = mockk<AccountService>()
    private val sut = AdminStudyServiceImpl(store, issuerFacade, accountService, ApplicationDataService(ObjectMapper()))

    private val researcher = UUID.randomUUID()
    private val formerResearcher = UUID.randomUUID()
    private val goneOwner = UUID.randomUUID()

    private fun row(
        ownerId: String?,
        applicationData: String? = null,
    ) = AdminStudyRow(
        studyId = UUID.randomUUID().stringRepresentation,
        name = "Study",
        description = null,
        ownerId = ownerId,
        isLive = true,
        applicationData = applicationData,
        createdAt = null,
        updatedAt = null,
        firstDeploymentAt = null,
        participantCount = 0,
        deploymentCount = 0,
        fileCount = 0,
    )

    private fun summary(
        id: UUID,
        email: String,
        firstName: String?,
    ) = AccountSummary(
        id = id.stringRepresentation,
        username = email,
        email = email,
        firstName = firstName,
        lastName = null,
        enabled = true,
        emailVerified = true,
        createdAt = null,
    )

    @Test
    fun `owners come from the researcher listing, with individual lookups only for the rest`() =
        runTest {
            every { store.findAll(null) } returns
                listOf(
                    row(researcher.stringRepresentation, """{"applicationName":"carp-studies-app"}"""),
                    row(researcher.stringRepresentation),
                    row(formerResearcher.stringRepresentation),
                    row(goneOwner.stringRepresentation),
                    row("not-a-uuid"),
                    row(null),
                )
            coEvery { issuerFacade.getAccountsWithRole(Role.RESEARCHER) } returns
                listOf(
                    summary(researcher, "jane@example.com", "Jane"),
                    summary(UUID.randomUUID(), "x@example.com", null),
                )
            coEvery { accountService.findByUUID(formerResearcher) } returns
                Account(id = formerResearcher.stringRepresentation, email = "bo@example.com", firstName = "Bo")
            coEvery { accountService.findByUUID(goneOwner) } returns null

            val studies = sut.getStudies(null)

            assertEquals(StudyOwnerDto(researcher.stringRepresentation, "jane@example.com", "Jane"), studies[0].owner)
            assertEquals(studies[0].owner, studies[1].owner)
            assertEquals(StudyOwnerDto(formerResearcher.stringRepresentation, "bo@example.com", "Bo"), studies[2].owner)
            assertEquals(StudyOwnerDto(goneOwner.stringRepresentation, null, null), studies[3].owner)
            assertEquals(StudyOwnerDto("not-a-uuid", null, null), studies[4].owner)
            assertEquals(StudyOwnerDto(null, null, null), studies[5].owner)
            assertEquals("carp-studies-app", studies[0].applicationName)
            coVerify(exactly = 1) { issuerFacade.getAccountsWithRole(Role.RESEARCHER) }
            coVerify(exactly = 0) { accountService.findByUUID(researcher) }
            coVerify(exactly = 1) { accountService.findByUUID(formerResearcher) }
        }

    @Test
    fun `a failed researcher listing falls back to individual lookups`() =
        runTest {
            every { store.findAll(null) } returns listOf(row(researcher.stringRepresentation))
            coEvery { issuerFacade.getAccountsWithRole(Role.RESEARCHER) } throws IllegalStateException("403 Forbidden")
            coEvery { accountService.findByUUID(researcher) } returns
                Account(id = researcher.stringRepresentation, email = "jane@example.com", firstName = "Jane")

            val owner = sut.getStudies(null).single().owner

            assertEquals(StudyOwnerDto(researcher.stringRepresentation, "jane@example.com", "Jane"), owner)
        }

    @Test
    fun `the live filter is passed to the store and no owners means no Keycloak calls`() =
        runTest {
            every { store.findAll(true) } returns emptyList()

            assertEquals(emptyList(), sut.getStudies(true))
            coVerify(exactly = 0) { issuerFacade.getAccountsWithRole(any()) }
        }
}
