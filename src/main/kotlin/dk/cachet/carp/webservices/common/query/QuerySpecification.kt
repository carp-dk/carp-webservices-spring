package dk.cachet.carp.webservices.common.query

import cz.jirutka.rsql.parser.ast.ComparisonOperator
import cz.jirutka.rsql.parser.ast.RSQLOperators
import jakarta.persistence.criteria.*
import org.springframework.data.jpa.domain.Specification
import org.springframework.lang.Nullable
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.stream.Collectors

/**
 * The Enum Class [QueryOperation].
 * The [QueryOperation] implements the operation execution for the nested queries.
 */
enum class QueryOperation(private val operator: ComparisonOperator) {
    EQUAL(RSQLOperators.EQUAL),
    NOT_EQUAL(RSQLOperators.NOT_EQUAL),
    GREATER_THAN(RSQLOperators.GREATER_THAN),
    GREATER_THAN_OR_EQUAL(RSQLOperators.GREATER_THAN_OR_EQUAL),
    LESS_THAN(RSQLOperators.LESS_THAN),
    LESS_THAN_OR_EQUAL(RSQLOperators.LESS_THAN_OR_EQUAL),
    IN(RSQLOperators.IN),
    NOT_IN(RSQLOperators.NOT_IN),
    ;

    companion object {
        fun getSimpleOperator(operator: ComparisonOperator?): QueryOperation? {
            for (operation in entries) {
                if (operation.operator === operator) {
                    return operation
                }
            }
            return null
        }
    }
}

/**
 * The Class [QuerySpecification].
 * The [QuerySpecification] provides a generic JPA specification which can be used across all database models
 * to query properties and nested properties, including within json objects.
 *
 * e.g. DataPoint [createdByUserId==1 or carpHeader.data_format.name==location].
 *
 * @param property String The property or nested property (e.g. createdByUserId, or carpHeader.data_format.name).
 * @param operator ComparisonOperator? The operator used in the query, e.g. ==, >=, <=, !=, etc.
 * @param arguments List? The list or arguments to query against the property.
 */
class QuerySpecification<T : Any>(
    private var property: String?,
    private val operator: ComparisonOperator?,
    private val arguments: List<String>?,
) : Specification<T> {
    companion object {
        /** The Constant [POSTGRES_JSON_EXTRACT_FUNCTION]. */
        const val POSTGRES_JSON_EXTRACT_FUNCTION = "jsonb_extract_path_text"
    }

    /**
     * The function [toPredicate] allows the query parameters to predicate the criteria set in the request.
     *
     * @param root The [root] request arguments.
     * @param query The [query] parameter to predicate.
     * @param builder The [builder] parameter to build the predicate.
     */
    @Suppress("LongMethod", "CyclomaticComplexMethod")
    override fun toPredicate(
        root: Root<T>,
        query: CriteriaQuery<*>,
        builder: CriteriaBuilder,
    ): Predicate? {
        // If this is a nested query, explode the string into its constituent parts and build an Expression.
        val nestedProperties = extractNestedProperties(property, root, builder)
        val propertyQuery: Expression<String> = nestedProperties ?: root.get(QueryUtil.toCamelCase(property!!))
        val allArguments = if (nestedProperties == null) castArguments(root) else arguments
        val firstArgument: Any = allArguments!!.first()

        return when (QueryOperation.getSimpleOperator(operator)) {
            QueryOperation.EQUAL ->
                {
                    return when (firstArgument) {
                        is String -> builder.like(propertyQuery, firstArgument.toString().replace('*', '%'))
                        else -> builder.equal(propertyQuery, firstArgument)
                    }
                }

            QueryOperation.NOT_EQUAL ->
                {
                    return when (firstArgument) {
                        is String -> builder.notLike(propertyQuery, firstArgument.toString().replace('*', '%'))
                        else -> builder.notEqual(propertyQuery, firstArgument)
                    }
                }

            QueryOperation.GREATER_THAN ->
                {
                    return when (firstArgument) {
                        is Instant -> builder.greaterThan(root.get(QueryUtil.toCamelCase(property!!)), firstArgument)
                        else -> builder.greaterThan(propertyQuery, firstArgument.toString())
                    }
                }

            QueryOperation.GREATER_THAN_OR_EQUAL ->
                {
                    return when (firstArgument) {
                        is Instant ->
                            builder.greaterThanOrEqualTo(
                                root.get(QueryUtil.toCamelCase(property!!)),
                                firstArgument,
                            )
                        else -> builder.greaterThanOrEqualTo(propertyQuery, firstArgument.toString())
                    }
                }

            QueryOperation.LESS_THAN ->
                {
                    return when (firstArgument) {
                        is Instant -> builder.lessThan(root.get(QueryUtil.toCamelCase(property!!)), firstArgument)
                        else -> builder.lessThan(propertyQuery, firstArgument.toString())
                    }
                }

            QueryOperation.LESS_THAN_OR_EQUAL ->
                {
                    return when (firstArgument) {
                        is Instant ->
                            builder.lessThanOrEqualTo(
                                root.get(QueryUtil.toCamelCase(property!!)),
                                firstArgument,
                            )
                        else -> builder.lessThanOrEqualTo(propertyQuery, firstArgument.toString())
                    }
                }
            QueryOperation.IN -> return propertyQuery.`in`(allArguments)
            QueryOperation.NOT_IN -> return builder.not(propertyQuery.`in`(allArguments))
            else -> null
        }
    }

    private fun castArguments(root: Root<T>): List<Any> {
        val type = root.get<Any>(QueryUtil.toCamelCase(property!!)).javaType

        return arguments!!.stream().map { arg ->
            when {
                (type == java.lang.Integer::class.java || type == Int::class.java) -> arg.toInt()
                (type == java.lang.Long::class.java || type == Long::class.java) -> arg.toLong()
                else -> parseString(arg)
            }
        }.collect(Collectors.toList())
    }

    private fun parseString(arg: String): Any {
        return try {
            // If the string looks like a date, convert it to an Instant.
            DateTimeFormatter.ISO_ZONED_DATE_TIME.parse(arg, Instant::from)
        } catch (e: DateTimeParseException) {
            // Otherwise return the string as normal.
            arg
        }
    }

    private fun extractNestedProperties(
        propertiesString: String?,
        root: Root<T>,
        builder: CriteriaBuilder,
    ): Expression<String>? {
        val nestedProperties = propertiesString!!.split('.')
        if (nestedProperties.size > 1) {
            val rootProperty = root.get<Any>(QueryUtil.toCamelCase(nestedProperties.first()))
            val propertyExpressions: List<Expression<String>> = nestedProperties.drop(1).map { builder.literal(it) }

            return builder.function(
                POSTGRES_JSON_EXTRACT_FUNCTION,
                String::class.java,
                rootProperty,
                *propertyExpressions.toTypedArray(),
            )
        }
        return null
    }
}
