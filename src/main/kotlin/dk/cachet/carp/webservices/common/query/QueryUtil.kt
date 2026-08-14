package dk.cachet.carp.webservices.common.query

import dk.cachet.carp.webservices.common.extensions.toCamelCase
import org.springframework.data.domain.Sort

/**
 * The Class [QueryUtil].
 * The [QueryUtil] implements the functionality for sorting the queries based on the given sort criteria.
 */
object QueryUtil {
    /**
     * The function [sort] endpoints enables queries to sort the results based on the given parameters
     * i.e., ascending (asc), descending (desc)
     *
     * @param queryParam The [queryParam] parameter containing the sort criteria.
     * @return The [Sort]ed list of results for the given parameter.
     */
    fun sort(queryParam: String?): Sort {
        queryParam?.let {
            val sortParts = queryParam.split(",")
            val property = toCamelCase(sortParts[0])
            val direction = sortParts[1]

            if (direction == "asc") {
                return Sort.by(property).ascending()
            } else if (direction == "desc") {
                return Sort.by(property).descending()
            }
        }
        return Sort.by("id").descending()
    }

    /**
     * Formatter that can change case of a given String
     *
     * @param value The string [value] to be formatted into camel case format.
     * @return The lower case format.
     */
    fun toCamelCase(value: String): String {
        return value.toCamelCase()
    }

    /**
     * Query validator of a given String
     *
     * @param query The [query] to be replaced ascii value.
     * @return The query string value.
     */
    fun validateQuery(query: String): String {
        val queryValidation = query.replace("%3E", ">")
        return queryValidation.replace("%3C", "<")
    }
}
