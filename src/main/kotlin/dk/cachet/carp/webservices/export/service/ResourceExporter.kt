package dk.cachet.carp.webservices.export.service

import dk.cachet.carp.common.application.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.KSerializer
import java.nio.file.Path

/**
 * Makes a service as capable of exporting a specific type of data. This allows each service to define its own export
 * logic and be discovered by the [ResourceExporterService] automatically.
 *
 * @param TExport The type of data to export.
 */
interface ResourceExporter<out TExport> {
    companion object {
        /** Batch size for exporters that override [exportBatchesOrThrow]. */
        const val EXPORT_BATCH_SIZE = 100
    }

    val dataFileName: String

    /**
     * The kotlinx serializer for [TExport], set by exporters of CARP Core types, whose JSON format is defined by
     * kotlinx.serialization and which Jackson cannot reproduce. When null, the export is written with Jackson.
     */
    val exportSerializer: KSerializer<@UnsafeVariance TExport>? get() = null

    suspend fun exportDataOrThrow(
        studyId: UUID,
        deploymentIds: Set<UUID>,
        target: Path,
    ): Collection<TExport>

    /**
     * The export as a flow of batches, which are written to the file one at a time, so only the current batch is
     * held in memory. Defaults to [exportDataOrThrow] as a single batch; exporters whose data grows with the study
     * override this to load it in batches.
     */
    fun exportBatchesOrThrow(
        studyId: UUID,
        deploymentIds: Set<UUID>,
        target: Path,
    ): Flow<Collection<TExport>> = flow { emit(exportDataOrThrow(studyId, deploymentIds, target)) }
}
