package dk.cachet.carp.webservices.document.service.impl

import dk.cachet.carp.webservices.collection.domain.Collection
import dk.cachet.carp.webservices.collection.repository.CollectionRepository
import dk.cachet.carp.webservices.common.configuration.internationalisation.service.MessageBase
import dk.cachet.carp.webservices.common.exception.responses.AlreadyExistsException
import dk.cachet.carp.webservices.common.exception.responses.ResourceNotFoundException
import dk.cachet.carp.webservices.document.domain.Document
import dk.cachet.carp.webservices.document.repository.DocumentRepository
import jakarta.servlet.http.HttpServletRequest
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.springframework.stereotype.Service
import org.springframework.web.servlet.HandlerMapping
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.util.*

/** The Data Class [CollectionWrapper]. */
data class CollectionWrapper(
    val id: Int?,
    val name: String,
    val type: CollectionWrapperType,
    val scopeToUserAccountId: String?,
)

/** The Data Class [DocumentPath]. */
data class DocumentPath(val collections: List<CollectionWrapper>, val documents: List<CollectionWrapper>)

/** The Enum Class [DocumentPathException]. */
enum class CollectionWrapperType { Document, Collection }

/**
 * The Class [DocumentPathException].
 * The [DocumentPathException] handles exceptions thrown when the document cannot create path from request to URL path.
 */
class DocumentPathException(message: String) : Exception(message)

/**
 * The Service Class [DocumentTraverser].
 * The [DocumentTraverser] implements the [DocumentRepository] and [CollectionRepository] interfaces
 * to create, retrieve, and delete documents.
 */
