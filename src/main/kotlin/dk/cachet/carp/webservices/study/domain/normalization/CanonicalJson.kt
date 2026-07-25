package dk.cachet.carp.webservices.study.domain.normalization

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * Order-insensitive canonical form of a JSON element: object keys are sorted **and** array elements
 * are sorted (recursively). Used to compare serialized recruitment snapshots for equality where:
 *  - kotlinx serializes `Set`s as arrays in arbitrary order, and
 *  - a stored blob may carry a different key order than current serialization produces (older core
 *    version / migration artifact).
 *
 * Sorting keys is essential: sorting arrays by element `toString()` is only stable if the elements'
 * keys are already normalized, otherwise the same object sorts differently on each side and a
 * multi-element array comparison spuriously fails.
 */
object CanonicalJson {
    fun canonicalize(element: JsonElement): JsonElement =
        when (element) {
            is JsonObject ->
                JsonObject(element.entries.sortedBy { it.key }.associate { it.key to canonicalize(it.value) })
            is JsonArray -> JsonArray(element.map(::canonicalize).sortedBy { it.toString() })
            else -> element
        }
}
