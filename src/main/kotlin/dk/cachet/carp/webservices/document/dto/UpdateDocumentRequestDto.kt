package dk.cachet.carp.webservices.document.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import tools.jackson.databind.JsonNode

/**
 * The Data Class [UpdateDocumentRequestDto].
 * The [UpdateDocumentRequestDto] represents a document request with the given [name] and [data].
 */
data class UpdateDocumentRequestDto(
    /** The [name] of the document. */
    @field:NotBlank
    val name: String?,
    /** The [data] object containing the document information. */
    @field:NotNull
    var data: JsonNode? = null,
)
