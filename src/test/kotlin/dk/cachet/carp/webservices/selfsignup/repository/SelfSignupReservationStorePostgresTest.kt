package dk.cachet.carp.webservices.selfsignup.repository

import dk.cachet.carp.common.application.UUID
import dk.cachet.carp.webservices.selfsignup.domain.StudySelfSignupConfig
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.core.io.ClassPathResource
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.jdbc.datasource.init.ScriptUtils
import org.springframework.transaction.support.TransactionTemplate
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import java.time.Duration
import java.util.concurrent.Executors
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Integration coverage for [SelfSignupReservationStore] against a real database: the two-phase
 * claim-before-Keycloak / finalize-with-the-participant lifecycle that makes self-signup capacity both
 * (a) never oversold under a concurrent burst, checked BEFORE any Keycloak account is minted, and
 * (b) never permanently leaked by an interrupted request, since an unfinalized claim simply expires.
 */
@Testcontainers(disabledWithoutDocker = true)
class SelfSignupReservationStorePostgresTest {
    private lateinit var jdbc: JdbcTemplate
    private lateinit var store: SelfSignupReservationStore
    private lateinit var configStore: StudySelfSignupStore
    private lateinit var dataSource: DriverManagerDataSource
    private val studyId = UUID.randomUUID().stringRepresentation
    private val ttl = Duration.ofMinutes(5)

    @BeforeEach
    fun prepareDatabase() {
        dataSource =
            DriverManagerDataSource().apply {
                setDriverClassName(postgres.driverClassName)
                url = postgres.jdbcUrl
                username = postgres.username
                password = postgres.password
            }
        jdbc = JdbcTemplate(dataSource)
        configStore = StudySelfSignupStore(jdbc)
        store = SelfSignupReservationStore(jdbc, DataSourceTransactionManager(dataSource))
        jdbc.execute("DROP SCHEMA public CASCADE; CREATE SCHEMA public")
        dataSource.connection.use { connection ->
            ScriptUtils.executeSqlScript(connection, ClassPathResource("db/migration/V10__add_study_self_signup.sql"))
            ScriptUtils.executeSqlScript(
                connection,
                ClassPathResource("db/migration/V11__add_self_signup_reservation.sql"),
            )
        }
    }

    private fun insertConfig(
        maxParticipants: Int = 3,
        enabled: Boolean = true,
    ) {
        configStore.insert(
            StudySelfSignupConfig(
                studyId = studyId,
                shortCode = "ABCDE",
                enabled = enabled,
                participantRoleName = "participant",
                maxParticipants = maxParticipants,
                currentParticipantCount = 0,
                clientId = "client",
                redirectUri = "https://example.com",
                subdomain = null,
                expirationSeconds = 86_400,
            ),
        )
    }

    @Test
    fun `tryClaim succeeds under the cap and each claim gets a distinct id`() {
        insertConfig(maxParticipants = 2)

        val first = store.tryClaim(studyId, ttl)
        val second = store.tryClaim(studyId, ttl)

        assertNotNull(first)
        assertNotNull(second)
        assertNotEquals(first.reservationId, second.reservationId)
    }

    @Test
    fun `tryClaim returns the current configuration, not a stale earlier read`() {
        insertConfig(maxParticipants = 2)
        // Simulates a caller resolving the short code to a config BEFORE this claim, then an admin
        // reconfiguring the study in the window between that read and the claim - tryClaim's own read
        // (taken under the same lock as the capacity check) must reflect the reconfiguration, not whatever
        // an earlier, unlocked read saw.
        configStore.update(
            studyId = studyId,
            enabled = true,
            participantRoleName = "new-role",
            maxParticipants = 2,
            clientId = "new-client",
            redirectUri = "https://new.example.com",
            subdomain = "new-subdomain",
            expirationSeconds = 3600,
        )

        val claimed = checkNotNull(store.tryClaim(studyId, ttl))

        assertEquals("new-role", claimed.config.participantRoleName)
        assertEquals("new-client", claimed.config.clientId)
        assertEquals("https://new.example.com", claimed.config.redirectUri)
        assertEquals("new-subdomain", claimed.config.subdomain)
        assertEquals(3600, claimed.config.expirationSeconds)
    }

    @Test
    fun `tryClaim fails once confirmed plus live reservations reach the cap - before any Keycloak call`() {
        insertConfig(maxParticipants = 1)

        assertNotNull(store.tryClaim(studyId, ttl))
        // The whole point: a second caller is rejected HERE, at the reservation step, never having
        // touched Keycloak at all - unlike checking capacity only after minting an account.
        assertNull(store.tryClaim(studyId, ttl))
    }

    @Test
    fun `tryClaim fails when disabled`() {
        insertConfig(enabled = false)

        assertNull(store.tryClaim(studyId, ttl))
    }

    @Test
    fun `tryClaim fails when no config exists`() {
        assertNull(store.tryClaim(studyId, ttl))
    }

