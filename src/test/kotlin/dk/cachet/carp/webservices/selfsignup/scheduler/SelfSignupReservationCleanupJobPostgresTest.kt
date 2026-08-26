package dk.cachet.carp.webservices.selfsignup.scheduler

import dk.cachet.carp.common.application.UUID
import dk.cachet.carp.webservices.selfsignup.domain.StudySelfSignupConfig
import dk.cachet.carp.webservices.selfsignup.repository.SelfSignupReservationStore
import dk.cachet.carp.webservices.selfsignup.repository.StudySelfSignupStore
import dk.cachet.carp.webservices.study.repository.AnonymousAccountCleanupStore
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.core.io.ClassPathResource
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.jdbc.datasource.init.ScriptUtils
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * End-to-end coverage of [SelfSignupReservationCleanupJob] against real Postgres, using the real
 * [AnonymousAccountCleanupStore] alongside the self-signup tables (both migrations applied together) -
 * this is what actually proves the record-before-delete ordering survives a genuine DB-level failure and a
 * simulated crash between the two writes, not just what a mocked unit test asserts was called.
 */
@Testcontainers(disabledWithoutDocker = true)
class SelfSignupReservationCleanupJobPostgresTest {
    private lateinit var jdbc: JdbcTemplate
    private lateinit var configStore: StudySelfSignupStore
    private lateinit var reservationStore: SelfSignupReservationStore
    private lateinit var cleanupStore: AnonymousAccountCleanupStore
    private lateinit var job: SelfSignupReservationCleanupJob
    private val studyId = UUID.randomUUID().stringRepresentation

    @BeforeEach
    fun prepareDatabase() {
        val dataSource =
            DriverManagerDataSource().apply {
                setDriverClassName(postgres.driverClassName)
                url = postgres.jdbcUrl
                username = postgres.username
                password = postgres.password
            }
        jdbc = JdbcTemplate(dataSource)
        configStore = StudySelfSignupStore(jdbc)
        reservationStore = SelfSignupReservationStore(jdbc, DataSourceTransactionManager(dataSource))
        cleanupStore = AnonymousAccountCleanupStore(jdbc)
        job = SelfSignupReservationCleanupJob(reservationStore, configStore, cleanupStore)

        jdbc.execute("DROP SCHEMA public CASCADE; CREATE SCHEMA public")
        dataSource.connection.use { connection ->
            ScriptUtils.executeSqlScript(
                connection,
                ClassPathResource("db/migration/V9__add_anonymous_account_cleanup.sql"),
            )
            ScriptUtils.executeSqlScript(
                connection,
                ClassPathResource("db/migration/V10__add_study_self_signup.sql"),
            )
            ScriptUtils.executeSqlScript(
                connection,
                ClassPathResource("db/migration/V11__add_self_signup_reservation.sql"),
            )
        }
        configStore.insert(
            StudySelfSignupConfig(
                studyId = studyId,
                shortCode = "ABCDE",
                enabled = true,
                participantRoleName = "participant",
                maxParticipants = 5,
                currentParticipantCount = 0,
                clientId = "client",
                redirectUri = "https://example.com",
                subdomain = null,
                expirationSeconds = 3600,
            ),
        )
    }

    private fun ledgerRowExists(): Boolean =
        checkNotNull(
            jdbcOrNull("SELECT COUNT(*) FROM anonymous_account_cleanup WHERE study_id = ?"),
        ) > 0

    private fun jdbcOrNull(sql: String): Long? = jdbc.queryForObject(sql, Long::class.java, studyId)

    @Test
    fun `reconciles an expired reservation into the cleanup ledger and then deletes it`() {
        val reservationId = checkNotNull(reservationStore.tryClaim(studyId, Duration.ofSeconds(-1))).reservationId

        job.cleanup()

        assertTrue(ledgerRowExists())
        // The reservation is gone - findExpired (and thus a re-run) sees nothing left to reconcile.
        assertTrue(reservationStore.findExpired().none { it.id == reservationId })
    }

    @Test
    fun `a ledger write failure leaves the reservation intact for the next sweep, instead of losing it`() {
        val reservationId = checkNotNull(reservationStore.tryClaim(studyId, Duration.ofSeconds(-1))).reservationId
        // Force the ledger write to fail with a genuine DB-level error, standing in for "the process
        // crashed mid-write" or any other reason the write couldn't complete.
        jdbc.execute("DROP TABLE anonymous_account_cleanup")

        job.cleanup()

        // The reservation - the durable evidence this signup attempt happened - was NOT deleted, because
        // the ledger write it depended on never succeeded. If delete had run anyway (the bug this fix
        // closes), this assertion would fail and the evidence would be gone for good.
        assertEquals(1, reservationStore.findExpired().count { it.id == reservationId })
    }

    @Test
    fun `a crash between recording and deleting is safe - the schedule already exists, so a retry just converges`() {
        val reservationId = checkNotNull(reservationStore.tryClaim(studyId, Duration.ofSeconds(-1))).reservationId

        // Simulates the process dying after the ledger write commits but before the delete runs, by doing
        // exactly what the job does up to that point and stopping there.
        cleanupStore.upsert(studyId, Instant.now(), 1)
        assertTrue(ledgerRowExists())
        assertNotNull(reservationStore.findExpired().firstOrNull { it.id == reservationId })

        // The next scheduled run (standing in for the process restarting) retries and converges cleanly:
        // a second ledger write is harmless (cumulative, see AnonymousAccountCleanupStore.upsert), and the
        // reservation is now finally deleted.
        job.cleanup()

        assertNull(reservationStore.findExpired().firstOrNull { it.id == reservationId })
    }

    companion object {
        @Container
        @JvmStatic
        val postgres = PostgreSQLContainer("postgres:17-alpine")
    }
}
