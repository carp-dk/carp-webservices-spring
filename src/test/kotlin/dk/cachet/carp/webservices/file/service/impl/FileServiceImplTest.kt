package dk.cachet.carp.webservices.file.service.impl

import dk.cachet.carp.common.application.UUID
import dk.cachet.carp.webservices.common.configuration.internationalisation.service.MessageBase
import dk.cachet.carp.webservices.file.domain.File
import dk.cachet.carp.webservices.file.repository.FileRepository
import dk.cachet.carp.webservices.file.service.FileStorage
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
import org.springframework.data.jpa.domain.Specification
import software.amazon.awssdk.services.s3.S3Client
import kotlin.test.Test
import kotlin.test.assertEquals

class FileServiceImplTest {
    private val fileRepository: FileRepository = mockk()
    private val fileStorage: FileStorage = mockk()
    private val validateMessages: MessageBase = mockk()
    private val s3Client: S3Client = mockk()
    private val authenticationService: AuthenticationService = mockk()

    private val sut =
        FileServiceImpl(
            fileRepository,
            fileStorage,
            validateMessages,
            s3Client,
            authenticationService,
            "bucket",
            "https://endpoint",
        )

    private val studyId = "study-123"
    private val accountId = UUID.randomUUID()

    @Nested
    inner class GetAllByOriginalName {
        @Test
        fun `researcher lookup is not scoped to their own account`() {
            every { authenticationService.getRole() } returns Role.RESEARCHER
            every { authenticationService.getId() } returns accountId
            val mockFiles = listOf(mockk<File>(relaxed = true))
            every { fileRepository.findByStudyIdAndOriginalName(studyId, "photo.jpg") } returns mockFiles

            val result = sut.getAll(query = null, originalName = "photo.jpg", studyId = studyId)

            assertEquals(mockFiles, result)
            verify(exactly = 0) {
                fileRepository.findByStudyIdAndOriginalNameAndCreatedBy(any(), any(), any())
            }
        }

        @Test
        fun `participant lookup is scoped to their own account`() {
            every { authenticationService.getRole() } returns Role.PARTICIPANT
            every { authenticationService.getId() } returns accountId
            val mockFiles = listOf(mockk<File>(relaxed = true))
            every {
                fileRepository.findByStudyIdAndOriginalNameAndCreatedBy(
                    studyId,
                    "photo.jpg",
                    accountId.stringRepresentation,
                )
            } returns mockFiles

            val result = sut.getAll(query = null, originalName = "photo.jpg", studyId = studyId)

            assertEquals(mockFiles, result)
            verify(exactly = 0) { fileRepository.findByStudyIdAndOriginalName(any(), any()) }
        }
    }

    @Nested
    inner class GetAll {
        @Test
        fun `falls back to unfiltered study listing for a fully authorized caller with no query`() {
            every { authenticationService.getRole() } returns Role.RESEARCHER
            every { authenticationService.getId() } returns accountId
            val mockFiles = listOf(mockk<File>(relaxed = true))
            every { fileRepository.findByStudyId(studyId) } returns mockFiles

            val result = sut.getAll(query = null, originalName = null, studyId = studyId)

            assertEquals(mockFiles, result)
        }

        @Test
        fun `falls back to own-files listing for a non-researcher with no query`() {
            every { authenticationService.getRole() } returns Role.PARTICIPANT
            every { authenticationService.getId() } returns accountId
            val mockFiles = listOf(mockk<File>(relaxed = true))
            every {
                fileRepository.findByStudyIdAndCreatedBy(studyId, accountId.stringRepresentation)
            } returns mockFiles

            val result = sut.getAll(query = null, originalName = null, studyId = studyId)

            assertEquals(mockFiles, result)
        }

        @Test
        fun `query is scoped to the study via the specification, not the raw query string`() {
            every { authenticationService.getRole() } returns Role.RESEARCHER
            every { authenticationService.getId() } returns accountId
            every { fileRepository.findAll(ofType<Specification<File>>()) } returns emptyList()

            sut.getAll(query = "fileName==*,other==bar", originalName = null, studyId = studyId)

            verify(exactly = 1) { fileRepository.findAll(ofType<Specification<File>>()) }
        }
    }

    /**
     * Proves that a comma injected into the user-supplied RSQL query (which parses as a top-level OR,
     * since RSQL AND binds tighter than OR) can no longer escape the study/account scoping — the scope
     * is ANDed onto the parsed [Specification] rather than concatenated into the RSQL string.
     */
    @Nested
    inner class ScopeCannotBeBypassedByOrInjection {
        @Test
        fun `study and account scoping still apply to a query with a top-level OR`() {
            every { authenticationService.getRole() } returns Role.PARTICIPANT
            every { authenticationService.getId() } returns accountId

            val specSlot = slot<Specification<File>>()
            every { fileRepository.findAll(capture(specSlot)) } returns emptyList()

            // A comma is RSQL's OR operator; a naive "$query;study_id==X" concatenation would let this
            // branch match every row in the table, regardless of study or owner.
            sut.getAll(query = "foo==bar,baz==bar", originalName = null, studyId = studyId)

            val root = mockk<Root<File>>()
            val query = mockk<CriteriaQuery<*>>()
            val builder = mockk<CriteriaBuilder>(relaxed = true)

            val fooPath = mockk<Path<String>>()
            val bazPath = mockk<Path<String>>()
            val studyPath = mockk<Path<String>>()
            val createdByPath = mockk<Path<String>>()

            every { fooPath.javaType } returns String::class.java
            every { bazPath.javaType } returns String::class.java
            every { root.get<String>("foo") } returns fooPath
            every { root.get<String>("baz") } returns bazPath
            every { root.get<String>("studyId") } returns studyPath
            every { root.get<String>("createdBy") } returns createdByPath

            val fooPredicate = mockk<Predicate>()
            val bazPredicate = mockk<Predicate>()
            val studyPredicate = mockk<Predicate>()
            val createdByPredicate = mockk<Predicate>()
            every { builder.like(fooPath, "bar") } returns fooPredicate
            every { builder.like(bazPath, "bar") } returns bazPredicate
            every { builder.equal(studyPath, studyId) } returns studyPredicate
            every { builder.equal(createdByPath, accountId.stringRepresentation) } returns createdByPredicate

            specSlot.captured.toPredicate(root, query, builder)

            // Whatever the user's OR clause matched, it is still ANDed with both the study and the
            // account scope. Disambiguate against `and(Predicate...)` by matching the fixed-arity
            // `and(Expression<Boolean>, Expression<Boolean>)` signature explicitly.
            val studyPredicateAsExpr: Expression<Boolean> = studyPredicate
            val createdByPredicateAsExpr: Expression<Boolean> = createdByPredicate
            verify(exactly = 1) { builder.equal(studyPath, studyId) }
            verify(exactly = 1) { builder.equal(createdByPath, accountId.stringRepresentation) }
            verify(exactly = 1) { builder.and(any<Expression<Boolean>>(), refEq(studyPredicateAsExpr)) }
            verify(exactly = 1) { builder.and(any<Expression<Boolean>>(), refEq(createdByPredicateAsExpr)) }
        }
    }
}
