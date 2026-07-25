package dk.cachet.carp.webservices.migration

import dk.cachet.carp.studies.domain.users.RecruitmentSnapshot
import dk.cachet.carp.webservices.common.input.WS_JSON
import dk.cachet.carp.webservices.study.domain.normalization.NormalizedRecruitment
import dk.cachet.carp.webservices.study.domain.normalization.RecruitmentNormalizationStore
import dk.cachet.carp.webservices.study.domain.normalization.RecruitmentNormalizer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import org.apache.logging.log4j.LogManager
import org.flywaydb.core.Flyway
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.core.env.Environment
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate
import java.util.concurrent.TimeUnit

/**
 * Backfills the normalized recruitment tables from `recruitments.snapshot` (see
 * docs/participant-group-normalization.md). Mirrors [Core13DataMigrationRunner]: JdbcTemplate +
 * TransactionTemplate, PK-paginated resumable batches, small per-batch transactions, and the shared
 * `core_data_migration_*` tracking tables — with `migration_name = recruitment-participant-normalization`.
 *
 * Modes (`carp.recruitment-normalization.mode`): `inventory`, `dry-run`, `apply`, `verify`.
 * Runs only in a non-web process (`--spring.main.web-application-type=none`); the bean exists only
 * when the mode property is set.
 *
 * Skip policy: recruitments whose snapshot cannot be decoded by [WS_JSON] (e.g. the pre-existing
 * stale `type` discriminator rows) and NULL snapshots are recorded as `SKIPPED` and excluded from the
 * failure count, so they do not block completion. Genuine errors (write failures) are real failures.
 */