    @Test
    fun `tryClaim ignores expired reservations when counting toward the cap`() {
        insertConfig(maxParticipants = 1)
        val expired = checkNotNull(store.tryClaim(studyId, Duration.ofSeconds(-1)))

        // The expired reservation still physically exists (cleanup hasn't run), but must not block a new
        // claim - only live reservations (plus the confirmed count) count toward the cap.
        val fresh = store.tryClaim(studyId, ttl)

        assertNotNull(fresh)
        assertNotEquals(expired.reservationId, fresh.reservationId)
    }

    @Test
    fun `finalize deletes the reservation and bumps the confirmed count`() {
        insertConfig(maxParticipants = 2)
        val reservationId = checkNotNull(store.tryClaim(studyId, ttl)).reservationId

        assertTrue(store.finalize(reservationId, studyId))

        assertEquals(1, checkNotNull(configStore.findByStudyId(studyId)).currentParticipantCount)
        // A second, later claim now correctly sees the confirmed slot as taken.
        assertNotNull(store.tryClaim(studyId, ttl))
        assertNull(store.tryClaim(studyId, ttl))
    }

    @Test
    fun `finalize fails and does not bump the count once the reservation has expired`() {
        insertConfig(maxParticipants = 2)
        val reservationId = checkNotNull(store.tryClaim(studyId, Duration.ofSeconds(-1))).reservationId

        assertFalse(store.finalize(reservationId, studyId))
        assertEquals(0, checkNotNull(configStore.findByStudyId(studyId)).currentParticipantCount)
    }

    @Test
    fun `finalize fails when the reservation does not exist`() {
        assertFalse(store.finalize("no-such-id", studyId))
    }

    @Test
    fun `findExpired reports only expired reservations, and does not delete them`() {
        insertConfig(maxParticipants = 5)
        val otherStudyId = UUID.randomUUID().stringRepresentation
        configStore.insert(
            StudySelfSignupConfig(
                studyId = otherStudyId,
                shortCode = "FGHJK",
                enabled = true,
                participantRoleName = "participant",
                maxParticipants = 5,
                currentParticipantCount = 0,
                clientId = "client",
                redirectUri = "https://example.com",
                subdomain = null,
                expirationSeconds = 86_400,
            ),
        )
        val expired = checkNotNull(store.tryClaim(studyId, Duration.ofSeconds(-1))).reservationId
        val expiredOther = checkNotNull(store.tryClaim(otherStudyId, Duration.ofSeconds(-1))).reservationId
        val live = checkNotNull(store.tryClaim(studyId, ttl)).reservationId

        val found = store.findExpired()

        assertEquals(setOf(expired, expiredOther), found.map { it.id }.toSet())
        assertEquals(setOf(studyId, otherStudyId), found.map { it.studyId }.toSet())
        // Read-only: the expired reservations are still physically present (and still block finalize, since
        // they're still expired) until something explicitly calls delete().
        assertFalse(store.finalize(expired, studyId))
        assertFalse(store.finalize(expiredOther, otherStudyId))
        assertTrue(store.finalize(live, studyId))
    }

    @Test
    fun `findExpired returns nothing when there is nothing expired`() {
        insertConfig(maxParticipants = 5)
        checkNotNull(store.tryClaim(studyId, ttl))

        assertTrue(store.findExpired().isEmpty())
    }

    @Test
    fun `delete removes only the given reservation, leaving others untouched`() {
        insertConfig(maxParticipants = 5)
        val a = checkNotNull(store.tryClaim(studyId, Duration.ofSeconds(-1))).reservationId
        val b = checkNotNull(store.tryClaim(studyId, Duration.ofSeconds(-1))).reservationId

        store.delete(a)

        assertEquals(listOf(b), store.findExpired().map { it.id })
    }

    @Test
    fun `finalize's confirmed-count bump is rolled back if the enclosing transaction fails`() {
        // Mirrors the crash-safety property the design depends on: finalize must run inside the SAME
        // transaction that persists the participant, so a failure anywhere in it undoes the bump too.
        insertConfig(maxParticipants = 1)
        val reservationId = checkNotNull(store.tryClaim(studyId, ttl)).reservationId
        val transactionTemplate = TransactionTemplate(DataSourceTransactionManager(dataSource))

        assertFailsWith<IllegalStateException> {
            transactionTemplate.executeWithoutResult {
                check(store.finalize(reservationId, studyId)) { "expected the reservation to still be live" }
                error("simulated failure after finalizing, before the participant insert commits")
            }
        }

        assertEquals(0, checkNotNull(configStore.findByStudyId(studyId)).currentParticipantCount)
    }

    @Test
    fun `tryClaim is atomic under a concurrent burst - exactly max_participants succeed`() {
        val maxParticipants = 5
        val concurrentCallers = 50
        insertConfig(maxParticipants = maxParticipants)

        val executor = Executors.newFixedThreadPool(concurrentCallers)
        try {
            val results =
                (1..concurrentCallers)
                    .map { executor.submit<ClaimedReservation?> { store.tryClaim(studyId, ttl) } }
                    .map { it.get() }

            assertEquals(maxParticipants, results.count { it != null })
        } finally {
            executor.shutdown()
        }
    }

    companion object {
        @Container
        @JvmStatic
        val postgres = PostgreSQLContainer("postgres:17-alpine")
    }
}
