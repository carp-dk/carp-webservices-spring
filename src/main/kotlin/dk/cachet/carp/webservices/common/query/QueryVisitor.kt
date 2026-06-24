package dk.cachet.carp.webservices.common.query

import cz.jirutka.rsql.parser.ast.AndNode
import cz.jirutka.rsql.parser.ast.ComparisonNode
import cz.jirutka.rsql.parser.ast.OrNode
import cz.jirutka.rsql.parser.ast.RSQLVisitor
import org.springframework.data.jpa.domain.Specification

/**
 * The Class [QueryVisitor].
 * The [QueryVisitor] implements the RSQL Specification visitor interface.
 */
class QueryVisitor<T : Any> : RSQLVisitor<Specification<T>, Void> {
    // The [QueryBuilder] to build the query builder.
    private val builder: QueryBuilder<T> = QueryBuilder()

    /**
     * The function [visit] creates the [Specification] with the given [andNode] and [param].
     *
     * @param andNode The [andNode] operator to be invoked.
     * @param param The [param] of the request.
     * @return The created [Specification] with the given parameter applied.
     */
    override fun visit(
        andNode: AndNode,
        param: Void?,
    ): Specification<T> {
        return builder.createSpecification(andNode)
    }

    /**
     * The function [visit] creates the [Specification] with the given [orNode] and [param].
     *
     * @param orNode The [OrNode] operator to be invoked.
     * @param param The [param] of the request.
     * @return The created [Specification] with the given parameter applied.
     */
    override fun visit(
        orNode: OrNode,
        param: Void?,
    ): Specification<T> {
        return builder.createSpecification(orNode)
    }

    /**
     * The function [visit] creates a [Specification] with the given [comparisonNode] and [params].
     *
     * @param comparisonNode The [comparisonNode] operator to be applied.
     * @param params The [params] of the request.
     * @return The created [Specification] with the given parameter applied.
     */
    override fun visit(
        comparisonNode: ComparisonNode,
        params: Void?,
    ): Specification<T>? {
        return builder.createSpecification(comparisonNode)
    }
}
