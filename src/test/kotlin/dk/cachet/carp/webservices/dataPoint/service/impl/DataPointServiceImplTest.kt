package dk.cachet.carp.webservices.dataPoint.service.impl

import dk.cachet.carp.common.application.UUID
import dk.cachet.carp.webservices.common.configuration.internationalisation.service.MessageBase
import dk.cachet.carp.webservices.dataPoint.domain.DataPoint
import dk.cachet.carp.webservices.dataPoint.repository.DataPointRepository
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
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Nested
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.jpa.domain.Specification
import kotlin.test.Test
import kotlin.test.assertEquals

class DataPointServiceImplTest {
    private val dataPointRepository: DataPointRepository = mockk()
    private val authenticationService: AuthenticationService = mockk()
    private val validateMessage: MessageBase = mockk()

    private val sut =
        DataPointServiceImpl(
            dataPointRepository,
            authenticationService,
            validateMessage,
        )

    private val deploymentId = "deployment-123"
    private val accountId = UUID.randomUUID()
    private val pageRequest = PageRequest.of(0, 10)

    @Nested
    inner class GetAll {
        @Test
        fun `researcher query is scoped to the deployment via the specification, not the raw query string`() =
            runTest {
                every { authenticationService.getRole() } returns Role.RESEARCHER
                every { authenticationService.getId() } returns accountId
                val mockDataPoints = listOf(mockk<DataPoint>(relaxed = true))
                every {
                    dataPointRepository.findAll(ofType<Specification<DataPoint>>(), pageRequest)
                } returns PageImpl(mockDataPoints)

                val result = sut.getAll(deploymentId, pageRequest, "storageName==*,foo==bar")

                assertEquals(mockDataPoints, result)
                verify(exactly = 1) { dataPointRepository.findAll(ofType<Specification<DataPoint>>(), pageRequest) }
            }

        @Test
        fun `participant query is additionally scoped to their own account via the specification`() =
            runTest {
                every { authenticationService.getRole() } returns Role.PARTICIPANT
                every { authenticationService.getId() } returns accountId
                val mockDataPoints = listOf(mockk<DataPoint>(relaxed = true))
                every {
                    dataPointRepository.findAll(ofType<Specification<DataPoint>>(), pageRequest)
                } returns PageImpl(mockDataPoints)

                val result = sut.getAll(deploymentId, pageRequest, "storageName==*,foo==bar")

                assertEquals(mockDataPoints, result)
                verify(exactly = 1) { dataPointRepository.findAll(ofType<Specification<DataPoint>>(), pageRequest) }
            }

        @Test
        fun `falls back to deployment-scoped lookup when no query is given`() =
            runTest {
                every { authenticationService.getRole() } returns Role.RESEARCHER
                every { authenticationService.getId() } returns accountId
                val mockDataPoints = listOf(mockk<DataPoint>(relaxed = true))
                every {
                    dataPointRepository.findByDeploymentIdAndCreatedBy(
                        deploymentId,
                        accountId.stringRepresentation,
                        pageRequest,
                    )
                } returns PageImpl(mockDataPoints)

                val result = sut.getAll(deploymentId, pageRequest, null)

                assertEquals(mockDataPoints, result)
                verify(exactly = 0) { dataPointRepository.findAll(ofType<Specification<DataPoint>>(), pageRequest) }
            }
    }

