package dk.cachet.carp.webservices.selfsignup.repository

import dk.cachet.carp.webservices.selfsignup.domain.StudySelfSignupConfig
import org.springframework.dao.DuplicateKeyException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Component
import java.sql.Timestamp
import java.time.Instant

/**
 * JdbcTemplate-backed access to the `study_self_signup` table (one row per study). Follows the same
 * raw-SQL convention as [dk.cachet.carp.webservices.study.repository.AnonymousAccountCleanupStore] rather
 * than JPA: the operation that matters most - "reserve a signup slot, atomically, under a burst of
 * concurrent callers" - is naturally a single UPDATE with a WHERE guard, letting Postgres's row lock
 * serialize concurrent requests for the same study with no app-level optimistic-lock retry loop.
 */
@Component
class StudySelfSignupStore(
    private val jdbcTemplate: JdbcTemplate,
) {
    companion object {
        // internal, not private: SelfSignupReservationStore.tryClaim needs to map the exact same row
        // shape when it locks and re-reads this table's current configuration - one mapper, so the two
        // can never drift apart on which columns exist or how they're read.
        internal val rowMapper =
            RowMapper<StudySelfSignupConfig> { rs, _ ->
                StudySelfSignupConfig(
                    studyId = rs.getString("study_id"),
                    shortCode = rs.getString("short_code"),
                    enabled = rs.getBoolean("enabled"),
                    participantRoleName = rs.getString("participant_role_name"),
                    maxParticipants = rs.getInt("max_participants"),
                    currentParticipantCount = rs.getInt("current_participant_count"),
                    clientId = rs.getString("client_id"),
                    redirectUri = rs.getString("redirect_uri"),
                    subdomain = rs.getString("subdomain"),
                    expirationSeconds = rs.getLong("expiration_seconds"),
                )
            }
    }

    fun findByStudyId(studyId: String): StudySelfSignupConfig? =
        jdbcTemplate
            .query(
                "SELECT * FROM study_self_signup WHERE study_id = ?",
                rowMapper,
                studyId,
            ).firstOrNull()

    /** [shortCode] must already be normalized (trimmed/uppercased) by the caller. */
    fun findByShortCode(shortCode: String): StudySelfSignupConfig? =
        jdbcTemplate
            .query(
                "SELECT * FROM study_self_signup WHERE short_code = ?",
                rowMapper,
                shortCode,
            ).firstOrNull()

    /**
     * First-time enable for a study with no existing row. Returns `false` on a unique-constraint violation
     * (either [config]'s study_id or short_code already exists) rather than throwing, so the caller can
     * decide how to recover: a study_id collision means a concurrent enable call won the race (re-check
     * with [findByStudyId] and fall back to [update]); a short_code collision means the generated code is
     * already taken by a different study (retry with a freshly generated code).
     */
    fun insert(
        config: StudySelfSignupConfig,
        now: Instant = Instant.now(),
    ): Boolean =
        try {
            jdbcTemplate.update(
                """
                INSERT INTO study_self_signup (
                    study_id, short_code, enabled, participant_role_name, max_participants,
                    current_participant_count, client_id, redirect_uri, subdomain, expiration_seconds,
                    created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, 0, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                config.studyId,
                config.shortCode,
                config.enabled,
                config.participantRoleName,
                config.maxParticipants,
                config.clientId,
                config.redirectUri,
                config.subdomain,
                config.expirationSeconds,
                Timestamp.from(now),
                Timestamp.from(now),
            )
            true
        } catch (
            @Suppress("SwallowedException") e: DuplicateKeyException,
        ) {
            // Expected control flow: a unique-constraint violation on study_id or short_code, which the
            // caller recovers from (see the method doc). Nothing to log - it's not an error condition.
            false
        }

    /**
     * Re-enable / reconfigure an existing row: [enabled], the participant role, the Keycloak account
     * parameters, and [maxParticipants] (which may be raised or lowered - see the migration's comment on
     * why there's no CHECK constraint against [maxParticipants] versus the current count). Never touches
     * `current_participant_count` or `short_code`.
     */
    @Suppress("LongParameterList")
    fun update(
        studyId: String,
        enabled: Boolean,
        participantRoleName: String,
        maxParticipants: Int,
        clientId: String,
        redirectUri: String?,
        subdomain: String?,
        expirationSeconds: Long,
        now: Instant = Instant.now(),
    ): Int =
        jdbcTemplate.update(
            """
            UPDATE study_self_signup
            SET enabled = ?, participant_role_name = ?, max_participants = ?, client_id = ?,
                redirect_uri = ?, subdomain = ?, expiration_seconds = ?, updated_at = ?
            WHERE study_id = ?
            """.trimIndent(),
            enabled,
            participantRoleName,
            maxParticipants,
            clientId,
            redirectUri,
            subdomain,
            expirationSeconds,
            Timestamp.from(now),
            studyId,
        )

    /** Flip [studyId]'s config to disabled, keeping the short code and cumulative count intact. */
    fun setEnabled(
        studyId: String,
        enabled: Boolean,
        now: Instant = Instant.now(),
    ): Int =
        jdbcTemplate.update(
            "UPDATE study_self_signup SET enabled = ?, updated_at = ? WHERE study_id = ?",
            enabled,
            Timestamp.from(now),
            studyId,
        )

    // The atomic capacity check/reserve/finalize lifecycle lives in SelfSignupReservationStore, which
    // needs to touch both self_signup_reservation and this table's current_participant_count together -
    // see its class doc for why capacity is claimed via a durable, TTL-bounded reservation BEFORE calling
    // Keycloak, rather than by incrementing this counter directly.
}
