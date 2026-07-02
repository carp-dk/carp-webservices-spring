package dk.cachet.carp.webservices.migration

import org.apache.logging.log4j.LogManager
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.core.env.Environment
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import java.time.Instant
import java.util.concurrent.TimeUnit

@Component
@ConditionalOnProperty(name = ["carp.core-1-3-migration.mode"])
class Core13DataMigrationRunner(
    private val jdbcTemplate: JdbcTemplate,
    private val transactionTemplate: TransactionTemplate,
    private val transformer: Core13SnapshotTransformer,
    private val objectMapper: ObjectMapper,
    private val environment: Environment,
    private val applicationContext: ConfigurableApplicationContext,
) : ApplicationRunner {
    @Suppress("TooGenericExceptionCaught")
    override fun run(args: ApplicationArguments) {
        val options = MigrationOptions.from(environment)
        val runId = startOrResumeRun(options)
        LOGGER.info("Starting CARP Core 1.3 data migration run {} in {} mode.", runId, options.mode)

        try {
            when (options.mode) {
                MigrationMode.INVENTORY -> recordInventory(runId)
                MigrationMode.DRY_RUN,
                MigrationMode.APPLY,
                -> migrate(runId, options)
                MigrationMode.VERIFY -> verify(runId, options)
            }
            completeRun(runId)
            SpringApplication.exit(applicationContext)
        } catch (exception: Exception) {
            failRun(runId, exception)
            throw exception
        }
    }

    private fun migrate(
        runId: Long,
        options: MigrationOptions,
    ) {
        var progress = loadProgress(runId)
        progress = processDeployments(runId, progress, options, legacyOnly = true)
        processRecruitments(runId, progress, options, legacyOnly = true)
    }

    private fun verify(
        runId: Long,
        options: MigrationOptions,
    ) {
        val legacyDeployments = count("SELECT COUNT(*) FROM deployments WHERE $LEGACY_DEPLOYMENT_PREDICATE")
        val legacyRecruitments =
            count(
                "SELECT COUNT(*) FROM recruitments " +
                    "WHERE jsonb_path_exists(snapshot, '$.participantGroups.*._participantIds')",
            )
        check(legacyDeployments == 0L) { "$legacyDeployments legacy deployment snapshots remain." }
        check(legacyRecruitments == 0L) { "$legacyRecruitments legacy recruitment snapshots remain." }

        var progress = loadProgress(runId)
        progress = processDeployments(runId, progress, options, legacyOnly = false)
        processRecruitments(runId, progress, options, legacyOnly = false)
    }

    private fun processDeployments(
        runId: Long,
        initialProgress: MigrationProgress,
        options: MigrationOptions,
        legacyOnly: Boolean,
    ): MigrationProgress {
        var progress = initialProgress
        while (true) {
            val outcome =
                transactionTemplate.execute {
                    val rows = loadDeploymentBatch(progress.lastDeploymentId, options.batchSize, legacyOnly)
                    if (rows.isEmpty()) {
                        BatchOutcome(progress, done = true)
                    } else {
                        BatchOutcome(
                            processBatch(runId, "deployments", rows, progress, options) { row ->
                                if (legacyOnly) {
                                    transformer.migrateDeployment(row.snapshot, row.updatedAt)
                                } else {
                                    transformer.validateDeployment(row.snapshot)
                                }
                            },
                            done = false,
                        )
                    }
                }
            progress = outcome.progress
            if (outcome.done) return progress
            pause(options.rateLimitMs)
        }
    }

    private fun processRecruitments(
        runId: Long,
        initialProgress: MigrationProgress,
        options: MigrationOptions,
        legacyOnly: Boolean,
    ): MigrationProgress {
        var progress = initialProgress
        while (true) {
            val outcome =
                transactionTemplate.execute {
                    val rows = loadRecruitmentBatch(progress.lastRecruitmentId, options.batchSize, legacyOnly)
                    if (rows.isEmpty()) {
                        BatchOutcome(progress, done = true)
                    } else {
                        BatchOutcome(
                            processBatch(runId, "recruitments", rows, progress, options) { row ->
                                if (legacyOnly) {
                                    transformer.migrateRecruitment(row.snapshot)
                                } else {
                                    transformer.validateRecruitment(row.snapshot)
                                }
                            },
                            done = false,
                        )
                    }
                }
            progress = outcome.progress
            if (outcome.done) return progress
            pause(options.rateLimitMs)
        }
    }

    @Suppress("LongParameterList", "TooGenericExceptionCaught")
    private fun processBatch(
        runId: Long,
        table: String,
        rows: List<SnapshotRow>,
        initialProgress: MigrationProgress,
        options: MigrationOptions,
        transform: (SnapshotRow) -> String,
    ): MigrationProgress {
        var progress = initialProgress
        rows.forEach { row ->
            progress = progress.process(table, row.id)
            val startedAt = System.nanoTime()
            try {
                val migratedJson = transform(row)
                val changed = migratedJson != row.snapshot
                val outcome =
                    when {
                        options.mode == MigrationMode.APPLY && changed -> {
                            jdbcTemplate.update(
                                "UPDATE $table SET snapshot = CAST(? AS jsonb) WHERE id = ?",
                                migratedJson,
                                row.id,
                            )
                            progress = progress.migrated()
                            "MIGRATED"
                        }
                        options.mode == MigrationMode.DRY_RUN && changed -> {
                            progress = progress.migrated()
                            "WOULD_MIGRATE"
                        }
                        options.mode == MigrationMode.VERIFY -> "VALIDATED"
                        else -> "UNCHANGED"
                    }
                recordRowOutcome(runId, table, row, outcome, startedAt)
            } catch (error: Exception) {
                recordFailure(runId, table, row, error)
                recordRowOutcome(runId, table, row, "FAILED", startedAt, error)
                progress = progress.failed()
            }
        }
        updateProgress(runId, progress)
        return progress
    }

    private fun loadDeploymentBatch(
        afterId: Int,
        batchSize: Int,
        legacyOnly: Boolean,
    ): List<SnapshotRow> {
        return jdbcTemplate.query(
            deploymentBatchQuery(legacyOnly),
            { result, _ ->
                SnapshotRow(
                    result.getInt("id"),
                    result.getString("snapshot"),
                    result.getTimestamp("updated_at")?.toInstant(),
                    result.getLong("jsonb_size"),
                )
            },
            afterId,
            batchSize,
        )
    }

    private fun loadRecruitmentBatch(
        afterId: Int,
        batchSize: Int,
        legacyOnly: Boolean,
    ): List<SnapshotRow> {
        val legacyClause =
            if (legacyOnly) "AND jsonb_path_exists(snapshot, '$.participantGroups.*._participantIds')" else ""
        return jdbcTemplate.query(
            "SELECT id, snapshot::text, updated_at, pg_column_size(snapshot) AS jsonb_size FROM recruitments " +
                "WHERE id > ? $legacyClause ORDER BY id LIMIT ? FOR UPDATE",
            { result, _ ->
                SnapshotRow(
                    result.getInt("id"),
                    result.getString("snapshot"),
                    result.getTimestamp("updated_at")?.toInstant(),
                    result.getLong("jsonb_size"),
                )
            },
            afterId,
            batchSize,
        )
    }

    private fun recordInventory(runId: Long) {
        val report = objectMapper.createObjectNode()
        report.put("deploymentCount", count("SELECT COUNT(*) FROM deployments"))
        report.put(
            "legacyDeploymentCount",
            count("SELECT COUNT(*) FROM deployments WHERE $LEGACY_DEPLOYMENT_PREDICATE"),
        )
        report.put("recruitmentCount", count("SELECT COUNT(*) FROM recruitments"))
        report.put(
            "legacyRecruitmentCount",
            count(
                "SELECT COUNT(*) FROM recruitments " +
                    "WHERE jsonb_path_exists(snapshot, '$.participantGroups.*._participantIds')",
            ),
        )
        report.set("deploymentJsonbBytes", jsonbSizePercentiles("deployments"))
        report.set("recruitmentJsonbBytes", jsonbSizePercentiles("recruitments"))
        jdbcTemplate.update(
            "UPDATE core_data_migration_runs SET report = CAST(? AS jsonb) WHERE id = ?",
            report.toString(),
            runId,
        )
        LOGGER.info("CARP Core 1.3 migration inventory: {}", report)
    }

    private fun jsonbSizePercentiles(table: String): JsonNode {
        val values =
            jdbcTemplate.queryForMap(
                "SELECT COALESCE(percentile_cont(0.5) WITHIN GROUP (ORDER BY pg_column_size(snapshot)), 0) AS p50, " +
                    "COALESCE(percentile_cont(0.95) WITHIN GROUP (ORDER BY pg_column_size(snapshot)), 0) AS p95, " +
                    "COALESCE(percentile_cont(0.99) WITHIN GROUP (ORDER BY pg_column_size(snapshot)), 0) AS p99, " +
                    "COALESCE(MAX(pg_column_size(snapshot)), 0) AS max FROM $table",
            )
        return objectMapper.valueToTree(values)
    }

    private fun startOrResumeRun(options: MigrationOptions): Long {
        if (options.resume) {
            jdbcTemplate.query(
                "SELECT id FROM core_data_migration_runs " +
                    "WHERE migration_name = ? AND mode = ? AND status = 'RUNNING' " +
                    "ORDER BY id DESC LIMIT 1",
                { result, _ -> result.getLong("id") },
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

    private fun loadProgress(runId: Long): MigrationProgress =
        jdbcTemplate.queryForObject(
            "SELECT last_deployment_id, last_recruitment_id, processed_count, migrated_count, failure_count " +
                "FROM core_data_migration_runs WHERE id = ?",
            { result, _ ->
                MigrationProgress(
                    result.getInt("last_deployment_id"),
                    result.getInt("last_recruitment_id"),
                    result.getLong("processed_count"),
                    result.getLong("migrated_count"),
                    result.getLong("failure_count"),
                )
            },
            runId,
        )

    private fun updateProgress(
        runId: Long,
        progress: MigrationProgress,
    ) {
        jdbcTemplate.update(
            "UPDATE core_data_migration_runs SET last_deployment_id = ?, last_recruitment_id = ?, " +
                "processed_count = ?, migrated_count = ?, failure_count = ? WHERE id = ?",
            progress.lastDeploymentId,
            progress.lastRecruitmentId,
            progress.processed,
            progress.migrated,
            progress.failures,
            runId,
        )
    }

    private fun recordFailure(
        runId: Long,
        table: String,
        row: SnapshotRow,
        error: Throwable,
    ) {
        jdbcTemplate.update(
            "INSERT INTO core_data_migration_failures (run_id, table_name, row_id, jsonb_size, error) " +
                "VALUES (?, ?, ?, ?, ?) ON CONFLICT (run_id, table_name, row_id) DO UPDATE SET " +
                "jsonb_size = EXCLUDED.jsonb_size, error = EXCLUDED.error, created_at = CURRENT_TIMESTAMP",
            runId,
            table,
            row.id,
            row.jsonbSize,
            error.message ?: error::class.qualifiedName.orEmpty(),
        )
    }

    @Suppress("LongParameterList")
    private fun recordRowOutcome(
        runId: Long,
        table: String,
        row: SnapshotRow,
        outcome: String,
        startedAt: Long,
        error: Throwable? = null,
    ) {
        jdbcTemplate.update(
            "INSERT INTO core_data_migration_rows " +
                "(run_id, table_name, row_id, outcome, duration_ms, jsonb_size, error) VALUES (?, ?, ?, ?, ?, ?, ?) " +
                "ON CONFLICT (run_id, table_name, row_id) DO UPDATE SET outcome = EXCLUDED.outcome, " +
                "duration_ms = EXCLUDED.duration_ms, jsonb_size = EXCLUDED.jsonb_size, error = EXCLUDED.error, " +
                "created_at = CURRENT_TIMESTAMP",
            runId,
            table,
            row.id,
            outcome,
            TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt),
            row.jsonbSize,
            error?.message ?: error?.let { it::class.qualifiedName },
        )
    }

    private fun completeRun(runId: Long) {
        val progress = loadProgress(runId)
        check(progress.failures == 0L) { "Migration completed with ${progress.failures} failed rows." }
        jdbcTemplate.update(
            "UPDATE core_data_migration_runs SET status = 'COMPLETED', completed_at = CURRENT_TIMESTAMP WHERE id = ?",
            runId,
        )
        LOGGER.info(
            "Completed CARP Core 1.3 migration run {}: processed={}, migrated={}.",
            runId,
            progress.processed,
            progress.migrated,
        )
    }

    private fun failRun(
        runId: Long,
        error: Exception,
    ) {
        jdbcTemplate.update(
            "UPDATE core_data_migration_runs SET status = 'FAILED', completed_at = CURRENT_TIMESTAMP, " +
                "report = jsonb_build_object('error', ?) WHERE id = ?",
            error.message ?: error::class.qualifiedName.orEmpty(),
            runId,
        )
    }

    private fun count(sql: String): Long = checkNotNull(jdbcTemplate.queryForObject(sql, Long::class.java))

    private fun pause(milliseconds: Long) {
        if (milliseconds > 0) TimeUnit.MILLISECONDS.sleep(milliseconds)
    }

    companion object {
        private val LOGGER = LogManager.getLogger()
        private const val MIGRATION_NAME = "carp-core-1.2-to-1.3"
    }
}

private data class SnapshotRow(
    val id: Int,
    val snapshot: String,
    val updatedAt: Instant?,
    val jsonbSize: Long,
)

private data class BatchOutcome(
    val progress: MigrationProgress,
    val done: Boolean,
)

private data class MigrationProgress(
    val lastDeploymentId: Int,
    val lastRecruitmentId: Int,
    val processed: Long,
    val migrated: Long,
    val failures: Long,
) {
    fun process(
        table: String,
        id: Int,
    ) = copy(
        lastDeploymentId = if (table == "deployments") id else lastDeploymentId,
        lastRecruitmentId = if (table == "recruitments") id else lastRecruitmentId,
        processed = processed + 1,
    )

    fun migrated() = copy(migrated = migrated + 1)

    fun failed() = copy(failures = failures + 1)
}

private data class MigrationOptions(
    val mode: MigrationMode,
    val batchSize: Int,
    val rateLimitMs: Long,
    val resume: Boolean,
) {
    companion object {
        fun from(environment: Environment): MigrationOptions {
            require(environment.getProperty("spring.main.web-application-type") == "none") {
                "The Core 1.3 migration must run with --spring.main.web-application-type=none."
            }
            val mode =
                MigrationMode.valueOf(
                    environment.getRequiredProperty("carp.core-1-3-migration.mode").replace('-', '_').uppercase(),
                )
            val batchSize =
                environment.getProperty(
                    "carp.core-1-3-migration.batch-size",
                    Int::class.java,
                    DEFAULT_BATCH_SIZE,
                )
            val rateLimitMs = environment.getProperty("carp.core-1-3-migration.rate-limit-ms", Long::class.java, 0L)
            val resume = environment.getProperty("carp.core-1-3-migration.resume", Boolean::class.java, true)
            require(batchSize > 0) { "Migration batch size must be positive." }
            require(rateLimitMs >= 0) { "Migration rate limit must not be negative." }
            return MigrationOptions(mode, batchSize, rateLimitMs, resume)
        }

        private const val DEFAULT_BATCH_SIZE = 100
    }
}

private enum class MigrationMode {
    INVENTORY,
    DRY_RUN,
    APPLY,
    VERIFY,
}

internal const val LEGACY_DEPLOYMENT_PREDICATE = "jsonb_exists(snapshot, 'isStopped')"

internal fun deploymentBatchQuery(legacyOnly: Boolean): String {
    val legacyClause = if (legacyOnly) "AND $LEGACY_DEPLOYMENT_PREDICATE" else ""
    return "SELECT id, snapshot::text, updated_at, pg_column_size(snapshot) AS jsonb_size FROM deployments " +
        "WHERE id > ? $legacyClause ORDER BY id LIMIT ? FOR UPDATE"
}
