package dk.cachet.carp.webservices.export.service

import dk.cachet.carp.common.application.UUID
import dk.cachet.carp.webservices.export.command.ExportCommand
import dk.cachet.carp.webservices.export.domain.Export
import org.springframework.core.io.Resource

interface ExportService {
    fun createExport(command: ExportCommand): Export

    fun downloadExport(
        studyId: UUID,
        exportId: UUID,
    ): Resource

    fun getAllForStudy(studyId: UUID): List<Export>

    fun deleteExport(
        studyId: UUID,
        exportId: UUID,
    ): UUID

    fun cleanupExpiredExports(retentionDays: Int)
}
