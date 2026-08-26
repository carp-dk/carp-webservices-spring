package dk.cachet.carp.webservices.selfsignup.service.impl

import dk.cachet.carp.common.application.UUID
import dk.cachet.carp.studies.application.StudyStatus
import dk.cachet.carp.webservices.account.service.AccountService
import dk.cachet.carp.webservices.common.exception.responses.ConflictException
import dk.cachet.carp.webservices.common.exception.responses.ResourceNotFoundException
import dk.cachet.carp.webservices.common.exception.responses.TooManyRequestsException
import dk.cachet.carp.webservices.common.services.CoreServiceContainer
import dk.cachet.carp.webservices.security.authentication.oauth2.issuers.keycloak.domain.MagicLinkResponse
import dk.cachet.carp.webservices.selfsignup.domain.StudySelfSignupConfig
import dk.cachet.carp.webservices.selfsignup.repository.ClaimedReservation
import dk.cachet.carp.webservices.selfsignup.repository.SelfSignupRateLimitStore
import dk.cachet.carp.webservices.selfsignup.repository.SelfSignupReservationStore
import dk.cachet.carp.webservices.selfsignup.repository.StudySelfSignupStore
import dk.cachet.carp.webservices.study.repository.AnonymousAccountCleanupStore
import dk.cachet.carp.webservices.study.service.AnonymousService
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * `addSelfSignupParticipant`'s `reserveCapacity` lambda is only meaningful once actually invoked inside
 * the real transaction (see AnonymousServiceImp) - here, where that method is mocked, tests that care about
 * its effect capture the lambda and invoke it themselves, faithfully simulating what the real
 * implementation does (including propagating a throw from it as a failure of the whole call).
 */
class SelfSignupPublicServiceImplTest {
    private val services: CoreServiceContainer = mockk()
    private val store: StudySelfSignupStore = mockk()
    private val reservationStore: SelfSignupReservationStore = mockk()
    private val rateLimitStore: SelfSignupRateLimitStore = mockk()
    private val accountService: AccountService = mockk()
    private val anonymousService: AnonymousService = mockk()
    private val anonymousAccountCleanupStore: AnonymousAccountCleanupStore = mockk()

    private val service =
        SelfSignupPublicServiceImpl(
            services, store, reservationStore, rateLimitStore, accountService, anonymousService,
            anonymousAccountCleanupStore, 20, 300,
        )

    private val studyId = UUID.randomUUID().stringRepresentation
    private val reservationId = "reservation-1"
    private val config =
        StudySelfSignupConfig(
            studyId = studyId,
            shortCode = "ABCDE",
            enabled = true,
            participantRoleName = "participant",
            maxParticipants = 10,
            currentParticipantCount = 0,
            clientId = "client",
            redirectUri = "https://example.com",
            subdomain = null,
            expirationSeconds = 86_400,
        )

    private fun stubCommonFixtures(isLive: Boolean = true) {
        every { rateLimitStore.incrementAndGet(any(), any()) } returns 1
        every { store.findByShortCode("ABCDE") } returns config
        every { services.internalStudyService } returns
            mockk {
                coEvery { getStudyStatus(any()) } returns
                    mockk<StudyStatus> { every { canDeployToParticipants } returns isLive }
                coEvery { getStudyDetails(any()) } returns
                    mockk {
                        every { protocolSnapshot } returns
                            mockk {
                                every { participantRoles } returns
                                    setOf(mockk { every { role } returns config.participantRoleName })
                            }
                    }
            }
    }

    /** Simulates addSelfSignupParticipant invoking reserveCapacity inside its (mocked) transaction. */
    private fun stubAddSelfSignupParticipant() {
        val capacity = slot<() -> Unit>()
        coEvery {
            anonymousService.addSelfSignupParticipant(any(), any(), any(), any(), capture(capacity))
        } answers {
            capacity.captured.invoke()
            mockk()
        }
    }

