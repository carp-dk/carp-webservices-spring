package dk.cachet.carp.webservices.admin.repository

import dk.cachet.carp.webservices.admin.domain.Announcement
import dk.cachet.carp.webservices.admin.domain.AnnouncementSeverity
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * JdbcTemplate-backed access to the singleton `system_announcement` table (see V13). At most one row
 * exists (`id = 1`); no row means no active announcement.
 *
 * `updated_at` is `timestamp without time zone` holding UTC wall-clock time, the same convention Hibernate
 * uses for the rest of the schema, so it is written and read as a UTC [LocalDateTime].
 */
@Component
class AnnouncementStore(
    private val jdbcTemplate: JdbcTemplate,
) {
    private val rowMapper =
        RowMapper { rs, _ ->
            Announcement(
                message = rs.getString("message"),
                severity = AnnouncementSeverity.valueOf(rs.getString("severity")),
                updatedAt = rs.getObject("updated_at", LocalDateTime::class.java).toInstant(ZoneOffset.UTC),
                updatedBy = rs.getString("updated_by"),
            )
        }

    fun find(): Announcement? =
        jdbcTemplate
            .query("SELECT * FROM system_announcement WHERE id = 1", rowMapper)
            .firstOrNull()

    fun upsert(
        message: String,
        severity: AnnouncementSeverity,
        updatedAt: Instant,
        updatedBy: String,
    ) {
        jdbcTemplate.update(
            """
            INSERT INTO system_announcement (id, message, severity, updated_at, updated_by)
            VALUES (1, ?, ?, ?, ?)
            ON CONFLICT (id) DO UPDATE SET
                message = EXCLUDED.message,
                severity = EXCLUDED.severity,
                updated_at = EXCLUDED.updated_at,
                updated_by = EXCLUDED.updated_by
            """.trimIndent(),
            message,
            severity.name,
            LocalDateTime.ofInstant(updatedAt, ZoneOffset.UTC),
            updatedBy,
        )
    }

    /** Clears the announcement. Idempotent. */
    fun delete() {
        jdbcTemplate.update("DELETE FROM system_announcement WHERE id = 1")
    }
}
