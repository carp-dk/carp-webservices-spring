package dk.cachet.carp.webservices.document.repository

import dk.cachet.carp.webservices.document.domain.Document
import dk.cachet.carp.webservices.document.dto.UpdateDocumentRequestDto
import org.springframework.context.annotation.Lazy
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import tools.jackson.databind.node.ObjectNode
import java.util.*

@Repository
interface DocumentRepository :
    JpaRepository<Document, Int>,
    JpaSpecificationExecutor<Document>,
    DocumentRepositoryCustom {
    fun findByNameAndCollectionId(
        name: String,
        collectionId: Int,
    ): Optional<Document>

    @Query(value = "SELECT d FROM documents d LEFT JOIN FETCH d.collections WHERE d.collectionId IN (:collectionIds)")
    fun findAllByCollectionsIdsWithDocuments(
        @Param(value = "collectionIds") collectionIds: Collection<Int>,
    ): List<Document>

    @Modifying
    @Transactional
    @Query(
        nativeQuery = true,
        value = "DELETE FROM documents WHERE collection_id IN (:collectionIds)",
    )
    fun deleteAllByCollectionIds(
        @Param(value = "collectionIds") collectionIds: Collection<Int>,
    )
}

/**
 * The Interface [DocumentRepositoryCustom].
 * The [DocumentRepositoryCustom] creates an interface for handling document requests.
 */
interface DocumentRepositoryCustom {
    fun update(
        id: Int,
        document: UpdateDocumentRequestDto,
    ): Optional<Document>

    fun appendDocument(
        id: Int,
        document: UpdateDocumentRequestDto,
    ): Optional<Document>
}

/**
 * The Repository [DocumentRepositoryImpl].
 * The [DocumentRepositoryImpl] implements the repository logout for [DocumentRepositoryCustom] interface.
 */
@Repository
class DocumentRepositoryImpl(
    @Lazy private val documentRepository: DocumentRepository,
) : DocumentRepositoryCustom {
    /**
     * The function [update] updates the [Document] with the given [id] and [document] parameters.
     *
     * @param id The [id] of the document to update.
     * @param document The [document] requested to update the existing document.
     * @return The [Document] updated.
     */
    override fun update(
        id: Int,
        document: UpdateDocumentRequestDto,
    ): Optional<Document> {
        documentRepository.findById(id).map { existingDocument ->
            document.name?.also { existingDocument.name = it }

            if (existingDocument.data?.isArray!!) (existingDocument.data as JsonNode).removeAll { true }

            if (existingDocument.data?.isObject!!) (existingDocument.data as ObjectNode).removeAll()

            document.data?.also {
                val updatedData =
                    JsonMapper.builder()
                        .defaultMergeable(true)
                        .build()
                        .readerForUpdating(existingDocument.data)
                        .readValue<JsonNode>(it)

                existingDocument.data = updatedData
            }

            documentRepository.save(existingDocument)
        }

        return documentRepository.findById(id)
    }

    /**
     * The function [appendDocument] updates the [Document] with the given [id] and [document] object request.
     *
     * @param id The [id] of the document to update.
     * @param document The [document] requested to update the existing document.
     * @return The updated (appended) [Document].
     */
    override fun appendDocument(
        id: Int,
        document: UpdateDocumentRequestDto,
    ): Optional<Document> {
        documentRepository.findById(id)
            .map { existingDocument ->
                document.name?.also { existingDocument.name = it }

                document.data?.also {
                    val updatedData =
                        JsonMapper.builder()
                            .defaultMergeable(true)
                            .build()
                            .readerForUpdating(existingDocument.data)
                            .readValue<JsonNode>(it)

                    existingDocument.data = updatedData
                }

                documentRepository.save(existingDocument)
            }

        return documentRepository.findById(id)
    }
}
