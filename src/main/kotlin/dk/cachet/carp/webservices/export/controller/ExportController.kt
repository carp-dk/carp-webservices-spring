package dk.cachet.carp.webservices.export.controller

import dk.cachet.carp.common.application.UUID
import dk.cachet.carp.webservices.common.constants.PathVariableName
import dk.cachet.carp.webservices.export.command.ExportCommandFactory
import dk.cachet.carp.webservices.export.controller.ExportController.Companion.EXPORT_BASE
import dk.cachet.carp.webservices.export.domain.Export
import dk.cachet.carp.webservices.export.domain.dto.SummaryRequest
import dk.cachet.carp.webservices.export.service.ExportService
import dk.cachet.carp.webservices.study.domain.AnonymousParticipantRequest
import io.swagger.v3.oas.annotations.Operation
import jakarta.validation.Valid
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.springframework.core.io.Resource
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException

@RestController
@RequestMapping(EXPORT_BASE)
class ExportController(
    private val exportCommandFactory: ExportCommandFactory,
    private val exportService: ExportService,
) {
    companion object {
        private val LOGGER: Logger = LogManager.getLogger()

        /** Endpoint URI constants */
        const val EXPORT_BASE = "/api/studies/{${PathVariableName.STUDY_ID}}/exports"
        const val DELETE = "/{${PathVariableName.EXPORT_ID}}"
        const val DOWNLOAD = "/{${PathVariableName.EXPORT_ID}}"
        const val SUMMARIES = "/summaries"
        const val ANONYMOUS_PARTICIPANTS = "/anonymous-participants"
    }

    @PostMapping(SUMMARIES)
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PreAuthorize("canManageStudy(#studyId) or canLimitedManageStudy(#studyId)")
    suspend fun exportSummary(
        @PathVariable(PathVariableName.STUDY_ID) studyId: UUID,
        @RequestBody request: SummaryRequest,
    ): Export {
        LOGGER.info("Start POST: $EXPORT_BASE$SUMMARIES")

        try {
            require(request.deploymentIds.isNullOrEmpty() || request.deploymentIds.size == 1) {
                "We only support exporting an entire study or a single deployment," +
                    " (deploymentsIds.size should be less than 2)."
            }
        } catch (e: IllegalArgumentException) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, e.message, e)
        }

        val command =
            exportCommandFactory.createExportSummary(
                studyId,
                request.deploymentIds,
                request.activeDeploymentsOnly,
            )

        return exportService.createExport(command)
    }

    @PostMapping(ANONYMOUS_PARTICIPANTS)
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PreAuthorize("canManageStudy(#studyId) or canLimitedManageStudy(#studyId)")
    suspend fun exportAnonymousParticipants(
        @PathVariable(PathVariableName.STUDY_ID) studyId: UUID,
        @Valid @RequestBody request: AnonymousParticipantRequest,
    ): Export {
        LOGGER.info("Start POST: $EXPORT_BASE$ANONYMOUS_PARTICIPANTS")

        val command = exportCommandFactory.createExportAnonymousParticipants(studyId, request)

        return exportService.createExport(command)
    }

    @DeleteMapping(DELETE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("canManageStudy(#studyId) or canLimitedManageStudy(#studyId)")
    fun delete(
        @PathVariable(PathVariableName.STUDY_ID) studyId: UUID,
        @PathVariable(PathVariableName.EXPORT_ID) exportId: UUID,
    ) {
        LOGGER.info("Start DELETE: $EXPORT_BASE$DELETE")
        exportService.deleteExport(studyId, exportId)
    }

    @GetMapping(DOWNLOAD)
    @PreAuthorize("canManageStudy(#studyId) or canLimitedManageStudy(#studyId)")
    @ResponseStatus(HttpStatus.OK)
    @Operation(description = "Download the export file.")
    fun download(
        @PathVariable(PathVariableName.STUDY_ID) studyId: UUID,
        @PathVariable(PathVariableName.EXPORT_ID) exportId: UUID,
    ): ResponseEntity<Resource> {
        LOGGER.info("Start GET: $EXPORT_BASE$DOWNLOAD")

        val file = exportService.downloadExport(studyId, exportId)

        return ResponseEntity.ok().header(
            HttpHeaders.CONTENT_DISPOSITION,
            "attachment; filename=\"${file.filename}\"",
        ).body(file)
    }

    @GetMapping
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("canManageStudy(#studyId) or canLimitedManageStudy(#studyId)")
    fun poll(
        @PathVariable(PathVariableName.STUDY_ID) studyId: UUID,
    ): List<Export> {
        LOGGER.info("Start GET: $EXPORT_BASE")

        return exportService.getAllForStudy(studyId)
    }
}