    @Nested
    inner class GetNumberOfDataPoints {
        @Test
        fun `count is scoped to the deployment via the specification, not the raw query string`() {
            every { authenticationService.getRole() } returns Role.RESEARCHER
            every { authenticationService.getId() } returns accountId
            every { dataPointRepository.count(ofType<Specification<DataPoint>>()) } returns 3L

            val result = sut.getNumberOfDataPoints(deploymentId, "storageName==*,foo==bar")

            assertEquals(3L, result)
            verify(exactly = 1) { dataPointRepository.count(ofType<Specification<DataPoint>>()) }
        }

        @Test
        fun `participant count is additionally scoped to their own account via the specification`() {
            every { authenticationService.getRole() } returns Role.PARTICIPANT
            every { authenticationService.getId() } returns accountId
            every { dataPointRepository.count(ofType<Specification<DataPoint>>()) } returns 1L

            val result = sut.getNumberOfDataPoints(deploymentId, "storageName==*,foo==bar")

            assertEquals(1L, result)
            verify(exactly = 1) { dataPointRepository.count(ofType<Specification<DataPoint>>()) }
        }

        @Test
        fun `falls back to deployment count when no query is given`() {
            every { authenticationService.getRole() } returns Role.RESEARCHER
            every { authenticationService.getId() } returns accountId
            every {
                dataPointRepository.countByDeploymentIdAndCreatedBy(deploymentId, accountId.stringRepresentation)
            } returns 5L

            val result = sut.getNumberOfDataPoints(deploymentId, null)

            assertEquals(5L, result)
            verify(exactly = 0) { dataPointRepository.count(ofType<Specification<DataPoint>>()) }
        }
    }

    /**
     * Proves that a comma injected into the user-supplied RSQL query (which parses as a top-level OR,
     * since RSQL AND binds tighter than OR) can no longer escape the deployment/account scoping — the
     * scope is ANDed onto the parsed [Specification] rather than concatenated into the RSQL string.
     */
    @Nested
    inner class ScopeCannotBeBypassedByOrInjection {
        @Test
        fun `deployment and account scoping still apply to a query with a top-level OR`() {
            every { authenticationService.getRole() } returns Role.PARTICIPANT
            every { authenticationService.getId() } returns accountId

            val specSlot = slot<Specification<DataPoint>>()
            every { dataPointRepository.count(capture(specSlot)) } returns 0L

            // A comma is RSQL's OR operator; a naive "$query;deployment_id==X" concatenation would let
            // this branch match every row in the table, regardless of deployment or owner.
            sut.getNumberOfDataPoints(deploymentId, "foo==bar,baz==bar")

            val root = mockk<Root<DataPoint>>()
            val query = mockk<CriteriaQuery<*>>()
            val builder = mockk<CriteriaBuilder>(relaxed = true)

            val fooPath = mockk<Path<String>>()
            val bazPath = mockk<Path<String>>()
            val deploymentPath = mockk<Path<String>>()
            val createdByPath = mockk<Path<String>>()

            every { fooPath.javaType } returns String::class.java
            every { bazPath.javaType } returns String::class.java
            every { root.get<String>("foo") } returns fooPath
            every { root.get<String>("baz") } returns bazPath
            every { root.get<String>("deploymentId") } returns deploymentPath
            every { root.get<String>("createdBy") } returns createdByPath

            val fooPredicate = mockk<Predicate>()
            val bazPredicate = mockk<Predicate>()
            val deploymentPredicate = mockk<Predicate>()
            val createdByPredicate = mockk<Predicate>()
            every { builder.like(fooPath, "bar") } returns fooPredicate
            every { builder.like(bazPath, "bar") } returns bazPredicate
            every { builder.equal(deploymentPath, deploymentId) } returns deploymentPredicate
            every { builder.equal(createdByPath, accountId.stringRepresentation) } returns createdByPredicate

            specSlot.captured.toPredicate(root, query, builder)

            // Whatever the user's OR clause matched, it is still ANDed with both the deployment and the
            // account scope — neither can be short-circuited by the injected OR. Disambiguate against
            // CriteriaBuilder's `and(Predicate...)` overload by matching the fixed-arity
            // `and(Expression<Boolean>, Expression<Boolean>)` signature explicitly.
            val deploymentPredicateAsExpr: Expression<Boolean> = deploymentPredicate
            val createdByPredicateAsExpr: Expression<Boolean> = createdByPredicate
            verify(exactly = 1) { builder.equal(deploymentPath, deploymentId) }
            verify(exactly = 1) { builder.equal(createdByPath, accountId.stringRepresentation) }
            verify(exactly = 1) { builder.and(any<Expression<Boolean>>(), refEq(deploymentPredicateAsExpr)) }
            verify(exactly = 1) { builder.and(any<Expression<Boolean>>(), refEq(createdByPredicateAsExpr)) }
        }
    }
}
