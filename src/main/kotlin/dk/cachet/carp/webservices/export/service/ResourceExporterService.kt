package dk.cachet.carp.webservices.export.service

import dk.cachet.carp.common.application.UUID
import dk.cachet.carp.webservices.export.domain.ExportLog
import java.nio.file.Path

interface ResourceExporterService {
    /** Exports every application resource serialized into the specified [targetDir]. */
    suspend fun exportStudyData(
        studyId: UUID,
        deploymentIds: Set<UUID>?,
        targetDir: Path,
        log: ExportLog,
        activeDeploymentsOnly: Boolean?,
    )

    /**
     * Exports the given [header] and [rows] into a CSV file in the specified [path].
     *
     * @param header The header string of the CSV file (comma-separated).
     * @param rows The rows of the CSV file (comma-separated).
     */
    fun exportCSV(
        header: String,
        rows: List<String>,
        path: Path,
        log: ExportLog,
    )
}
