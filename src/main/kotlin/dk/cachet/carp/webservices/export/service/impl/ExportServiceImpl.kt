package dk.cachet.carp.webservices.export.service.impl

import dk.cachet.carp.common.application.UUID
import dk.cachet.carp.webservices.common.exception.responses.ConflictException
import dk.cachet.carp.webservices.export.command.ExportCommand
import dk.cachet.carp.webservices.export.command.ExportCommandInvoker
import dk.cachet.carp.webservices.export.domain.Export
import dk.cachet.carp.webservices.export.domain.ExportStatus
import dk.cachet.carp.webservices.export.domain.ExportType
import dk.cachet.carp.webservices.export.repository.ExportRepository
import dk.cachet.carp.webservices.export.service.ExportService
import dk.cachet.carp.webservices.file.service.FileStorage
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.springframework.core.io.Resource
import org.springframework.stereotype.Service
import java.io.IOException
import java.nio.file.Path
import java.time.Duration
import java.time.Instant

@Service
class ExportServiceImpl(
    private val exportRepository: ExportRepository,
    private val exportCommandInvoker: ExportCommandInvoker,
    private val fileStorage: FileStorage,
) : ExportService {
    companion object {
        private val LOGGER: Logger = LogManager.getLogger()
    }

    override fun createExport(command: ExportCommand): Export {
        val existing = getExportOrDeleteIfFailed(UUID(command.entry.id), UUID(command.entry.studyId))

        if (existing != null) return existing

        exportCommandInvoker.invoke(command)

        return exportRepository.save(command.entry)
    }

    override fun downloadExport(
        studyId: UUID,
        exportId: UUID,
    ): Resource {
        val export = getExportOrThrow(exportId, studyId)
        val file = fileStorage.getFileAtPath(export.fileName, Path.of(export.relativePath))

        LOGGER.info("Summary with id $studyId is being downloaded.")

        return file
    }

    override fun getAllForStudy(studyId: UUID) = exportRepository.findAllByStudyId(studyId.stringRepresentation)

    override fun deleteExport(
        studyId: UUID,
        exportId: UUID,
    ): UUID {
        val export = getExportOrThrow(exportId, studyId)

        if (export.status == ExportStatus.IN_PROGRESS) {
            throw ConflictException("The export creation is still in progress.")
        }

        fileStorage.deleteFileAtPath(export.fileName, Path.of(export.relativePath))
        exportRepository.delete(export)

        LOGGER.info("Export with id $exportId has been successfully deleted.")

        return studyId
    }

    override fun cleanupExpiredExports(retentionDays: Int) {
        val expiredBefore = Instant.now().minus(Duration.ofDays(retentionDays.toLong()))
        val expiredExports = exportRepository.getAllByUpdatedAtIsBefore(expiredBefore)

        expiredExports
            .filterNot(::isRetainedAfterExpiration)
            .forEach(::deleteExportAndFile)

        LOGGER.info("Export cleanup completed for exports older than $retentionDays days.")
    }

    private fun isRetainedAfterExpiration(export: Export): Boolean =
        when (export.type) {
            ExportType.UNKNOWN,
            ExportType.STUDY_DATA,
            ExportType.DEPLOYMENT_DATA,
            -> false
            ExportType.ANONYMOUS_PARTICIPANTS -> true
        }

    private fun deleteExportAndFile(export: Export) {
        exportRepository.delete(export)
        try {
            fileStorage.deleteFileAtPath(export.fileName, Path.of(export.relativePath))
        } catch (e: IOException) {
            LOGGER.error("Failed to delete export with id ${export.id}.", e)
        }
    }

    /**
     * Get the export with the given [id] if it exists, or delete it if it failed to export and return null.
     */
    private fun getExportOrDeleteIfFailed(
        id: UUID,
        studyId: UUID,
    ): Export? {
        try {
            val export = getExportOrThrow(id, studyId)

            if (export.status != ExportStatus.ERROR) {
                return export
            }

            exportRepository.deleteById(export.id)
        } catch (_: Throwable) {
            LOGGER.info("No export exists with id $id.")
        }

        return null
    }

    private fun getExportOrThrow(
        id: UUID,
        studyId: UUID,
    ): Export {
        val export = exportRepository.findByIdAndStudyId(id.stringRepresentation, studyId.stringRepresentation)

        requireNotNull(export) { "No export exists with id $id." }

        return export
    }
}
