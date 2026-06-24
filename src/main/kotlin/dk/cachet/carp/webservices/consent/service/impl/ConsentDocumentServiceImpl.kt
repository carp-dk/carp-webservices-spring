package dk.cachet.carp.webservices.consent.service.impl

import dk.cachet.carp.common.application.UUID
import dk.cachet.carp.studies.application.users.ParticipantGroupStatus
import dk.cachet.carp.webservices.common.configuration.internationalisation.service.MessageBase
import dk.cachet.carp.webservices.common.exception.responses.ResourceNotFoundException
import dk.cachet.carp.webservices.consent.domain.ConsentDocument
import dk.cachet.carp.webservices.consent.repository.ConsentDocumentRepository
import dk.cachet.carp.webservices.consent.service.ConsentDocumentService
import dk.cachet.carp.webservices.export.service.ResourceExporter
import dk.cachet.carp.webservices.security.config.SecurityCoroutineContext
import dk.cachet.carp.webservices.study.service.RecruitmentService
import kotlinx.coroutines.*
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.JsonNode
import java.nio.file.Path

/**
 * The Class [ConsentDocumentServiceImpl].
 * The [ConsentDocumentServiceImpl] provides implementation to create, retrieve, and delete a consent documents.
 */
@Service
@Transactional
class ConsentDocumentServiceImpl(
    private val consentDocumentRepository: ConsentDocumentRepository,
    private val validationMessages: MessageBase,
    private val recruitmentService: RecruitmentService,
) : ConsentDocumentService, ResourceExporter<ConsentDocument> {
    companion object {
        private val LOGGER: Logger = LogManager.getLogger()
    }

    override fun getAllByStudyId(studyId: UUID): List<ConsentDocument> {
        val deploymentIds =
            runBlocking(Dispatchers.IO + SecurityCoroutineContext()) {
                recruitmentService.core.getParticipantGroupStatusList(studyId)
                    .filterIsInstance<ParticipantGroupStatus.InDeployment>()
                    .map { it.studyDeploymentStatus.studyDeploymentId }
                    .toSet()
            }

        return getAllByDeploymentIds(deploymentIds)
    }

    override fun getAllByDeploymentIds(deploymentIds: Set<UUID>): List<ConsentDocument> =
        consentDocumentRepository.findAllByDeploymentIds(deploymentIds.map { it.toString() })

    override fun getOne(consentId: Int): ConsentDocument {
        val optionalConsent = consentDocumentRepository.findById(consentId)
        if (!optionalConsent.isPresent) {
            LOGGER.info("Consent document is not found, id: $consentId")
            throw ResourceNotFoundException(validationMessages.get("consent.document.id.not_found", consentId))
        }
        return optionalConsent.get()
    }

    override fun delete(consentId: Int) {
        val consent = getOne(consentId)
        consentDocumentRepository.delete(consent)
        LOGGER.info("Consent document deleted, id: ${consent.id}")
    }

    override fun create(
        deploymentId: UUID,
        data: JsonNode?,
    ): ConsentDocument {
        val saved =
            consentDocumentRepository.save(
                ConsentDocument(
                    deploymentId = deploymentId.stringRepresentation,
                    data = data,
                ),
            )
        LOGGER.info("Consent document created, id: ${saved.id}")

        return saved
    }

    override val dataFileName = "consent-documents.json"

    override suspend fun exportDataOrThrow(
        studyId: UUID,
        deploymentIds: Set<UUID>,
        target: Path,
    ) = withContext(Dispatchers.IO) {
        getAllByDeploymentIds(deploymentIds)
    }
}
