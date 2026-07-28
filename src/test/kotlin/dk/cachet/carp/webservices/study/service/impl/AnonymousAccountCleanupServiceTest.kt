package dk.cachet.carp.webservices.study.service.impl

import dk.cachet.carp.webservices.security.authentication.oauth2.IssuerFacade
import dk.cachet.carp.webservices.security.authentication.oauth2.issuers.keycloak.domain.BulkDeleteResult
import dk.cachet.carp.webservices.study.repository.AnonymousAccountCleanupStore
import dk.cachet.carp.webservices.study.repository.ExpiredAnonymousAccounts
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals

class AnonymousAccountCleanupServiceTest {
    private val store = mockk<AnonymousAccountCleanupStore>(relaxed = true)
    private val issuerFacade = mockk<IssuerFacade>()

    private fun service(
        maxAccountsPerRun: Long = 1000,
        perRequestLimit: Int = 100,
        maxStudiesPerScan: Int = 500,
    ) = AnonymousAccountCleanupService(store, issuerFacade, maxAccountsPerRun, perRequestLimit, maxStudiesPerScan)

    private fun expired(vararg ids: String) =
        ids.map { ExpiredAnonymousAccounts(it, Instant.parse("2026-01-01T00:00:00Z"), 0) }

    @Test
    fun `a fully-scanned study with no active sessions drops its schedule row`() =
        runBlocking {
            every { store.findExpired(any(), any()) } returns expired("s1")
            every { store.deleteIfUnchanged("s1", any()) } returns 1
            coEvery { issuerFacade.deleteAnonymousAccounts("s1", any(), any(), any()) } returns
                BulkDeleteResult(deleted = 50, skipped = 0, activeSkipped = 0, exhausted = true, cursor = 0)

            service().cleanupExpired()

            verify { store.markAttempted("s1", any()) }
            verify { store.deleteIfUnchanged("s1", any()) }
        }

    @Test
    fun `a study with an active session keeps its row and is rotated`() =
        runBlocking {
            every { store.findExpired(any(), any()) } returns expired("s1")
            coEvery { issuerFacade.deleteAnonymousAccounts("s1", any(), any(), any()) } returns
                BulkDeleteResult(deleted = 10, skipped = 5, activeSkipped = 5, exhausted = true, cursor = 5)

            service().cleanupExpired()

            verify { store.markAttempted("s1", any()) }
            verify(exactly = 0) { store.deleteIfUnchanged(any(), any()) }
        }

    @Test
    fun `the cursor is threaded across pages until the group is exhausted`() =
        runBlocking {
            every { store.findExpired(any(), any()) } returns expired("s1")
            every { store.deleteIfUnchanged("s1", any()) } returns 1
            val cursors = mutableListOf<Int>()
            coEvery { issuerFacade.deleteAnonymousAccounts("s1", any(), any(), any()) } answers {
                val cursor = arg<Int>(3)
                cursors += cursor
                if (cursor == 0) {
                    BulkDeleteResult(deleted = 100, exhausted = false, cursor = 100)
                } else {
                    BulkDeleteResult(deleted = 0, exhausted = true, cursor = 100)
                }
            }

            service(maxAccountsPerRun = 1000).cleanupExpired()

            assertEquals(listOf(0, 100), cursors) // resumed from the returned cursor, not restarted at 0
            verify { store.deleteIfUnchanged("s1", any()) }
        }

    @Test
    fun `an active session on a later page still prevents completion`() =
        runBlocking {
            every { store.findExpired(any(), any()) } returns expired("s1")
            coEvery { issuerFacade.deleteAnonymousAccounts("s1", any(), any(), any()) } answers {
                if (arg<Int>(3) == 0) {
                    BulkDeleteResult(deleted = 100, activeSkipped = 0, exhausted = false, cursor = 100)
                } else {
                    BulkDeleteResult(deleted = 0, skipped = 3, activeSkipped = 3, exhausted = true, cursor = 103)
                }
            }

            service().cleanupExpired()

            verify(exactly = 0) { store.deleteIfUnchanged(any(), any()) }
        }

    @Test
    fun `the run never deletes more than the account budget`() =
        runBlocking {
            every { store.findExpired(any(), any()) } returns expired("s1", "s2", "s3")
            every { store.deleteIfUnchanged(any(), any()) } returns 1
            val requestedLimits = mutableListOf<Int>()
            coEvery { issuerFacade.deleteAnonymousAccounts(any(), any(), any(), any()) } answers {
                val limit = thirdArg<Int>()
                requestedLimits += limit
                BulkDeleteResult(deleted = limit, exhausted = true, cursor = 0)
            }

            // budget 150, perRequest 100 => s1 examines 100, s2 examines 50 (shrunk to remaining), s3 skipped.
            service(maxAccountsPerRun = 150, perRequestLimit = 100).cleanupExpired()

            assertEquals(listOf(100, 50), requestedLimits)
            assertEquals(150, requestedLimits.sum())
            verify(exactly = 0) { store.markAttempted("s3", any()) }
        }
}
