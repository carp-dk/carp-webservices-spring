package dk.cachet.carp.webservices.document.service.impl

import cz.jirutka.rsql.parser.RSQLParser
import dk.cachet.carp.common.application.UUID
import dk.cachet.carp.webservices.collection.service.CollectionService
import dk.cachet.carp.webservices.common.configuration.internationalisation.service.MessageBase
import dk.cachet.carp.webservices.common.exception.responses.AlreadyExistsException
import dk.cachet.carp.webservices.common.exception.responses.ResourceNotFoundException
import dk.cachet.carp.webservices.common.query.QueryUtil
import dk.cachet.carp.webservices.common.query.QueryVisitor
import dk.cachet.carp.webservices.document.domain.Document
import dk.cachet.carp.webservices.document.dto.CreateDocumentRequestDto
import dk.cachet.carp.webservices.document.dto.UpdateDocumentRequestDto
import dk.cachet.carp.webservices.document.filter.DocumentSpecifications
import dk.cachet.carp.webservices.document.repository.DocumentRepository
import dk.cachet.carp.webservices.document.service.DocumentService
import dk.cachet.carp.webservices.export.service.ResourceExporter
import dk.cachet.carp.webservices.security.authentication.service.AuthenticationService
import dk.cachet.carp.webservices.security.authorization.Role
import jakarta.servlet.http.HttpServletRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.JsonNode
import java.nio.file.Path

@Service
@Transactional
class DocumentServiceImpl(
    private val documentRepository: DocumentRepository,
    private val documentTraverser: DocumentTraverser,
    private val validationMessages: MessageBase,
    private val authenticationService: AuthenticationService,
    private val collectionService: CollectionService,
) : DocumentService, ResourceExporter<Document> {
    companion object {
        private val LOGGER: Logger = LogManager.getLogger()
    }

    @Suppress("TooGenericExceptionCaught")
    override fun getAll(
        pageRequest: PageRequest,
        query: String?,
        studyId: String,
    ): List<Document> {
        try {
            val role = authenticationService.getRole()
            val id = authenticationService.getId()
            val isFullyAuthorized = role >= Role.RESEARCH_ASSISTANT
            val belongsToStudySpec = DocumentSpecifications.belongsToStudyId(studyId)
            val belongsToUserSpec = DocumentSpecifications.belongsToUserAccountId(id.stringRepresentation)

            val validatedQuery = query?.let { QueryUtil.validateQuery(it) }

            validatedQuery?.let {
                var specification =
                    RSQLParser()
                        .parse(validatedQuery)
                        .accept(QueryVisitor<Document>())
                        .and(belongsToStudySpec)

                if (!isFullyAuthorized) {
                    // Return data relevant to this user only.
                    specification = specification.and(belongsToUserSpec)
                }

                return documentRepository.findAll(specification, pageRequest).content
            }

            if (isFullyAuthorized) {
                return documentRepository.findAll(belongsToStudySpec, pageRequest).content
            }

            return documentRepository.findAll(belongsToStudySpec.and(belongsToUserSpec), pageRequest).content
        } catch (e: Exception) {
            LOGGER.error("Error occurred when invoking get all documents for study id: $studyId", e)
            throw e
        }
    }

    override fun getAll(collectionIds: List<Int>): List<Document> {
        return documentRepository.findAllByCollectionsIdsWithDocuments(collectionIds)
    }

    override fun getByDocumentPath(
        studyId: String,
        request: HttpServletRequest,
    ): String? {
        return documentTraverser.getAllFromDocumentPath(request, studyId, null)
    }

    override fun createByDocumentPath(
        studyId: String,
        data: JsonNode?,
        request: HttpServletRequest,
    ): String? {
        val saved = documentTraverser.createAllFromDocumentPath(request, studyId, data, null)
        LOGGER.info("Document saved, path: $saved")
        return saved
    }

    @Suppress("TooGenericExceptionCaught")
    override fun getOne(id: Int): Document {
        try {
            val optionalDocument = documentRepository.findById(id)
            if (!optionalDocument.isPresent) {
                LOGGER.warn("Document is not found, id: $id")
                throw ResourceNotFoundException(validationMessages.get("document.id.not_found", id))
            }
            return optionalDocument.get()
        } catch (e: Exception) {
            LOGGER.error("Error occurred when invoking get one document with id: $id", e)
            throw e
        }
    }

    // FIXME: saving document without validating its relationships? e.g. collection

    /**
     *  The function [create] creates a new document with the given [request] object.
     *  @param request The [request] containing the document object request.
     *  @return The saved [Document] object.
     */
    override fun create(request: CreateDocumentRequestDto): Document {
        val document = mapCreateDocumentRequestToDocument(request)
        val documentByName = documentRepository.findByNameAndCollectionId(request.name, document.collectionId!!)
        if (documentByName.isPresent) {
            throw AlreadyExistsException(
                validationMessages.get("document.already_exists", request.collectionId!!, request.name),
            )
        }
        val saved = documentRepository.save(document)
        LOGGER.info("Document saved, id: ${saved.id}")
        return saved
    }

    override fun delete(id: Int) {
        val document = getOne(id)
        documentRepository.delete(document)
        LOGGER.info("Document deleted, id: $id")
    }

    override fun update(
        id: Int,
        updateRequest: UpdateDocumentRequestDto,
    ): Document {
        val updated = documentRepository.update(id, updateRequest)
        if (!updated.isPresent) {
            LOGGER.warn("Document is not found, id: $id")
            throw ResourceNotFoundException(validationMessages.get("document.id.not_found", id))
        }
        return updated.get()
    }

    override fun append(
        id: Int,
        updateRequest: UpdateDocumentRequestDto,
    ): Document {
        val updated = documentRepository.appendDocument(id, updateRequest)
        if (!updated.isPresent) {
            LOGGER.warn("Document is not found, id: $id")
            throw ResourceNotFoundException(validationMessages.get("document.id.not_found", id))
        }
        return updated.get()
    }

    private fun mapCreateDocumentRequestToDocument(request: CreateDocumentRequestDto) =
        Document().apply {
            name = request.name
            collectionId = request.collectionId
            collections = request.collections
            data = request.data
        }

    override val dataFileName = "documents.json"

    // We always want to export deployment-agnostic data, which is the case when the collection
    // does not have a deployment ID set.
    override suspend fun exportDataOrThrow(
        studyId: UUID,
        deploymentIds: Set<UUID>,
        target: Path,
    ) = withContext(Dispatchers.IO) {
        val collections =
            collectionService.getAll(studyId.stringRepresentation)
                .filter {
                    it.studyDeploymentId.isNullOrBlank() ||
                        it.studyDeploymentId in deploymentIds.map { d -> d.stringRepresentation }
                }

        getAll(collections.map { it.id })
    }
}
