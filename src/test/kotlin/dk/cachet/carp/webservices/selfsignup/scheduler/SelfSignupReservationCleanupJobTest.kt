package dk.cachet.carp.webservices.selfsignup.scheduler

import dk.cachet.carp.webservices.selfsignup.domain.StudySelfSignupConfig
import dk.cachet.carp.webservices.selfsignup.repository.ExpiredReservation
import dk.cachet.carp.webservices.selfsignup.repository.SelfSignupReservationStore
import dk.cachet.carp.webservices.selfsignup.repository.StudySelfSignupStore
import dk.cachet.carp.webservices.study.repository.AnonymousAccountCleanupStore
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.jupiter.api.Test

/**
 * The reconciliation is the whole point of this job (see its class doc): an expired, never-finalized
 * reservation is the only remaining evidence a Keycloak account might exist with no cleanup-ledger row, so
 * every one MUST get a ledger write - and, critically, that write must happen and succeed BEFORE the
 * reservation is deleted, never after, or a crash/failure in between silently loses the evidence.
 */
class SelfSignupReservationCleanupJobTest {
    private val store: SelfSignupReservationStore = mockk()
    private val configStore: StudySelfSignupStore = mockk()
    private val cleanupStore: AnonymousAccountCleanupStore = mockk(relaxUnitFun = true)

    private val job = SelfSignupReservationCleanupJob(store, configStore, cleanupStore)

    private fun config(expirationSeconds: Long) =
        StudySelfSignupConfig(
            studyId = "irrelevant",
            shortCode = "ABCDE",
            enabled = true,
            participantRoleName = "participant",
            maxParticipants = 10,
            currentParticipantCount = 0,
            clientId = "client",
            redirectUri = "https://example.com",
            subdomain = null,
            expirationSeconds = expirationSeconds,
        )

    @Test
    fun `does nothing when nothing expired`() {
        every { store.findExpired(any()) } returns emptyList()

        job.cleanup()

        verify(exactly = 0) { cleanupStore.upsert(any(), any(), any(), any()) }
        verify(exactly = 0) { store.delete(any()) }
    }

    @Test
    fun `records the cleanup schedule BEFORE deleting the reservation`() {
        val reservation = ExpiredReservation("res-1", "study-1")
        every { store.findExpired(any()) } returns listOf(reservation)
        every { configStore.findByStudyId("study-1") } returns config(expirationSeconds = 3600)

        job.cleanup()

        // Order matters: this is the exact property the fix depends on - reversing it would recreate the
        // bug (destroying the evidence before it's safely recorded elsewhere).
        verifyOrder {
            cleanupStore.upsert("study-1", any(), 1, any())
            store.delete("res-1")
        }
    }

    @Test
    fun `still schedules cleanup even if the study's self-signup config was since removed`() {
        val reservation = ExpiredReservation("res-1", "study-1")
        every { store.findExpired(any()) } returns listOf(reservation)
        every { configStore.findByStudyId("study-1") } returns null

        job.cleanup()

        verify(exactly = 1) { cleanupStore.upsert("study-1", any(), 1, any()) }
        verify(exactly = 1) { store.delete("res-1") }
    }

    @Test
    fun `when the ledger write fails, the reservation is NOT deleted, and other reservations still proceed`() {
        val failing = ExpiredReservation("res-fails", "study-fails")
        val succeeding = ExpiredReservation("res-ok", "study-ok")
        every { store.findExpired(any()) } returns listOf(failing, succeeding)
        every { configStore.findByStudyId(any()) } returns config(expirationSeconds = 3600)
        every { cleanupStore.upsert("study-fails", any(), any(), any()) } throws
            IllegalStateException("simulated ledger write failure")

        // The job must not propagate this - one reservation's reconciliation failing must not stop the
        // scheduled run entirely or block the others from being reconciled.
        job.cleanup()

        // The evidence for the failed one is preserved (not deleted) so the next sweep retries it - this
        // is what stands in for "a crash between deletion and scheduling" in the finding: since scheduling
        // is attempted first and delete never runs unless it succeeds, there is no such window anymore.
        verify(exactly = 0) { store.delete("res-fails") }
        // The other reservation is unaffected by the first one's failure.
        verify(exactly = 1) { cleanupStore.upsert("study-ok", any(), 1, any()) }
        verify(exactly = 1) { store.delete("res-ok") }
    }
}
