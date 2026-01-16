package dk.cachet.carp.webservices.export.command.impl

import dk.cachet.carp.common.application.UUID
import dk.cachet.carp.webservices.export.command.ExportCommand
import dk.cachet.carp.webservices.export.domain.Export
import dk.cachet.carp.webservices.export.service.ResourceExporterService
import dk.cachet.carp.webservices.file.util.FileUtil
import org.apache.logging.log4j.LogManager
import java.io.IOException
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteRecursively

class ExportSummary(
    entry: Export,
    private val deploymentIds: Set<UUID>?,
    private val resourceExporter: ResourceExporterService,
    private val fileUtil: FileUtil,
    private val activeDeploymentsOnly: Boolean?,
) : ExportCommand(entry) {
    companion object {
        private val LOGGER = LogManager.getLogger()
    }

    override fun canExecute(): Pair<Boolean, String> = Pair(true, "")

    @OptIn(ExperimentalPathApi::class)
    override suspend fun execute() {
        val workingDir = createTempDirectory()
        val zipPath =
            fileUtil.resolveFileStoragePathForFilenameAndRelativePath(
                entry.fileName,
                Path.of(entry.relativePath),
            )

        resourceExporter.exportStudyData(UUID(entry.studyId), deploymentIds, workingDir, logger, activeDeploymentsOnly)

        try {
            fileUtil.zipDirectory(workingDir, zipPath)
        } catch (e: IOException) {
            LOGGER.error("Zipping failed for study: ${entry.studyId}")
            fileUtil.deleteFile(zipPath)
            throw e
        } finally {
            workingDir.deleteRecursively()
        }
    }
}