@Component
@ConditionalOnProperty(name = ["carp.recruitment-normalization.mode"])
class RecruitmentNormalizationRunner(
    private val jdbcTemplate: JdbcTemplate,
    private val transactionTemplate: TransactionTemplate,
    private val store: RecruitmentNormalizationStore,
    private val environment: Environment,
    private val applicationContext: ConfigurableApplicationContext,
) : ApplicationRunner {
    @Suppress("TooGenericExceptionCaught")
    override fun run(args: ApplicationArguments) {
        val options = Options.from(environment)
        ensureSchema()
        val runId = startOrResumeRun(options)
        LOGGER.info("Starting recruitment normalization run {} in {} mode.", runId, options.mode)

        try {
            when (options.mode) {
                Mode.INVENTORY -> recordInventory(runId)
                Mode.DRY_RUN, Mode.APPLY, Mode.VERIFY -> processRecruitments(runId, options)
            }
            completeRun(runId)
            SpringApplication.exit(applicationContext)
        } catch (exception: Exception) {
            failRun(runId, exception)
            throw exception
        }
    }

    private fun ensureSchema() {
        val targetExists =
            jdbcTemplate.queryForObject(
                "SELECT to_regclass('public.recruitment_participants') IS NOT NULL",
                Boolean::class.java,
            ) == true
        if (targetExists) return

        val configuredFlyway = applicationContext.getBeanProvider(Flyway::class.java).ifAvailable
        if (configuredFlyway != null) {
            configuredFlyway.migrate()
            return
        }
        val dataSource = requireNotNull(jdbcTemplate.dataSource) { "JdbcTemplate dataSource is required." }
        Flyway.configure().dataSource(dataSource).load().migrate()
    }

    private fun processRecruitments(
        runId: Long,
        options: Options,
    ) {
        var progress = loadProgress(runId)
        while (true) {
            val outcome =
                transactionTemplate.execute {
                    val rows = loadBatch(progress.lastRecruitmentId, options.batchSize)
                    if (rows.isEmpty()) {
                        RecruitmentBatchOutcome(progress, done = true)
                    } else {
                        RecruitmentBatchOutcome(processBatch(runId, rows, progress, options), done = false)
                    }
                }!!
            progress = outcome.progress
            if (outcome.done) break
            pause(options.rateLimitMs)
        }
    }

    private fun processBatch(
        runId: Long,
        rows: List<RecruitmentRow>,
        initialProgress: Progress,
        options: Options,
    ): Progress {
        var progress = initialProgress
        rows.forEach { row ->
            progress = progress.process(row.id)
            val startedAt = System.nanoTime()
            try {
                val outcome = processRow(row, options.mode)
                if (outcome == "MIGRATED" || outcome == "WOULD_MIGRATE") progress = progress.migrated()
                recordRowOutcome(runId, row, outcome, startedAt, null)
            } catch (error: DecodeSkip) {
                recordRowOutcome(runId, row, "SKIPPED", startedAt, error.reason)
            } catch (@Suppress("TooGenericExceptionCaught") error: Exception) {
                recordFailure(runId, row, error)
                recordRowOutcome(runId, row, "FAILED", startedAt, error.message)
                progress = progress.failed()
            }
        }
        updateProgress(runId, progress)
        return progress
    }

    /** Returns the row outcome; throws [DecodeSkip] for NULL/undecodable snapshots (out-of-scope skip). */
    private fun processRow(
        row: RecruitmentRow,
        mode: Mode,
    ): String {
        val json = row.snapshot ?: throw DecodeSkip("null snapshot")
        val snapshot = decodeOrSkip(json)
        val normalized = RecruitmentNormalizer.decompose(snapshot)
        return when (mode) {
            Mode.APPLY -> {
                store.replace(row.id, normalized)
                "MIGRATED"
            }
            Mode.DRY_RUN -> "WOULD_MIGRATE"
            Mode.VERIFY ->
                if (verifyPersisted(row.id, json, normalized)) "VALIDATED" else error("reconstruction mismatch")
            Mode.INVENTORY -> "UNCHANGED"
        }
    }

    /** Reconstructs from the persisted rows + envelope and checks it re-serializes to the stored blob. */
    private fun verifyPersisted(
        recruitmentId: Int,
        json: String,
        decomposed: NormalizedRecruitment,
    ): Boolean {
        val dbRows = store.readRows(recruitmentId)
        val reconstructed =
            RecruitmentNormalizer.reconstruct(
                decomposed.copy(participants = dbRows.participants, groups = dbRows.groups, members = dbRows.members),
            )
        val reEncoded = WS_JSON.encodeToString(RecruitmentSnapshot.serializer(), reconstructed)
        return canonical(reEncoded) == canonical(json)
    }

    private fun decodeOrSkip(json: String): RecruitmentSnapshot =
        try {
            WS_JSON.decodeFromString(RecruitmentSnapshot.serializer(), json)
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            throw DecodeSkip(e.message?.take(MAX_ERROR_LENGTH) ?: "undecodable snapshot", e)
        }

    private fun loadBatch(
        afterId: Int,
        batchSize: Int,
    ): List<RecruitmentRow> =
        // NULL snapshots are included so they get a SKIPPED row for auditability (handled in processRow).
        jdbcTemplate.query(
            "SELECT id, snapshot::text, pg_column_size(snapshot) AS jsonb_size FROM recruitments " +
                "WHERE id > ? ORDER BY id LIMIT ? FOR UPDATE",
            { rs, _ -> RecruitmentRow(rs.getInt("id"), rs.getString("snapshot"), rs.getLong("jsonb_size")) },
            afterId,
            batchSize,
        )

    private fun recordInventory(runId: Long) {
        val total = count("SELECT COUNT(*) FROM recruitments")
        val nullSnapshots = count("SELECT COUNT(*) FROM recruitments WHERE snapshot IS NULL")
        jdbcTemplate.update(
            "UPDATE core_data_migration_runs SET report = jsonb_build_object(" +
                "'recruitmentCount', ?::bigint, 'nullSnapshotCount', ?::bigint) WHERE id = ?",
            total,
            nullSnapshots,
            runId,
        )
        LOGGER.info("Recruitment normalization inventory: total={}, nullSnapshots={}.", total, nullSnapshots)
    }

    private fun startOrResumeRun(options: Options): Long {
        if (options.resume) {
            jdbcTemplate.query(
                "SELECT id FROM core_data_migration_runs WHERE migration_name = ? AND mode = ? " +
                    "AND status = 'RUNNING' ORDER BY id DESC LIMIT 1",
                { rs, _ -> rs.getLong("id") },
                MIGRATION_NAME,
                options.mode.name,
            ).firstOrNull()?.let { runId ->
                jdbcTemplate.update(
                    "UPDATE core_data_migration_runs SET status = 'RUNNING', completed_at = NULL WHERE id = ?",
                    runId,
                )
                return runId
            }
        }
        return checkNotNull(
            jdbcTemplate.queryForObject(
                "INSERT INTO core_data_migration_runs (migration_name, mode, status) " +
                    "VALUES (?, ?, 'RUNNING') RETURNING id",
                Long::class.java,
                MIGRATION_NAME,
                options.mode.name,
            ),
        )
    }

    private fun loadProgress(runId: Long): Progress =
        jdbcTemplate.queryForObject(
            "SELECT last_recruitment_id, processed_count, migrated_count, failure_count " +
                "FROM core_data_migration_runs WHERE id = ?",
            { rs, _ ->
                Progress(
                    rs.getInt("last_recruitment_id"),
                    rs.getLong("processed_count"),
                    rs.getLong("migrated_count"),
                    rs.getLong("failure_count"),
                )
            },
            runId,
        )!!

    private fun updateProgress(
        runId: Long,
        progress: Progress,
    ) {
        jdbcTemplate.update(
            "UPDATE core_data_migration_runs SET last_recruitment_id = ?, processed_count = ?, " +
                "migrated_count = ?, failure_count = ? WHERE id = ?",
            progress.lastRecruitmentId,
            progress.processed,
            progress.migrated,
            progress.failures,
            runId,
        )
    }

    private fun recordFailure(
        runId: Long,
        row: RecruitmentRow,
        error: Throwable,
    ) {
        jdbcTemplate.update(
            "INSERT INTO core_data_migration_failures (run_id, table_name, row_id, jsonb_size, error) " +
                "VALUES (?, 'recruitments', ?, ?, ?) ON CONFLICT (run_id, table_name, row_id) DO UPDATE SET " +
                "jsonb_size = EXCLUDED.jsonb_size, error = EXCLUDED.error, created_at = CURRENT_TIMESTAMP",
            runId,
            row.id,
            row.jsonbSize,
            error.message ?: error::class.qualifiedName.orEmpty(),
        )
    }

    private fun recordRowOutcome(
        runId: Long,
        row: RecruitmentRow,
        outcome: String,
        startedAt: Long,
        error: String?,
    ) {
        jdbcTemplate.update(
            "INSERT INTO core_data_migration_rows " +
                "(run_id, table_name, row_id, outcome, duration_ms, jsonb_size, error) " +
                "VALUES (?, 'recruitments', ?, ?, ?, ?, ?) " +
                "ON CONFLICT (run_id, table_name, row_id) DO UPDATE SET outcome = EXCLUDED.outcome, " +
                "duration_ms = EXCLUDED.duration_ms, jsonb_size = EXCLUDED.jsonb_size, error = EXCLUDED.error, " +
                "created_at = CURRENT_TIMESTAMP",
            runId,
            row.id,
            outcome,
            TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt),
            row.jsonbSize,
            error,
        )
    }

    private fun completeRun(runId: Long) {
        val progress = loadProgress(runId)
        val skipped =
            count("SELECT COUNT(*) FROM core_data_migration_rows WHERE run_id = $runId AND outcome = 'SKIPPED'")
        check(progress.failures == 0L) { "Normalization completed with ${progress.failures} failed rows." }
        jdbcTemplate.update(
            "UPDATE core_data_migration_runs SET status = 'COMPLETED', completed_at = CURRENT_TIMESTAMP, " +
                "report = COALESCE(report, '{}'::jsonb) || jsonb_build_object('skippedCount', ?::bigint) WHERE id = ?",
            skipped,
            runId,
        )
        LOGGER.info(
            "Completed recruitment normalization run {}: processed={}, migrated={}, skipped={}.",
            runId,
            progress.processed,
            progress.migrated,
            skipped,
        )
    }

    private fun failRun(
        runId: Long,
        error: Exception,
    ) {
        jdbcTemplate.update(
            "UPDATE core_data_migration_runs SET status = 'FAILED', completed_at = CURRENT_TIMESTAMP, " +
                "report = COALESCE(report, '{}'::jsonb) || jsonb_build_object('error', ?) WHERE id = ?",
            error.message ?: error::class.qualifiedName.orEmpty(),
            runId,
        )
    }

    private fun count(sql: String): Long = checkNotNull(jdbcTemplate.queryForObject(sql, Long::class.java))

    private fun pause(milliseconds: Long) {
        if (milliseconds > 0) TimeUnit.MILLISECONDS.sleep(milliseconds)
    }

    private fun canonical(json: String): JsonElement = canonicalize(WS_JSON.parseToJsonElement(json))

    private fun canonicalize(element: JsonElement): JsonElement =
        when (element) {
            // Sort keys too: the stored blob is Postgres jsonb (keys ordered by length) while the
            // reconstructed side is kotlinx (declaration order). Only key-sorted objects have a
            // source-independent toString(), which the array sort below relies on.
            is JsonObject ->
                JsonObject(element.entries.sortedBy { it.key }.associate { it.key to canonicalize(it.value) })
            is JsonArray -> JsonArray(element.map(::canonicalize).sortedBy { it.toString() })
            else -> element
        }

    companion object {
        private val LOGGER = LogManager.getLogger()
        private const val MIGRATION_NAME = "recruitment-participant-normalization"
        private const val MAX_ERROR_LENGTH = 200
    }
}

