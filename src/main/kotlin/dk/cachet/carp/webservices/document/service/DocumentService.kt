package dk.cachet.carp.webservices.document.service

import dk.cachet.carp.webservices.document.domain.Document
import dk.cachet.carp.webservices.document.dto.CreateDocumentRequestDto
import dk.cachet.carp.webservices.document.dto.UpdateDocumentRequestDto
import jakarta.servlet.http.HttpServletRequest
import org.springframework.data.domain.PageRequest
import tools.jackson.databind.JsonNode

/**
 * The Interface [DocumentService].
 * [DocumentService] creates an interface for handling document requests.
 */
interface DocumentService {
    /** The [getAll] interface retrieves all documents with the given [studyId] and [query] parameters. */
    fun getAll(
        pageRequest: PageRequest,
        query: String?,
        studyId: String,
    ): List<Document>

    /** The [getAll] interface retrieves all documents that belong to one of the specified collection id's. */
    fun getAll(collectionIds: List<Int>): List<Document>

    /** The [getByDocumentPath] interface for retrieving documents by its [studyId]. */
    fun getByDocumentPath(
        studyId: String,
        request: HttpServletRequest,
    ): String?

    /** The [getOne] interface for retrieving one documents by its [id]. */
    fun getOne(id: Int): Document

    /** The [createByDocumentPath] interface for creating documents by its [studyId] and [data] object requested. */
    fun createByDocumentPath(
        studyId: String,
        data: JsonNode?,
        request: HttpServletRequest,
    ): String?

    /** The [create] interface for creating document by [CreateDocumentRequestDto] object requested. */
    fun create(request: CreateDocumentRequestDto): Document

    /** The [delete] interface for deleting document by its [id]. */
    fun delete(id: Int)

    /** The [update] interface for updating document (replacing) by its [id] and [updateRequest] object request. */
    fun update(
        id: Int,
        updateRequest: UpdateDocumentRequestDto,
    ): Document

    /** The [append] interface for updating document (appending) by its [id] and [updateRequest] object request. */
    fun append(
        id: Int,
        updateRequest: UpdateDocumentRequestDto,
    ): Document
}