    @Test
    fun `rejects when the rate limit is exceeded, before ever claiming a reservation`() {
        runTest {
            every { rateLimitStore.incrementAndGet(any(), any()) } returns 21

            assertFailsWith<TooManyRequestsException> { service.signUp("ABCDE", "10.0.0.1") }
            coVerify(exactly = 0) { reservationStore.tryClaim(any(), any(), any()) }
        }
    }

    @Test
    fun `rejects an unknown code, quietly - a typo or code-guessing attempt is expected, not a paging-worthy error`() {
        runTest {
            every { rateLimitStore.incrementAndGet(any(), any()) } returns 1
            every { store.findByShortCode("ZZZZZ") } returns null

            val exception = assertFailsWith<ResourceNotFoundException> { service.signUp("zzzzz", "10.0.0.1") }

            assertTrue(exception.quiet)
        }
    }

    @Test
    fun `rejects when the study is no longer live, without ever claiming a reservation or calling Keycloak`() {
        runTest {
            stubCommonFixtures(isLive = false)

            assertFailsWith<ConflictException> { service.signUp("abcde", "10.0.0.1") }
            coVerify(exactly = 0) { reservationStore.tryClaim(any(), any(), any()) }
            coVerify(exactly = 0) {
                accountService.generateAnonymousAccountBulk(any(), any(), any(), any(), any(), any())
            }
        }
    }

    @Test
    fun `rejects and releases the reservation when the study stops being live just before the Keycloak call`() {
        runTest {
            every { rateLimitStore.incrementAndGet(any(), any()) } returns 1
            every { store.findByShortCode("ABCDE") } returns config
            every { services.internalStudyService } returns
                mockk {
                    // First call (in signUp, before claiming) sees the study as live; second call (right
                    // before the Keycloak call) does not - simulating an admin stopping the study in
                    // between.
                    coEvery { getStudyStatus(any()) } returnsMany
                        listOf(
                            mockk<StudyStatus> { every { canDeployToParticipants } returns true },
                            mockk<StudyStatus> { every { canDeployToParticipants } returns false },
                        )
                }
            every { reservationStore.tryClaim(studyId, any(), any()) } returns ClaimedReservation(reservationId, config)
            every { reservationStore.delete(reservationId) } just Runs

            assertFailsWith<ConflictException> { service.signUp("abcde", "10.0.0.1") }

            // Unlike every other failure past tryClaim, this one is known to happen before any Keycloak
            // call is made, so the reservation is released immediately instead of left to expire.
            verify(exactly = 1) { reservationStore.delete(reservationId) }
            coVerify(exactly = 0) {
                accountService.generateAnonymousAccountBulk(any(), any(), any(), any(), any(), any())
            }
        }
    }

    @Test
    fun `rejects and releases the reservation when the protocol no longer defines the configured role`() {
        runTest {
            every { rateLimitStore.incrementAndGet(any(), any()) } returns 1
            every { store.findByShortCode("ABCDE") } returns config
            every { services.internalStudyService } returns
                mockk {
                    coEvery { getStudyStatus(any()) } returns
                        mockk<StudyStatus> { every { canDeployToParticipants } returns true }
                    // Simulates an admin replacing the protocol after self-signup was enabled, without
                    // disabling it: the role config still points at no longer exists.
                    coEvery { getStudyDetails(any()) } returns
                        mockk {
                            every { protocolSnapshot } returns
                                mockk {
                                    every { participantRoles } returns
                                        setOf(mockk { every { role } returns "some-other-role" })
                                }
                        }
                }
            every { reservationStore.tryClaim(studyId, any(), any()) } returns ClaimedReservation(reservationId, config)
            every { reservationStore.delete(reservationId) } just Runs

            assertFailsWith<ConflictException> { service.signUp("abcde", "10.0.0.1") }

            verify(exactly = 1) { reservationStore.delete(reservationId) }
            coVerify(exactly = 0) {
                accountService.generateAnonymousAccountBulk(any(), any(), any(), any(), any(), any())
            }
        }
    }

