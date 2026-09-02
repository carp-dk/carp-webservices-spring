package dk.cachet.carp.webservices.file.controller

import dk.cachet.carp.common.application.UUID
import dk.cachet.carp.webservices.common.constants.PathVariableName
import dk.cachet.carp.webservices.common.constants.RequestParamName
import dk.cachet.carp.webservices.file.domain.File
import dk.cachet.carp.webservices.file.service.FileService
import dk.cachet.carp.webservices.security.authentication.service.AuthenticationService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.springframework.core.io.Resource
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile

@RestController
class FileController(private val fileService: FileService, private val authenticationService: AuthenticationService) {
    companion object {
        private val LOGGER: Logger = LogManager.getLogger()

        /** Endpoint URI constants */
        const val FILE_BASE = "/api/studies/{${PathVariableName.STUDY_ID}}/files"
        const val UPLOAD_IMAGE = "/api/studies/{${PathVariableName.STUDY_ID}}/images"
        const val DOWNLOAD = "$FILE_BASE/{${PathVariableName.FILE_ID}}/download"
        const val FILE_ID = "$FILE_BASE/{${PathVariableName.FILE_ID}}"
    }

    @GetMapping(FILE_ID)
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize(
        "canManageStudy(#studyId) or canLimitedManageStudy(#studyId) or @fileControllerAuthorizer.isFileOwner(#fileId)",
    )
    fun getOne(
        @PathVariable(PathVariableName.STUDY_ID) studyId: UUID,
        @PathVariable(PathVariableName.FILE_ID) fileId: Int,
    ): File {
        LOGGER.info("Start GET: /api/studies/$studyId/files/$fileId")
        return fileService.getOne(fileId)
    }

    // As of 2026-09, the only known live use of the general RSQL `query` filter across any consumer we
    // checked (carp-portal, carp.sensing-flutter, carp-client-ts) was carp.sensing-flutter's exact
    // original_name==<name> file lookup — everything else either omits query entirely or doesn't call
    // this endpoint at all. `originalName` below is the dedicated, RSQL-free replacement for that one
    // use case: it's the parameter to keep and to point new/updated clients at. `query` stays working
    // (with the scope-bypass fixed) only until that client migrates — at which point it, unlike
    // Collection/Document's `query`, should become removable too. Don't assume it's unused yet.
    @GetMapping(FILE_BASE)
    @PreAuthorize("canManageStudy(#studyId) or canLimitedManageStudy(#studyId)")
    @ResponseStatus(HttpStatus.OK)
    fun getAll(
        @PathVariable(PathVariableName.STUDY_ID) studyId: UUID,
        @Parameter(
            deprecated = true,
            description = "Deprecated — prefer original_name where possible. Will be removed in a future release.",
        )
        @RequestParam(RequestParamName.QUERY, required = false) query: String?,
        // The confirmed-live, RSQL-free replacement for the original_name==<name> pattern above — keep.
        @RequestParam(RequestParamName.ORIGINAL_NAME, required = false) originalName: String?,
    ): List<File> {
        LOGGER.info("Start GET: /api/studies/$studyId/files")
        return fileService.getAll(query, originalName, studyId.stringRepresentation)
    }

    @GetMapping(
        produces = [
            MediaType.MULTIPART_FORM_DATA_VALUE,
            MediaType.APPLICATION_OCTET_STREAM_VALUE,
        ],
        value = [DOWNLOAD],
    )
    @ResponseBody
    @PreAuthorize(
        "canManageStudy(#studyId) or canLimitedManageStudy(#studyId) or @fileControllerAuthorizer.isFileOwner(#id)",
    )
    @ResponseStatus(HttpStatus.OK)
    @Operation(description = "Ensure the JWT token is refreshed, before accessing this endpoint.")
    fun download(
        @PathVariable(PathVariableName.STUDY_ID) studyId: UUID,
        @PathVariable(PathVariableName.FILE_ID) id: Int,
    ): ResponseEntity<Resource> {
        LOGGER.info("Start GET: /api/studies/$studyId/files/$id/download")
        val (fileToDownload, originalFilename) = fileService.download(id, studyId)

        return ResponseEntity.ok().header(
            HttpHeaders.CONTENT_DISPOSITION,
            "attachment; filename=\"$originalFilename\"",
        ).body(fileToDownload)
    }

    @PostMapping(
        consumes = [
            MediaType.MULTIPART_FORM_DATA_VALUE,
            MediaType.APPLICATION_OCTET_STREAM_VALUE,
        ],
        produces = [MediaType.APPLICATION_JSON_VALUE],
        value = [FILE_BASE],
    )
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("canManageStudy(#studyId) or canLimitedManageStudy(#studyId) or isInDeploymentOfStudy(#studyId)")
    fun create(
        @PathVariable(PathVariableName.STUDY_ID) studyId: UUID,
        @RequestParam(RequestParamName.METADATA, required = false) metadata: String?,
        @RequestParam(RequestParamName.DEPLOYMENT_ID, required = true) deploymentId: UUID,
        @RequestPart file: MultipartFile,
    ): File {
        LOGGER.info("Start POST: /api/studies/$studyId/files")
        val ownerId = authenticationService.getId()

        return fileService.create(studyId, deploymentId, ownerId, file, metadata)
    }

    @DeleteMapping(FILE_ID)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(
        "canManageStudy(#studyId) or canLimitedManageStudy(#studyId) or @fileControllerAuthorizer.isFileOwner(#fileId)",
    )
    fun delete(
        @PathVariable(PathVariableName.STUDY_ID) studyId: UUID,
        @PathVariable(PathVariableName.FILE_ID) fileId: Int,
    ) {
        LOGGER.info("Start DELETE: /api/studies/$studyId/files/$fileId")
        fileService.delete(fileId, studyId)
    }

    @PostMapping(UPLOAD_IMAGE)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("canManageStudy(#studyId) or canLimitedManageStudy(#studyId) or isInDeploymentOfStudy(#studyId)")
    fun uploadS3(
        @PathVariable(PathVariableName.STUDY_ID) studyId: UUID,
        @RequestParam(RequestParamName.IMAGE, required = true) image: MultipartFile,
    ): String {
        LOGGER.info("Start PUT: /api/studies/$studyId/images")
        return fileService.uploadImage(image, studyId.stringRepresentation)
    }
}
