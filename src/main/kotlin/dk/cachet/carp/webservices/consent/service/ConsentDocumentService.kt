package dk.cachet.carp.webservices.consent.service

import dk.cachet.carp.common.application.UUID
import dk.cachet.carp.webservices.consent.domain.ConsentDocument
import tools.jackson.databind.JsonNode

/**
 * The Interface [ConsentDocumentService] for CRUD operations on consent documents.
 */
interface ConsentDocumentService {
    fun getAllByStudyId(studyId: UUID): List<ConsentDocument>

    fun getAllByDeploymentIds(deploymentIds: Set<UUID>): List<ConsentDocument>

    fun getOne(consentId: Int): ConsentDocument

    fun delete(consentId: Int)

    fun create(
        deploymentId: UUID,
        data: JsonNode?,
    ): ConsentDocument
}