    @Test
    fun `rejects when the reservation store reports no capacity, before ever calling Keycloak`() {
        runTest {
            stubCommonFixtures()
            every { reservationStore.tryClaim(studyId, any(), any()) } returns null

            assertFailsWith<ConflictException> { service.signUp("abcde", "10.0.0.1") }
            // The whole point of claiming BEFORE Keycloak: a rejected caller never mints an account.
            coVerify(exactly = 0) {
                accountService.generateAnonymousAccountBulk(any(), any(), any(), any(), any(), any())
            }
        }
    }

    @Test
    fun `on success, records cleanup atomically with finalize, and never uses the swallowing fallback`() {
        runTest {
            stubCommonFixtures()
            every { reservationStore.tryClaim(studyId, any(), any()) } returns ClaimedReservation(reservationId, config)
            val accountId = UUID.randomUUID().stringRepresentation
            coEvery {
                accountService.generateAnonymousAccountBulk(
                    config.expirationSeconds, config.clientId, config.redirectUri, config.subdomain, 1, studyId,
                )
            } returns flowOf(MagicLinkResponse(accountId, "https://example.com/magic-link"))
            stubAddSelfSignupParticipant()
            every { reservationStore.finalize(reservationId, studyId, any()) } returns true
            every { anonymousAccountCleanupStore.upsert(studyId, any(), 1, any()) } returns Unit

            val result = service.signUp("abcde", "10.0.0.1")

            kotlin.test.assertEquals("https://example.com/magic-link", result.magicLink)
            coVerify(exactly = 1) { reservationStore.finalize(reservationId, studyId, any()) }
            // The durable, non-swallowing write - not the best-effort fallback, which must never fire on a
            // clean success (it would silently double-count account_count in the ledger).
            verify(exactly = 1) { anonymousAccountCleanupStore.upsert(studyId, any(), 1, any()) }
            coVerify(exactly = 0) { anonymousService.recordCleanupSchedule(any(), any(), any()) }
        }
    }

    @Test
    fun `when the ledger write itself fails inside the transaction, the fallback still schedules cleanup`() {
        runTest {
            // This is the exact gap being closed: a swallowed ledger-write failure must not let a request
            // return success with the account's only tracking silently lost. Simulated here as the write
            // throwing (standing in for the transaction rolling back on a real DB failure) - the fallback
            // in the outer catch must still attempt to record it.
            stubCommonFixtures()
            every { reservationStore.tryClaim(studyId, any(), any()) } returns ClaimedReservation(reservationId, config)
            val accountId = UUID.randomUUID().stringRepresentation
            coEvery {
                accountService.generateAnonymousAccountBulk(
                    config.expirationSeconds, config.clientId, config.redirectUri, config.subdomain, 1, studyId,
                )
            } returns flowOf(MagicLinkResponse(accountId, "https://example.com/magic-link"))
            stubAddSelfSignupParticipant()
            every { reservationStore.finalize(reservationId, studyId, any()) } returns true
            every { anonymousAccountCleanupStore.upsert(studyId, any(), 1, any()) } throws
                IllegalStateException("simulated ledger write failure")
            coEvery { anonymousService.recordCleanupSchedule(any(), any(), any()) } returns Unit

            assertFailsWith<IllegalStateException> { service.signUp("abcde", "10.0.0.1") }

            verify(exactly = 1) { anonymousAccountCleanupStore.upsert(studyId, any(), 1, any()) }
            coVerify(exactly = 1) { anonymousService.recordCleanupSchedule(UUID(studyId), any(), 1) }
        }
    }

