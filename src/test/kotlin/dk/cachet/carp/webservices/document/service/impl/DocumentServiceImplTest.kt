package dk.cachet.carp.webservices.document.service.impl

import dk.cachet.carp.common.application.UUID
import dk.cachet.carp.webservices.collection.domain.Collection
import dk.cachet.carp.webservices.collection.service.CollectionService
import dk.cachet.carp.webservices.common.configuration.internationalisation.service.MessageBase
import dk.cachet.carp.webservices.document.domain.Document
import dk.cachet.carp.webservices.document.repository.DocumentRepository
import dk.cachet.carp.webservices.security.authentication.service.AuthenticationService
import dk.cachet.carp.webservices.security.authorization.Role
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import jakarta.persistence.criteria.CriteriaBuilder
import jakarta.persistence.criteria.CriteriaQuery
import jakarta.persistence.criteria.Expression
import jakarta.persistence.criteria.Path
import jakarta.persistence.criteria.Predicate
import jakarta.persistence.criteria.Root
import org.junit.jupiter.api.Nested
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.jpa.domain.Specification
import kotlin.test.Test

class DocumentServiceImplTest {
    private val documentRepository: DocumentRepository = mockk()
    private val documentTraverser: DocumentTraverser = mockk()
    private val validationMessages: MessageBase = mockk()
    private val authenticationService: AuthenticationService = mockk()
    private val collectionService: CollectionService = mockk()

    private val sut =
        DocumentServiceImpl(
            documentRepository,
            documentTraverser,
            validationMessages,
            authenticationService,
            collectionService,
        )

    private val studyId = "study-123"
    private val accountId = UUID.randomUUID()
    private val pageRequest = PageRequest.of(0, 10)

    /**
     * Proves that a comma injected into the user-supplied RSQL query (which parses as a top-level OR,
     * since RSQL AND binds tighter than OR) can no longer escape the per-account scoping applied to
     * non-fully-authorized callers — the scope is ANDed onto the parsed [Specification] rather than
     * concatenated into the RSQL string. The outer study scope was already applied this way (via
     * [dk.cachet.carp.webservices.document.filter.DocumentSpecifications.belongsToStudyId]) and is
     * asserted here too, for completeness.
     */
    @Nested
    inner class ScopeCannotBeBypassedByOrInjection {
        @Test
        fun `study and account scoping still apply to a query with a top-level OR`() {
            every { authenticationService.getRole() } returns Role.PARTICIPANT
            every { authenticationService.getId() } returns accountId

            val specSlot = slot<Specification<Document>>()
            every { documentRepository.findAll(capture(specSlot), pageRequest) } returns PageImpl(emptyList())

            // A comma is RSQL's OR operator; a naive "$query;created_by==X" concatenation would let
            // this branch match every other user's documents in the study.
            sut.getAll(pageRequest, "foo==bar,baz==bar", studyId)

            val root = mockk<Root<Document>>()
            val query = mockk<CriteriaQuery<*>>()
            val builder = mockk<CriteriaBuilder>(relaxed = true)

            val fooPath = mockk<Path<String>>()
            val bazPath = mockk<Path<String>>()
            val collectionPath = mockk<Path<Collection>>()
            val studyIdPath = mockk<Path<Int>>()
            val createdByPath = mockk<Path<String>>()

            every { fooPath.javaType } returns String::class.java
            every { bazPath.javaType } returns String::class.java
            every { root.get<String>("foo") } returns fooPath
            every { root.get<String>("baz") } returns bazPath
            every { root.get<Collection>("collection") } returns collectionPath
            every { collectionPath.get<Int>("studyId") } returns studyIdPath
            every { root.get<String>("createdBy") } returns createdByPath

            val fooPredicate = mockk<Predicate>()
            val bazPredicate = mockk<Predicate>()
            val studyPredicate = mockk<Predicate>()
            val createdByPredicate = mockk<Predicate>()
            every { builder.like(fooPath, "bar") } returns fooPredicate
            every { builder.like(bazPath, "bar") } returns bazPredicate
            every { builder.equal(studyIdPath, studyId) } returns studyPredicate
            every { builder.equal(createdByPath, accountId.stringRepresentation) } returns createdByPredicate

            specSlot.captured.toPredicate(root, query, builder)

            // Whatever the user's OR clause matched, it is still ANDed with both the study and the
            // account scope. Disambiguate against `and(Predicate...)` by matching the fixed-arity
            // `and(Expression<Boolean>, Expression<Boolean>)` signature explicitly.
            val studyPredicateAsExpr: Expression<Boolean> = studyPredicate
            val createdByPredicateAsExpr: Expression<Boolean> = createdByPredicate
            verify(exactly = 1) { builder.equal(studyIdPath, studyId) }
            verify(exactly = 1) { builder.equal(createdByPath, accountId.stringRepresentation) }
            verify(exactly = 1) { builder.and(any<Expression<Boolean>>(), refEq(studyPredicateAsExpr)) }
            verify(exactly = 1) { builder.and(any<Expression<Boolean>>(), refEq(createdByPredicateAsExpr)) }
        }
    }
}
