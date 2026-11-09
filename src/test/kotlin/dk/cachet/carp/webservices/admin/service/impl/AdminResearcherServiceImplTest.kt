package dk.cachet.carp.webservices.admin.service.impl

import dk.cachet.carp.webservices.admin.domain.OwnedStudyCounts
import dk.cachet.carp.webservices.admin.repository.AdminStudyStore
import dk.cachet.carp.webservices.security.authentication.domain.AccountSummary
import dk.cachet.carp.webservices.security.authentication.oauth2.IssuerFacade
import dk.cachet.carp.webservices.security.authorization.Role
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class AdminResearcherServiceImplTest {
    private val issuerFacade = mockk<IssuerFacade>()
    private val store = mockk<AdminStudyStore>()
    private val sut = AdminResearcherServiceImpl(issuerFacade, store)

    private fun account(
        id: String,
        createdAt: Instant?,
        firstName: String? = null,
        lastName: String? = null,
    ) = AccountSummary(
        id = id,
        username = "$id@example.com",
        email = "$id@example.com",
        firstName = firstName,
        lastName = lastName,
        enabled = true,
        emailVerified = true,
        createdAt = createdAt,
    )

    @Test
    fun `joins owned-study counts and defaults researchers without studies to zero`() =
        runTest {
            coEvery { issuerFacade.getAccountsWithRole(Role.RESEARCHER) } returns
                listOf(
                    account("owner", Instant.parse("2025-01-01T00:00:00Z"), "Ann", "Lee"),
                    account("idle", Instant.parse("2024-01-01T00:00:00Z"), firstName = "Bo"),
                )
            // "not-a-researcher" owns a study but lacks the role: it must not appear.
            every { store.countStudiesByOwner() } returns
                mapOf("owner" to OwnedStudyCounts(3, 1), "not-a-researcher" to OwnedStudyCounts(1, 0))

            val researchers = sut.getResearchers().associateBy { it.id }

            assertEquals(setOf("owner", "idle"), researchers.keys)
            assertEquals(3L to 1L, researchers.getValue("owner").let { it.ownedStudyCount to it.liveStudyCount })
            assertEquals(0L to 0L, researchers.getValue("idle").let { it.ownedStudyCount to it.liveStudyCount })
            assertEquals("Ann Lee", researchers.getValue("owner").fullName)
            assertEquals("Bo", researchers.getValue("idle").fullName)
        }

    @Test
    fun `lists newest account first with unknown created times last`() =
        runTest {
            coEvery { issuerFacade.getAccountsWithRole(Role.RESEARCHER) } returns
                listOf(
                    account("old", Instant.parse("2024-01-01T00:00:00Z")),
                    account("unknown", null),
                    account("new", Instant.parse("2026-01-01T00:00:00Z")),
                )
            every { store.countStudiesByOwner() } returns emptyMap()

            assertEquals(listOf("new", "old", "unknown"), sut.getResearchers().map { it.id })
        }
}
