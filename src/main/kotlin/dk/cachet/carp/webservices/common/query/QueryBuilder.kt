package dk.cachet.carp.webservices.common.query

import cz.jirutka.rsql.parser.ast.ComparisonNode
import cz.jirutka.rsql.parser.ast.LogicalNode
import cz.jirutka.rsql.parser.ast.LogicalOperator
import cz.jirutka.rsql.parser.ast.Node
import org.springframework.data.jpa.domain.Specification
import java.util.*
import java.util.stream.Collectors

/**
 * The Class [QueryBuilder].
 * The [QueryBuilder] implements the RSQL Query Specification functionality to build a nested query.
 */
class QueryBuilder<T : Any> {
    private fun createSpecification(node: Node): Specification<T>? {
        if (node is LogicalNode) {
            return createSpecification(node)
        }
        return if (node is ComparisonNode) {
            createSpecification(node)
        } else {
            null
        }
    }

    /**
     * The function [createSpecification] creates a specification for a [LogicalNode].
     *
     * @param logicalNode The [logicalNode] of the a specification.
     * @return The [Specification] object created.
     */
    fun createSpecification(logicalNode: LogicalNode): Specification<T> {
        val specs =
            logicalNode.children
                .stream()
                .map { node -> createSpecification(node) }
                .filter(({ Objects.nonNull(it) }))
                .collect(Collectors.toList<Specification<T>>())

        var result = specs.first()

        if (logicalNode.operator == LogicalOperator.AND) {
            for (i in 1 until specs.size) {
                result = Specification.where(result).and(specs[i])
            }
        } else if (logicalNode.operator == LogicalOperator.OR) {
            for (i in 1 until specs.size) {
                result = Specification.where(result).or(specs[i])
            }
        }

        return result
    }

    /**
     * The function [createSpecification] creates a specification for the given [comparisonNode].
     *
     * @param comparisonNode The [comparisonNode] to create a specification.
     * @return The created [Specification] object.
     */
    fun createSpecification(comparisonNode: ComparisonNode): Specification<T>? {
        return Specification.where(
            QuerySpecification<T>(comparisonNode.selector, comparisonNode.operator, comparisonNode.arguments),
        )
    }
}
