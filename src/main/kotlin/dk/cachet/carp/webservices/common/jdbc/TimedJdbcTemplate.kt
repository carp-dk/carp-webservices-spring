package dk.cachet.carp.webservices.common.jdbc

import org.springframework.dao.DataAccessException
import org.springframework.jdbc.core.JdbcTemplate
import java.sql.SQLException
import java.time.Duration

/**
 * Runs heavy, read-only overview queries with a statement timeout, so a very large study or instance fails
 * fast with [StatementTimeoutException] instead of holding one of the shared pool's connections.
 *
 * Wraps a private copy of [jdbcTemplate], so the timeout doesn't leak into the shared `JdbcTemplate` bean.
 */
class TimedJdbcTemplate(
    jdbcTemplate: JdbcTemplate,
    val timeout: Duration,
) {
    companion object {
        // Postgres SQLSTATE for a statement cancelled by its timeout ("canceling statement due to user request").
        private const val QUERY_CANCELED = "57014"
    }

    private val jdbc =
        JdbcTemplate(requireNotNull(jdbcTemplate.dataSource) { "JdbcTemplate has no DataSource." }).apply {
            queryTimeout = timeout.toSeconds().toInt()
        }

    /** Runs [query]; if the database cancels it for running past [timeout], throws [StatementTimeoutException]. */
    fun <T> run(
        description: String,
        query: (JdbcTemplate) -> T,
    ): T =
        try {
            query(jdbc)
        } catch (e: DataAccessException) {
            if ((e.mostSpecificCause as? SQLException)?.sqlState != QUERY_CANCELED) throw e
            throw StatementTimeoutException(
                "$description took longer than ${timeout.toSeconds()}s and was cancelled.",
                e,
            )
        }
}

/** A query hit its statement timeout and was cancelled by the database. */
class StatementTimeoutException(
    message: String,
    cause: Throwable,
) : RuntimeException(message, cause)