    @Test
    fun `when the reservation expires before it can be finalized, it is left for reconciliation, not deleted`() {
        runTest {
            stubCommonFixtures()
            every { reservationStore.tryClaim(studyId, any(), any()) } returns ClaimedReservation(reservationId, config)
            val accountId = UUID.randomUUID().stringRepresentation
            coEvery {
                accountService.generateAnonymousAccountBulk(
                    config.expirationSeconds, config.clientId, config.redirectUri, config.subdomain, 1, studyId,
                )
            } returns flowOf(MagicLinkResponse(accountId, "https://example.com/magic-link"))
            stubAddSelfSignupParticipant()
            every { reservationStore.finalize(reservationId, studyId, any()) } returns false
            coEvery { anonymousService.recordCleanupSchedule(any(), any(), any()) } returns Unit

            assertFailsWith<ConflictException> { service.signUp("abcde", "10.0.0.1") }

            // finalize() fails before the transactional ledger write is even attempted; the fallback in
            // the outer catch handles it, and the reservation itself is left alone (there is no release
            // call to make) for SelfSignupReservationCleanupJob's own record-then-delete reconciliation.
            verify(exactly = 0) { anonymousAccountCleanupStore.upsert(any(), any(), any(), any()) }
            coVerify(exactly = 1) { anonymousService.recordCleanupSchedule(UUID(studyId), any(), 1) }
        }
    }

    @Test
    fun `a malformed response still schedules cleanup and leaves the reservation for reconciliation`() {
        runTest {
            stubCommonFixtures()
            every { reservationStore.tryClaim(studyId, any(), any()) } returns ClaimedReservation(reservationId, config)
            // userId is null - a serialization/parsing issue, NOT evidence the account wasn't created.
            coEvery {
                accountService.generateAnonymousAccountBulk(
                    config.expirationSeconds, config.clientId, config.redirectUri, config.subdomain, 1, studyId,
                )
            } returns flowOf(MagicLinkResponse(null, "https://example.com/magic-link"))
            coEvery { anonymousService.recordCleanupSchedule(any(), any(), any()) } returns Unit

            assertFailsWith<IllegalStateException> { service.signUp("abcde", "10.0.0.1") }

            coVerify(exactly = 1) { anonymousService.recordCleanupSchedule(UUID(studyId), any(), 1) }
            coVerify(exactly = 0) { anonymousService.addSelfSignupParticipant(any(), any(), any(), any(), any()) }
        }
    }

    @Test
    fun `when participant persistence fails, cleanup is still scheduled and the reservation is not deleted`() {
        runTest {
            stubCommonFixtures()
            every { reservationStore.tryClaim(studyId, any(), any()) } returns ClaimedReservation(reservationId, config)
            val accountId = UUID.randomUUID().stringRepresentation
            coEvery {
                accountService.generateAnonymousAccountBulk(
                    config.expirationSeconds, config.clientId, config.redirectUri, config.subdomain, 1, studyId,
                )
            } returns flowOf(MagicLinkResponse(accountId, "https://example.com/magic-link"))
            coEvery { anonymousService.addSelfSignupParticipant(any(), any(), any(), any(), any()) } throws
                IllegalStateException("db failure")
            coEvery { anonymousService.recordCleanupSchedule(any(), any(), any()) } returns Unit

            assertFailsWith<IllegalStateException> { service.signUp("abcde", "10.0.0.1") }

            coVerify(exactly = 1) { anonymousService.recordCleanupSchedule(UUID(studyId), any(), 1) }
        }
    }

    @Test
    fun `when Keycloak itself throws, the reservation is left intact rather than released`() {
        runTest {
            // Standing in for a timeout/lost response: we can't know whether Keycloak actually created the
            // account before the call failed on our end, so the reservation must not be deleted here - it
            // is the only remaining evidence SelfSignupReservationCleanupJob could use to find an orphan.
            stubCommonFixtures()
            every { reservationStore.tryClaim(studyId, any(), any()) } returns ClaimedReservation(reservationId, config)
            coEvery {
                accountService.generateAnonymousAccountBulk(
                    config.expirationSeconds, config.clientId, config.redirectUri, config.subdomain, 1, studyId,
                )
            } throws IllegalStateException("simulated timeout / lost response")

            assertFailsWith<IllegalStateException> { service.signUp("abcde", "10.0.0.1") }

            // No cleanup schedule either - accountCreated is never set true here, since we never received a
            // response to know a group-joined account exists. Nothing about the reservation is touched at
            // all; it is left exactly as claimed, to expire and be reconciled if an account did exist.
            coVerify(exactly = 0) { anonymousService.recordCleanupSchedule(any(), any(), any()) }
        }
    }
}
