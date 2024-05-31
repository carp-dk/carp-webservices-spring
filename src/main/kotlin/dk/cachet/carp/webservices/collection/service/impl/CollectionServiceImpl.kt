package dk.cachet.carp.webservices.collection.service.impl

import com.fasterxml.jackson.databind.ObjectMapper
import cz.jirutka.rsql.parser.RSQLParser
import dk.cachet.carp.webservices.account.service.AccountService
import dk.cachet.carp.webservices.collection.domain.Collection
import dk.cachet.carp.webservices.collection.dto.CollectionCreateRequestDto
import dk.cachet.carp.webservices.collection.dto.CollectionUpdateRequestDto
import dk.cachet.carp.webservices.collection.repository.CollectionRepository
import dk.cachet.carp.webservices.collection.service.CollectionService
import dk.cachet.carp.webservices.common.configuration.internationalisation.service.MessageBase
import dk.cachet.carp.webservices.common.exception.responses.AlreadyExistsException
import dk.cachet.carp.webservices.common.exception.responses.ResourceNotFoundException
import dk.cachet.carp.webservices.common.query.QueryUtil
import dk.cachet.carp.webservices.common.query.QueryVisitor
import dk.cachet.carp.webservices.security.authentication.service.AuthenticationService
import dk.cachet.carp.webservices.security.authorization.Claim
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*

@Service
@Transactional
class CollectionServiceImpl(
    private val collectionRepository: CollectionRepository,
    private val accountService: AccountService,
    private val authenticationService: AuthenticationService,
    private val validationMessages: MessageBase,
    private val objectMapper: ObjectMapper,
) : CollectionService {
    private val backgroundWorker = CoroutineScope(Dispatchers.IO)

    companion object {
        private val LOGGER: Logger = LogManager.getLogger()
    }

    override fun delete(
        studyId: String,
        id: Int,
    ) {
        val collection = this.getCollectionByStudyIdAndId(studyId, id)
        collectionRepository.delete(collection)
        LOGGER.info("Collection deleted, id: $id")

        val identity = authenticationService.getCarpIdentity()
        backgroundWorker.launch {
            accountService.revoke(identity, setOf(Claim.CollectionOwner(collection.id)))
        }
    }

    override fun update(
        studyId: String,
        id: Int,
        updateRequest: CollectionUpdateRequestDto,
    ): Collection {
        val collection = this.getCollectionByStudyIdAndId(studyId, id)
        collection.name = updateRequest.name
        LOGGER.info("Collection updated, studyId: $studyId and collectionId: $id")
        return collection
    }

    override fun create(
        request: CollectionCreateRequestDto,
        studyId: String,
        deploymentId: String?,
    ): Collection {
        val collection = Collection()
        collection.name = request.name
        collection.studyId = studyId
        collection.studyDeploymentId = deploymentId
        val collectionByName =
            collectionRepository.findByStudyDeploymentIdAndName(
                collection.studyDeploymentId!!,
                collection.name,
            )
        if (collectionByName.isPresent) {
            throw AlreadyExistsException(
                validationMessages.get("collection.already-exists", request.deploymentId!!, request.name),
            )
        }
        val saved = collectionRepository.save(collection)
        LOGGER.info("Collection saved, id: ${saved.id}")

        val identity = authenticationService.getCarpIdentity()
        backgroundWorker.launch {
            accountService.grant(identity, setOf(Claim.CollectionOwner(saved.id)))
        }

        return saved
    }

    override fun getCollectionByStudyIdAndId(
        studyId: String,
        id: Int,
    ): Collection {
        val collectionOp = collectionRepository.findCollectionByStudyIdAndId(studyId, id)
        if (!collectionOp.isPresent) {
            LOGGER.info("Collection not found, studyId: $studyId, collectionId: $id")
            throw ResourceNotFoundException(
                validationMessages.get("collection.studyId-and-collectionId.not_found", studyId, id),
            )
        }
        val collection = collectionOp.get()
        objectMapper.writeValueAsString(collection)
        return collection
    }

    override fun getCollectionByStudyIdAndByName(
        studyId: String,
        name: String,
    ): Collection {
        val collectionOp = collectionRepository.findCollectionByStudyIdAndName(studyId, name)
        if (!collectionOp.isPresent) {
            LOGGER.warn("Collection not found, studyId: $studyId, collection name: $name")
            throw ResourceNotFoundException(
                validationMessages.get("collection.studyId-and-collectionName.not_found", studyId, name),
            )
        }
        val collection = collectionOp.get()
        objectMapper.writeValueAsString(collection)
        return collection
    }

    /**
     * The function [getAll] retrieves all collections with the given [query] parameters.
     */
    override fun getAll(
        studyId: String,
        query: String?,
    ): List<Collection> {
        val validatedQuery = query?.let { QueryUtil.validateQuery(it) }
        val nestedQuery = "$validatedQuery;study_id==$studyId"
        val specification = RSQLParser().parse(nestedQuery).accept(QueryVisitor<Collection>())
        return collectionRepository.findAll(specification)
    }

    override fun getAll(studyId: String): List<Collection> {
        return collectionRepository.findAllByStudyId(studyId)
    }

    override fun getAllByStudyIdAndDeploymentId(
        studyId: String,
        deploymentId: String,
    ): List<Collection> {
        return collectionRepository.findAllByStudyIdAndDeploymentId(studyId, deploymentId)
    }
}
