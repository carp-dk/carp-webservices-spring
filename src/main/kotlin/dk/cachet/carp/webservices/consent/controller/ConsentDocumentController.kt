package dk.cachet.carp.webservices.consent.controller

import dk.cachet.carp.common.application.UUID
import dk.cachet.carp.webservices.common.constants.PathVariableName
import dk.cachet.carp.webservices.consent.domain.ConsentDocument
import dk.cachet.carp.webservices.consent.service.ConsentDocumentService
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import tools.jackson.databind.JsonNode

// todo should we delete?
@Deprecated("This class is deprecated, use participantData instead")
@RestController
class ConsentDocumentController(
    private val consentDocumentService: ConsentDocumentService,
) {
    companion object {
        private val LOGGER: Logger = LogManager.getLogger()

        /** Endpoint URI constants */
        const val CONSENT_BY_STUDY_ID = "/api/studies/{${PathVariableName.STUDY_ID}}/consent-documents"
        const val CONSENT_BY_DEPLOYMENT_ID = "/api/deployments/{${PathVariableName.DEPLOYMENT_ID}}/consent-documents"
        const val CONSENT_BY_ID = "/{${PathVariableName.CONSENT_ID}}"
    }

    @RequestMapping(
        value = [ CONSENT_BY_STUDY_ID, CONSENT_BY_DEPLOYMENT_ID ],
        method = [ RequestMethod.GET ],
    )
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("canManageDeployment( #deploymentId ) or canManageStudy( #studyId ) ")
    fun getAll(
        @PathVariable(PathVariableName.DEPLOYMENT_ID, required = false) deploymentId: UUID?,
        @PathVariable(PathVariableName.STUDY_ID, required = false) studyId: UUID?,
    ): List<ConsentDocument> {
        if (deploymentId != null) {
            LOGGER.info("Start GET: /api/deployments/$deploymentId/consent-documents")
            return consentDocumentService.getAllByDeploymentIds(setOf(deploymentId))
        } else if (studyId != null) {
            LOGGER.info("Start GET: /api/studies/$studyId/consent-documents")
            return consentDocumentService.getAllByStudyId(studyId)
        }

        throw IllegalArgumentException("Either a study ID or deployment ID should be provided.")
    }

    @GetMapping(CONSENT_BY_DEPLOYMENT_ID + CONSENT_BY_ID)
    @PreAuthorize("canManageDeployment( #deploymentId ) or isInDeployment( #deploymentId )")
    @ResponseStatus(HttpStatus.OK)
    fun getOne(
        @PathVariable(PathVariableName.DEPLOYMENT_ID) deploymentId: UUID,
        @PathVariable(PathVariableName.CONSENT_ID) consentId: Int,
    ): ConsentDocument {
        LOGGER.info("Start GET: /api/deployments/$deploymentId/consent-documents/$consentId")
        return consentDocumentService.getOne(consentId)
    }

    @PostMapping(CONSENT_BY_DEPLOYMENT_ID)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("isInDeployment( #deploymentId )")
    fun create(
        @PathVariable(PathVariableName.DEPLOYMENT_ID) deploymentId: UUID,
        @RequestBody data: JsonNode?,
    ): ConsentDocument {
        LOGGER.info("Start POST: /api/deployments/$deploymentId/consent-documents")
        return consentDocumentService.create(deploymentId, data)
    }

    @DeleteMapping(CONSENT_BY_DEPLOYMENT_ID + CONSENT_BY_ID)
    @PreAuthorize("canManageDeployment( #deploymentId ) or isInDeployment( #deploymentId )")
    @ResponseStatus(HttpStatus.OK)
    fun delete(
        @PathVariable(PathVariableName.DEPLOYMENT_ID) deploymentId: UUID,
        @PathVariable(PathVariableName.CONSENT_ID) consentId: Int,
    ) {
        LOGGER.info("Start DELETE: /api/deployments/$deploymentId/consent-documents/$consentId")
        consentDocumentService.delete(consentId)
    }
}
