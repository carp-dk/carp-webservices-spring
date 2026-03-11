package dk.cachet.carp.webservices.study.domain

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue

enum class SortDirection {
    Asc,
    Desc,
    ;

    @JsonValue
    fun toJson(): String =
        when (this) {
            Asc -> "asc"
            Desc -> "desc"
        }

    companion object {
        @JvmStatic
        @JsonCreator
        fun fromJson(value: String): SortDirection =
            when (value.lowercase()) {
                "asc" -> Asc
                "desc" -> Desc
                else -> throw IllegalArgumentException("Unsupported sortDirection value: $value")
            }
    }
}
