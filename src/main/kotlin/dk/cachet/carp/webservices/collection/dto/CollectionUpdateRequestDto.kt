package dk.cachet.carp.webservices.collection.dto

import jakarta.validation.constraints.NotBlank
import tools.jackson.databind.PropertyNamingStrategies
import tools.jackson.databind.annotation.JsonNaming
import java.io.Serializable

/**
 * The Data Class [CollectionUpdateRequestDto].
 * The [CollectionUpdateRequestDto] represents a user updated collection request, with the given [name].
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class CollectionUpdateRequestDto(
    /** The [name] of the collection. */
    @field:NotBlank
    val name: String,
) : Serializable