@Service
class DocumentTraverser(
    private val documentRepository: DocumentRepository,
    private val collectionRepository: CollectionRepository,
    private val validationMessages: MessageBase,
) {
    companion object {
        private val LOGGER: Logger = LogManager.getLogger()
    }

    /**
     * Takes a request object with a path structure: collections/{collectionName}/{documentName}/.../
     * and builds the entire representation in the database, passing back either a
     * Collection or Document object with the corresponding data payload.
     *
     * @param request The request to split into collection/document parts.
     * @param studyId The studyId to store the objects against.
     * @param documentData The json payload (if supplied) to populate the final document in the path.
     * @param scopeToUserId (Optional) Scope the request to the author of the document.
     * @throws DocumentPathException when the document cannot create path from [request] to URL path.
     * @return The created Json object (Collection/Document), or null if the path was invalid.
     */
    @Suppress("LongMethod")
    fun createAllFromDocumentPath(
        request: HttpServletRequest,
        studyId: String,
        documentData: JsonNode?,
        scopeToUserId: String?,
    ): String? {
        val pathList = requestToPathList(request, scopeToUserId)

        var lastCollectionWrapper: CollectionWrapper? = null

        if (pathList.collections.isEmpty()) {
            LOGGER.warn("Cannot create path from: ${requestToUrlPath(request)}")
            throw DocumentPathException(validationMessages.get("document.create_all.empty", requestToUrlPath(request)))
        }

        pathList.collections.forEachIndexed { index, wrappedCollection ->

            val parentDocumentId = lastCollectionWrapper?.id

            val collection =
                collectionRepository.findByNameAndStudyIdAndDocumentId(
                    wrappedCollection.name,
                    studyId,
                    parentDocumentId,
                ).orElseGet {
                    // If there's no collection to store this document in, create it,
                    // referencing a parent document if required.

                    collectionRepository.save(
                        Collection(
                            name = wrappedCollection.name,
                            studyId = studyId,
                            documentId = lastCollectionWrapper?.id,
                        ),
                    )
                }

            // If we're not creating a document, return the Collection object.

            lastCollectionWrapper =
                if (index >= pathList.documents.size) {
                    CollectionWrapper(
                        collection.id,
                        collection.name,
                        CollectionWrapperType.Collection,
                        scopeToUserId,
                    )
                } else {
                    val documentName = pathList.documents[index].name
                    val documentQuery =
                        documentRepository.findByNameAndCollectionId(
                            pathList.documents[index].name,
                            collection.id,
                        )

                    if (documentQuery.isPresent) {
                        throw AlreadyExistsException(
                            validationMessages.get(
                                "document.already_exists",
                                collection.id,
                                pathList.documents[index].name,
                            ),
                        )
                    }

                    val document =
                        documentRepository.save(
                            Document(
                                name = documentName,
                                collectionId = collection.id,
                                data = if (index == pathList.collections.lastIndex) documentData else null,
                            ),
                        )

                    CollectionWrapper(document.id, document.name, CollectionWrapperType.Document, scopeToUserId)
                }
        }

        return toJson(lastCollectionWrapper)
    }

    /**
     * Takes a request object with a path structure: collections/{collectionName}/{documentName}/.../
     * and returns the Document or Collection if it exists within that path.
     *
     * @param request The request to explode into collection/document parts.
     * @param studyId The studyId to store the objects against.
     * @param scopeToUserId (Optional) Scope the request to the author of the document.
     *
     * @return The Collection or Document if it exists.
     */
    fun getAllFromDocumentPath(
        request: HttpServletRequest,
        studyId: String,
        scopeToUserId: String?,
    ): String? {
        val pathList = requestToPathList(request, scopeToUserId)

        var lastCollectionWrapper: CollectionWrapper? = null

        pathList.collections.forEachIndexed { index, wrappedCollection ->

            val collection =
                collectionRepository.findByNameAndStudyIdAndDocumentId(
                    wrappedCollection.name,
                    studyId,
                    lastCollectionWrapper?.id,
                )
                    .orElseThrow {
                        ResourceNotFoundException(
                            validationMessages.get(
                                "document.get_with_all_params.not_found",
                                studyId,
                                wrappedCollection.name,
                                lastCollectionWrapper?.id!!,
                            ),
                        )
                    }

            lastCollectionWrapper =
                if (index >= pathList.documents.size) {
                    CollectionWrapper(
                        collection.id,
                        collection.name,
                        CollectionWrapperType.Collection,
                        scopeToUserId,
                    )
                } else {
                    val document =
                        documentRepository.findByNameAndCollectionId(
                            pathList.documents[index].name,
                            collection.id,
                        ).orElseThrow {
                            ResourceNotFoundException(
                                validationMessages.get(
                                    "document.get_params.not_found",
                                    pathList.documents[index].name,
                                    collection.id,
                                ),
                            )
                        }
                    CollectionWrapper(
                        document.id,
                        document.name,
                        CollectionWrapperType.Document,
                        scopeToUserId,
                    )
                }
        }

        return toJson(lastCollectionWrapper)
    }

    /**
     * The [requestToUrlPath] function converts a request object to a URL path.
     *
     * @param request The [request] object.
     * @return The converted request object to URL path.
     */
    fun requestToUrlPath(request: HttpServletRequest): String {
        return request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE) as String
    }

    private fun requestToPathList(
        request: HttpServletRequest,
        scopeToUserId: String?,
    ): DocumentPath {
        val urlPath = requestToUrlPath(request)

        val documentPath =
            "(?<=collections\\/)(.*)".toRegex()
                .find(urlPath)
                ?.value
                ?.split("/")
                .orEmpty()

        val collections =
            documentPath
                .filterIndexed { index, _ -> index % 2 == 0 }
                .filter { value -> value.isNotBlank() }
                .map { collectionName ->
                    CollectionWrapper(
                        null,
                        collectionName.lowercase(Locale.getDefault()),
                        CollectionWrapperType.Collection,
                        scopeToUserId,
                    )
                }

        val documents =
            documentPath
                .filterIndexed { index, _ -> index % 2 != 0 }
                .filter { value -> value.isNotBlank() }
                .map { documentName ->
                    CollectionWrapper(
                        null,
                        documentName.lowercase(Locale.getDefault()),
                        CollectionWrapperType.Document,
                        scopeToUserId,
                    )
                }

        return DocumentPath(collections = collections, documents = documents)
    }

    private fun toJson(collectionWrapper: CollectionWrapper?): String? {
        if (collectionWrapper == null) return null

        val mapper = JsonMapper.builder().build()

        return when (collectionWrapper.type) {
            CollectionWrapperType.Collection -> {
                val collection =
                    collectionRepository.findById(collectionWrapper.id!!)
                        .orElseThrow {
                            ResourceNotFoundException(
                                "Collection not found with id ${collectionWrapper.id}",
                            )
                        }

                collection.documents = collectionWrapper.scopeToUserAccountId?.let { userId ->
                    collection.documents?.filter { document ->
                        document.createdBy == userId
                    }
                } ?: collection.documents

                mapper.writeValueAsString(collection)
            }

            CollectionWrapperType.Document -> {
                val document =
                    documentRepository.findById(collectionWrapper.id!!)
                        .filter { document ->
                            collectionWrapper.scopeToUserAccountId?.let { userId ->
                                document.createdBy == userId
                            } ?: true
                        }
                        .orElseThrow {
                            LOGGER.warn(
                                "Current account has no access to the document with id = ${collectionWrapper.id}.",
                            )
                            ResourceNotFoundException(
                                validationMessages.get(
                                    "document.access.not_granted",
                                    collectionWrapper.id.toString(),
                                ),
                            )
                        }

                mapper.writeValueAsString(document)
            }
        }
    }
}