private class DecodeSkip(val reason: String, cause: Throwable? = null) : RuntimeException(reason, cause)

private data class RecruitmentRow(
    val id: Int,
    val snapshot: String?,
    val jsonbSize: Long,
)

private data class RecruitmentBatchOutcome(
    val progress: Progress,
    val done: Boolean,
)

private data class Progress(
    val lastRecruitmentId: Int,
    val processed: Long,
    val migrated: Long,
    val failures: Long,
) {
    fun process(id: Int) = copy(lastRecruitmentId = id, processed = processed + 1)

    fun migrated() = copy(migrated = migrated + 1)

    fun failed() = copy(failures = failures + 1)
}

private data class Options(
    val mode: Mode,
    val batchSize: Int,
    val rateLimitMs: Long,
    val resume: Boolean,
) {
    companion object {
        private const val DEFAULT_BATCH_SIZE = 25

        fun from(environment: Environment): Options {
            require(environment.getProperty("spring.main.web-application-type") == "none") {
                "Recruitment normalization must run with --spring.main.web-application-type=none."
            }
            val mode =
                Mode.valueOf(
                    environment.getRequiredProperty("carp.recruitment-normalization.mode")
                        .replace('-', '_').uppercase(),
                )
            val batchSize =
                environment.getProperty(
                    "carp.recruitment-normalization.batch-size",
                    Int::class.java,
                    DEFAULT_BATCH_SIZE,
                )
            val rateLimitMs =
                environment.getProperty("carp.recruitment-normalization.rate-limit-ms", Long::class.java, 0L)
            val resume = environment.getProperty("carp.recruitment-normalization.resume", Boolean::class.java, true)
            require(batchSize > 0) { "Batch size must be positive." }
            require(rateLimitMs >= 0) { "Rate limit must not be negative." }
            return Options(mode, batchSize, rateLimitMs, resume)
        }
    }
}

private enum class Mode {
    INVENTORY,
    DRY_RUN,
    APPLY,
    VERIFY,
}
