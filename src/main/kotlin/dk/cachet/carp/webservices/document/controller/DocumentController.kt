package dk.cachet.carp.webservices.document.controller

import dk.cachet.carp.common.application.UUID
import dk.cachet.carp.webservices.common.configuration.internationalisation.service.MessageBase
import dk.cachet.carp.webservices.common.constants.PathVariableName
import dk.cachet.carp.webservices.common.constants.RequestParamName
import dk.cachet.carp.webservices.common.exception.responses.ResourceNotFoundException
import dk.cachet.carp.webservices.common.query.QueryUtil
import dk.cachet.carp.webservices.document.controller.DocumentController.Companion.DOCUMENT_BASE
import dk.cachet.carp.webservices.document.domain.Document
import dk.cachet.carp.webservices.document.dto.CreateDocumentRequestDto
import dk.cachet.carp.webservices.document.dto.UpdateDocumentRequestDto
import dk.cachet.carp.webservices.document.service.DocumentService
import dk.cachet.carp.webservices.document.service.impl.DocumentTraverser
import io.swagger.v3.oas.annotations.Operation
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.springframework.data.domain.PageRequest
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import tools.jackson.databind.JsonNode

@RestController
@RequestMapping(value = [DOCUMENT_BASE])
class DocumentController(
    private val documentService: DocumentService,
    private val documentTraverser: DocumentTraverser,
    private val validationMessages: MessageBase,
) {
    companion object {
        private val LOGGER: Logger = LogManager.getLogger()

        /** Endpoint URI constants */
        const val DOCUMENT_BASE = "/api/studies/{${PathVariableName.STUDY_ID}}"
        const val GET_DOCUMENT_BY_ID = "/documents/{${PathVariableName.DOCUMENT_ID}}"
        const val DOCUMENTS = "/documents"
        const val COLLECTIONS = "/collections/**"
        const val APPEND = "/append"

        /** Others */
        // A page of documents is JSONB-laden, so the page size bounds how much of the heap one request
        // can claim on the JVM that is also ingesting live data. Callers page with `page`.
        const val DEFAULT_PAGE_SIZE = 1000
    }

    @GetMapping(value = [DOCUMENTS])
    @PreAuthorize("canManageStudy(#studyId) or canLimitedManageStudy(#studyId)")
    @ResponseStatus(HttpStatus.OK)
    fun getAll(
        @RequestParam(RequestParamName.QUERY) query: String?,
        @RequestParam(RequestParamName.SORT, required = false) sort: String?,
        @RequestParam(RequestParamName.PAGE, required = false) page: Int?,
        @PathVariable(PathVariableName.STUDY_ID) studyId: UUID,
    ): List<Document> {
        LOGGER.info("Start GET: /api/studies/$studyId/documents")
        val pageRequest = PageRequest.of(page ?: 0, DEFAULT_PAGE_SIZE, QueryUtil.sort(sort))
        return documentService.getAll(pageRequest, query, studyId.stringRepresentation)
    }

    @GetMapping(value = [COLLECTIONS], produces = [MediaType.APPLICATION_JSON_VALUE])
    @PreAuthorize("canManageStudy(#studyId) or canLimitedManageStudy(#studyId) or isInDeploymentOfStudy(#studyId)")
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    @ResponseStatus(HttpStatus.OK)
    @Operation(description = "Get a document by its path e.g. .../collections/foo/bar/baz")
    fun getByDocumentPath(
        @PathVariable(PathVariableName.STUDY_ID) studyId: UUID,
        request: HttpServletRequest,
    ): String? {
        try {
            val path = documentTraverser.requestToUrlPath(request)
            LOGGER.info("Start GET: $path")
            return documentService.getByDocumentPath(studyId.stringRepresentation, request)
        } catch (e: NullPointerException) {
            LOGGER.error("Document not found, studyId: $studyId, path: ${request.requestURI}")
            throw ResourceNotFoundException(
                validationMessages.get("document.path.not_found", studyId.stringRepresentation, request.requestURI),
            )
        }
    }

    @PostMapping(value = [COLLECTIONS], produces = [MediaType.APPLICATION_JSON_VALUE])
    @PreAuthorize("canManageStudy(#studyId) or canLimitedManageStudy(#studyId) or isInDeploymentOfStudy(#studyId)")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(description = "Create a document by its path e.g. .../collections/foo/bar/baz")
    fun createByDocumentPath(
        @PathVariable(PathVariableName.STUDY_ID) studyId: UUID,
        @RequestBody data: JsonNode?,
        request: HttpServletRequest,
    ): String? {
        val path = documentTraverser.requestToUrlPath(request)
        LOGGER.info("Start POST: $path")
        return documentService.createByDocumentPath(studyId.stringRepresentation, data, request)
    }

    @GetMapping(value = [GET_DOCUMENT_BY_ID], produces = [MediaType.APPLICATION_JSON_VALUE])
    @PreAuthorize("canManageStudy(#studyId) or canLimitedManageStudy(#studyId) or isInDeploymentOfStudy(#studyId)")
    @ResponseStatus(HttpStatus.OK)
    fun getOne(
        @PathVariable(PathVariableName.STUDY_ID) studyId: UUID,
        @PathVariable(PathVariableName.DOCUMENT_ID) id: Int,
    ): Document {
        LOGGER.info("Start GET: /api/studies/$studyId/documents/$id")
        return documentService.getOne(id)
    }

    @PostMapping(value = [DOCUMENTS], produces = [MediaType.APPLICATION_JSON_VALUE])
    @PreAuthorize("canManageStudy(#studyId) or canLimitedManageStudy(#studyId) or isInDeploymentOfStudy(#studyId)")
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        @PathVariable(PathVariableName.STUDY_ID) studyId: UUID,
        @Valid @RequestBody request: CreateDocumentRequestDto,
    ): Document {
        LOGGER.info("Start POST: /api/studies/$studyId/documents")
        return documentService.create(request)
    }

    @DeleteMapping(value = [GET_DOCUMENT_BY_ID])
    @PreAuthorize("canManageStudy(#studyId) or canLimitedManageStudy(#studyId) or isInDeploymentOfStudy(#studyId)")
    @ResponseStatus(HttpStatus.OK)
    fun delete(
        @PathVariable(PathVariableName.STUDY_ID) studyId: UUID,
        @PathVariable(PathVariableName.DOCUMENT_ID) id: Int,
    ) {
        LOGGER.info("Start DELETE: /api/studies/$studyId/documents/$id")
        documentService.delete(id)
    }

    @PutMapping(value = [GET_DOCUMENT_BY_ID])
    @PreAuthorize("canManageStudy(#studyId) or canLimitedManageStudy(#studyId) or isInDeploymentOfStudy(#studyId)")
    @ResponseStatus(HttpStatus.OK)
    fun update(
        @PathVariable(PathVariableName.STUDY_ID) studyId: UUID,
        @PathVariable(PathVariableName.DOCUMENT_ID) id: Int,
        @Valid @RequestBody document: UpdateDocumentRequestDto,
    ): Document {
        LOGGER.info("Start PUT: /api/studies/$studyId/documents/$id")
        return documentService.update(id, document)
    }

    @PutMapping(value = [GET_DOCUMENT_BY_ID + APPEND])
    @PreAuthorize("canManageStudy(#studyId) or canLimitedManageStudy(#studyId) or isInDeploymentOfStudy(#studyId)")
    @ResponseStatus(HttpStatus.OK)
    fun append(
        @PathVariable(PathVariableName.STUDY_ID) studyId: UUID,
        @PathVariable(PathVariableName.DOCUMENT_ID) id: Int,
        @Valid @RequestBody document: UpdateDocumentRequestDto,
    ): Document {
        LOGGER.info("Start PUT: /api/studies/$studyId/documents/$id/append")
        return documentService.append(id, document)
    }
}
