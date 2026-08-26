package dk.cachet.carp.webservices.selfsignup.repository

import dk.cachet.carp.common.application.UUID
import dk.cachet.carp.webservices.selfsignup.domain.StudySelfSignupConfig
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.core.io.ClassPathResource
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.jdbc.datasource.init.ScriptUtils
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Integration coverage for [StudySelfSignupStore] against a real database: the enable/update/end
 * bookkeeping. The atomic capacity-reservation lifecycle (claim/finalize/release) lives in
 * [SelfSignupReservationStore] and is covered there.
 */
@Testcontainers(disabledWithoutDocker = true)
class StudySelfSignupStorePostgresTest {
    private lateinit var jdbc: JdbcTemplate
    private lateinit var store: StudySelfSignupStore
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
        store = StudySelfSignupStore(jdbc)
        jdbc.execute("DROP SCHEMA public CASCADE; CREATE SCHEMA public")
        dataSource.connection.use { connection ->
            ScriptUtils.executeSqlScript(
                connection,
                ClassPathResource("db/migration/V10__add_study_self_signup.sql"),
            )
        }
    }

    private fun config(
        studyId: String = this.studyId,
        shortCode: String = "ABCDE",
        enabled: Boolean = true,
        maxParticipants: Int = 3,
    ) = StudySelfSignupConfig(
        studyId = studyId,
        shortCode = shortCode,
        enabled = enabled,
        participantRoleName = "participant",
        maxParticipants = maxParticipants,
        currentParticipantCount = 0,
        clientId = "client",
        redirectUri = "https://example.com",
        subdomain = null,
        expirationSeconds = 86_400,
    )

    /** Directly sets the confirmed count, standing in for what SelfSignupReservationStore.finalize does. */
    private fun setConfirmedCount(count: Int) {
        jdbc.update("UPDATE study_self_signup SET current_participant_count = ? WHERE study_id = ?", count, studyId)
    }

    @Test
    fun `insert creates a row that findByStudyId and findByShortCode can both find`() {
        assertTrue(store.insert(config()))

        assertEquals(studyId, store.findByStudyId(studyId)?.studyId)
        assertEquals(studyId, store.findByShortCode("ABCDE")?.studyId)
    }

    @Test
    fun `insert fails on a duplicate study_id`() {
        store.insert(config())

        assertFalse(store.insert(config(shortCode = "FGHJK")))
    }

    @Test
    fun `insert fails on a duplicate short_code for a different study`() {
        store.insert(config())

        val other = UUID.randomUUID().stringRepresentation
        assertFalse(store.insert(config(studyId = other, shortCode = "ABCDE")))
    }

    @Test
    fun `findByStudyId and findByShortCode return null when nothing matches`() {
        assertNull(store.findByStudyId(studyId))
        assertNull(store.findByShortCode("ZZZZZ"))
    }

    @Test
    fun `update changes configuration but never the short code or current count`() {
        store.insert(config(shortCode = "ABCDE", maxParticipants = 3))
        setConfirmedCount(1)

        store.update(
            studyId = studyId,
            enabled = false,
            participantRoleName = "other-role",
            maxParticipants = 10,
            clientId = "other-client",
            redirectUri = "https://other.example.com",
            subdomain = "sub",
            expirationSeconds = 3600,
        )

        val updated = checkNotNull(store.findByStudyId(studyId))
        assertEquals("ABCDE", updated.shortCode)
        assertEquals(1, updated.currentParticipantCount)
        assertFalse(updated.enabled)
        assertEquals("other-role", updated.participantRoleName)
        assertEquals(10, updated.maxParticipants)
    }

    @Test
    fun `setEnabled toggles enabled without touching the short code or count`() {
        store.insert(config())
        setConfirmedCount(1)

        store.setEnabled(studyId, enabled = false)

        val disabled = checkNotNull(store.findByStudyId(studyId))
        assertFalse(disabled.enabled)
        assertEquals(1, disabled.currentParticipantCount)
        assertEquals("ABCDE", disabled.shortCode)

        store.setEnabled(studyId, enabled = true)
        assertTrue(checkNotNull(store.findByStudyId(studyId)).enabled)
    }

    companion object {
        @Container
        @JvmStatic
        val postgres = PostgreSQLContainer("postgres:17-alpine")
    }
}
