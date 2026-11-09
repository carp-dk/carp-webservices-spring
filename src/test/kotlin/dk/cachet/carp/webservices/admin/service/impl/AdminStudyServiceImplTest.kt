package dk.cachet.carp.webservices.admin.service.impl

import dk.cachet.carp.common.application.UUID
import dk.cachet.carp.webservices.account.service.AccountService
import dk.cachet.carp.webservices.admin.domain.AdminStudyRow
import dk.cachet.carp.webservices.admin.dto.StudyOwnerDto
import dk.cachet.carp.webservices.admin.repository.AdminStudyStore
import dk.cachet.carp.webservices.common.input.ApplicationDataService
import dk.cachet.carp.webservices.security.authentication.domain.Account
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
    private val accountService = mockk<AccountService>()
    private val sut = AdminStudyServiceImpl(store, accountService, ApplicationDataService(ObjectMapper()))

    private val owner = UUID.randomUUID()
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

    @Test
    fun `each distinct owner is looked up once and a missing owner leaves null fields`() =
        runTest {
            every { store.findAll(null) } returns
                listOf(
                    row(owner.stringRepresentation, """{"applicationName":"carp-studies-app"}"""),
                    row(owner.stringRepresentation),
                    row(goneOwner.stringRepresentation),
                    row("not-a-uuid"),
                    row(null),
                )
            coEvery { accountService.findByUUID(owner) } returns
                Account(
                    id = owner.stringRepresentation,
                    email = "jane@example.com",
                    firstName = "Jane",
                    lastName = null,
                )
            coEvery { accountService.findByUUID(goneOwner) } returns null

            val studies = sut.getStudies(null)

            assertEquals(StudyOwnerDto(owner.stringRepresentation, "jane@example.com", "Jane"), studies[0].owner)
            assertEquals(studies[0].owner, studies[1].owner)
            assertEquals(StudyOwnerDto(goneOwner.stringRepresentation, null, null), studies[2].owner)
            assertEquals(StudyOwnerDto("not-a-uuid", null, null), studies[3].owner)
            assertEquals(StudyOwnerDto(null, null, null), studies[4].owner)
            assertEquals("carp-studies-app", studies[0].applicationName)
            coVerify(exactly = 1) { accountService.findByUUID(owner) }
            coVerify(exactly = 1) { accountService.findByUUID(goneOwner) }
        }

    @Test
    fun `the live filter is passed to the store`() =
        runTest {
            every { store.findAll(true) } returns emptyList()

            assertEquals(emptyList(), sut.getStudies(true))
        }
}
