package dk.cachet.carp.webservices.collection.controller

import dk.cachet.carp.common.application.UUID
import dk.cachet.carp.webservices.collection.controller.CollectionController.Companion.COLLECTION_BASE
import dk.cachet.carp.webservices.collection.domain.Collection
import dk.cachet.carp.webservices.collection.dto.CollectionCreateRequestDto
import dk.cachet.carp.webservices.collection.dto.CollectionUpdateRequestDto
import dk.cachet.carp.webservices.collection.service.CollectionService
import dk.cachet.carp.webservices.common.constants.PathVariableName
import dk.cachet.carp.webservices.common.constants.RequestParamName
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import jakarta.validation.Valid
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping(value = [COLLECTION_BASE])
class CollectionController(
    private val collectionService: CollectionService,
) {
    companion object {
        private val LOGGER: Logger = LogManager.getLogger()

        /** Endpoint URI constants */
        const val COLLECTION_BASE = "/api/studies/{${PathVariableName.STUDY_ID}}/collections"

        // todo remove /id/ from path
        const val GET_COLLECTION_BY_ID = "/id/{${PathVariableName.COLLECTION_ID}}"
        const val GET_COLLECTION_BY_NAME = "/{${PathVariableName.COLLECTION_NAME}}"
        const val GET_COLLECTION_BY_DEPLOYMENT_ID = "/deployments/{${PathVariableName.DEPLOYMENT_ID}}"
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("canManageStudy(#studyId) or canLimitedManageStudy(#studyId) or isInDeploymentOfStudy(#studyId)")
    fun create(
        @PathVariable(PathVariableName.STUDY_ID) studyId: UUID,
        @Valid @RequestBody request: CollectionCreateRequestDto,
    ): Collection {
        LOGGER.info("Start POST: /api/studies/$studyId/collections")
        return collectionService.create(request, studyId.stringRepresentation, request.deploymentId)
    }

    @GetMapping(value = [GET_COLLECTION_BY_ID])
    @PreAuthorize("canManageStudy(#studyId) or canLimitedManageStudy(#studyId) or isInDeploymentOfStudy(#studyId)")
    @ResponseStatus(HttpStatus.OK)
    fun getByStudyIdAndCollectionId(
        @PathVariable(PathVariableName.STUDY_ID) studyId: UUID,
        @PathVariable(PathVariableName.COLLECTION_ID) collectionId: Int,
    ): Collection {
        LOGGER.info("Start GET: /api/studies/$studyId/collections/id/$collectionId")
        return collectionService.getCollectionByStudyIdAndId(studyId.stringRepresentation, collectionId)
    }

    // todo replace with .../collection?collectionName=...
    @GetMapping(value = [GET_COLLECTION_BY_NAME])
    @PreAuthorize("canManageStudy(#studyId) or canLimitedManageStudy(#studyId) or isInDeploymentOfStudy(#studyId)")
    @Operation(description = "Gets a collection by studyId and collectionName")
    @ResponseStatus(HttpStatus.OK)
    fun getByStudyIdAndCollectionName(
        @PathVariable(PathVariableName.STUDY_ID) studyId: String,
        @PathVariable(PathVariableName.COLLECTION_NAME) collectionName: String,
    ): Collection {
        LOGGER.info("Start GET: /api/studies/$studyId/collections/$collectionName")
        return collectionService.getCollectionByStudyIdAndByName(studyId, collectionName)
    }

    // As of 2026-09, no consumer we checked (carp-portal, carp.sensing-flutter, carp-client-ts) calls
    // this endpoint at all — the query param appears entirely unused. Candidate for outright removal
    // once that's confirmed with any other consumers, independently of Document/File below (see their
    // own removal notes — each is unused on a different timeline).
    @GetMapping
    @PreAuthorize("canManageStudy(#studyId) or canLimitedManageStudy(#studyId)")
    @ResponseStatus(HttpStatus.OK)
    fun getAll(
        @PathVariable(PathVariableName.STUDY_ID) studyId: UUID,
        @Parameter(deprecated = true, description = "Deprecated. Will be removed in a future release.")
        @RequestParam(RequestParamName.QUERY, required = true) query: String?,
    ): List<Collection> {
        LOGGER.info("Start GET: /api/studies/$studyId/collections?query=$query")
        return collectionService.getAll(studyId.stringRepresentation, query)
    }

    // todo replace with .../collection?deploymentId=...
    @GetMapping(value = [GET_COLLECTION_BY_DEPLOYMENT_ID])
    @Operation(description = "Gets a collection by studyId and deploymentId")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("canManageStudy(#studyId) or canLimitedManageStudy(#studyId) or isInDeployment(#deploymentId)")
    fun getByStudyIdAndDeploymentId(
        @PathVariable(PathVariableName.STUDY_ID) studyId: UUID,
        @PathVariable(PathVariableName.DEPLOYMENT_ID) deploymentId: UUID,
    ): List<Collection> {
        LOGGER.info("Start GET: /api/studies/$studyId/deployments/$deploymentId/collections")
        return collectionService.getAllByStudyIdAndDeploymentId(
            studyId.stringRepresentation,
            deploymentId.stringRepresentation,
        )
    }

    @DeleteMapping(value = [GET_COLLECTION_BY_ID])
    @PreAuthorize("canManageStudy(#studyId) or canLimitedManageStudy(#studyId) or isCollectionOwner(#collectionId)")
    @ResponseStatus(HttpStatus.OK)
    fun delete(
        @PathVariable(PathVariableName.STUDY_ID) studyId: UUID,
        @PathVariable(PathVariableName.COLLECTION_ID) collectionId: Int,
    ) {
        LOGGER.info("Start DELETE: /api/studies/$studyId/collections/id/$collectionId")
        collectionService.delete(studyId.stringRepresentation, collectionId)
    }

    @PutMapping(value = [GET_COLLECTION_BY_ID])
    @PreAuthorize("canManageStudy(#studyId) or canLimitedManageStudy(#studyId) or isCollectionOwner(#collectionId)")
    @ResponseStatus(HttpStatus.OK)
    fun update(
        @PathVariable(PathVariableName.STUDY_ID) studyId: UUID,
        @PathVariable(PathVariableName.COLLECTION_ID) collectionId: Int,
        @Valid @RequestBody request: CollectionUpdateRequestDto,
    ): Collection {
        LOGGER.info("Start PUT: /api/studies/$studyId/collections/id/$collectionId")
        return collectionService.update(studyId.stringRepresentation, collectionId, request)
    }
}
