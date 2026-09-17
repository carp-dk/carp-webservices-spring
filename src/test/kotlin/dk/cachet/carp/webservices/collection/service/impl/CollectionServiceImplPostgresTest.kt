package dk.cachet.carp.webservices.collection.service.impl

import com.ninjasquad.springmockk.MockkBean
import dk.cachet.carp.webservices.account.service.AccountService
import dk.cachet.carp.webservices.collection.domain.Collection
import dk.cachet.carp.webservices.collection.repository.CollectionRepository
import dk.cachet.carp.webservices.common.audit.JpaAuditConfiguration
import dk.cachet.carp.webservices.common.configuration.internationalisation.service.MessageBase
import dk.cachet.carp.webservices.document.domain.Document
import dk.cachet.carp.webservices.document.repository.DocumentRepository
import dk.cachet.carp.webservices.security.authentication.service.AuthenticationService
import jakarta.persistence.EntityManagerFactory
import org.hibernate.LazyInitializationException
import org.hibernate.SessionFactory
import org.hibernate.stat.Statistics
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.context.annotation.Import
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import tools.jackson.databind.json.JsonMapper
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Exercises the real [CollectionServiceImpl] against a real Postgres, not just raw Hibernate: a
 * regression that reintroduces missing (or wrongly-added) document initialization in production
 * code should be caught here. Mock-based [CollectionServiceImplTest] can't do this - mocked
 * repositories return already-materialized objects, never a real lazy proxy bound to a session
 * that later closes.
 *
 * `@Transactional(propagation = NOT_SUPPORTED)` suspends @DataJpaTest's default auto-rollback
 * wrapper, so calls into `collectionService`/the repositories run in their own genuinely
 * independent, committing transactions - the same way they do in production - instead of sharing
 * one long-lived session for the whole test that would mask the exact bug being guarded against.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
// JpaAuditConfiguration enables @CreatedDate population (createdAt), which the @OrderBy("createdAt
// desc") ordering assertions below depend on - @DataJpaTest doesn't pull this in on its own.
@Import(CollectionServiceImpl::class, JpaAuditConfiguration::class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Testcontainers(disabledWithoutDocker = true)
// Without this, the cached ApplicationContext's connection pool outlives the Testcontainers
// container (stopped once this class's tests finish), causing a slow, noisy shutdown-time
// teardown failure. Evict the context before the container goes away.
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
// The three @MockkBean collaborators below are never referenced directly - they only need to exist
// so CollectionServiceImpl's constructor dependencies resolve to mocks rather than real beans.
@Suppress("UnusedPrivateProperty")
class CollectionServiceImplPostgresTest {
    @Autowired
    private lateinit var collectionService: CollectionServiceImpl

    @Autowired
    private lateinit var collectionRepository: CollectionRepository

    @Autowired
    private lateinit var documentRepository: DocumentRepository

    @Autowired
    private lateinit var entityManagerFactory: EntityManagerFactory

    @MockkBean
    private lateinit var accountService: AccountService

    @MockkBean
    private lateinit var authenticationService: AuthenticationService

    @MockkBean
    private lateinit var validationMessages: MessageBase

    private val mapper = JsonMapper.builder().build()

    // @Transactional(NOT_SUPPORTED) intentionally disables @DataJpaTest's per-test rollback (real
    // commits are the point), so the database isn't reset between tests - a fresh studyId/deploymentId
    // per test avoids cross-test collisions instead.
    private lateinit var studyId: String
    private lateinit var deploymentId: String
    private var collectionId: Int = 0

    @BeforeEach
    fun seed() {
        studyId = "study-${java.util.UUID.randomUUID()}"
        deploymentId = "deployment-${java.util.UUID.randomUUID()}"
        val collection =
            collectionRepository.save(Collection(name = "c1", studyId = studyId, studyDeploymentId = deploymentId))
        collectionId = collection.id
        documentRepository.save(Document(name = "doc-1", collectionId = collectionId))
        // A gap wide enough that createdAt reliably differs, so @OrderBy("createdAt desc") has an
        // unambiguous newest-first order to assert on below.
        Thread.sleep(5)
        documentRepository.save(Document(name = "doc-2", collectionId = collectionId))
    }

    @Test
    fun `getCollectionByStudyIdAndId serializes successfully after the transaction closes`() {
        val result = collectionService.getCollectionByStudyIdAndId(studyId, collectionId)

        // @OrderBy("createdAt desc") on Collection.documents - doc-2 was created after doc-1.
        assertEquals(listOf("doc-2", "doc-1"), result.documents?.map { it.name })
        val json = mapper.writeValueAsString(result)
        assertContains(json, "doc-1")
        assertContains(json, "doc-2")
    }

    @Test
    fun `getCollectionByStudyIdAndByName serializes successfully after the transaction closes`() {
        val result = collectionService.getCollectionByStudyIdAndByName(studyId, "c1")

        val json = mapper.writeValueAsString(result)
        assertContains(json, "doc-1")
        assertContains(json, "doc-2")
    }

    @Test
    fun `getAll with RSQL query serializes successfully after the transaction closes`() {
        val result = collectionService.getAll(studyId, "name=='c1'")

        assertEquals(1, result.size)
        val json = mapper.writeValueAsString(result)
        assertContains(json, "doc-1")
        assertContains(json, "doc-2")
    }

    @Test
    fun `getAllByStudyIdAndDeploymentId serializes successfully after the transaction closes`() {
        val result = collectionService.getAllByStudyIdAndDeploymentId(studyId, deploymentId)

        assertEquals(1, result.size)
        // @OrderBy("createdAt desc") on Collection.documents - doc-2 was created after doc-1.
        assertEquals(listOf("doc-2", "doc-1"), result.single().documents?.map { it.name })
        val json = mapper.writeValueAsString(result)
        assertContains(json, "doc-1")
        assertContains(json, "doc-2")
    }

    @Test
    fun `getAllByStudyIdAndDeploymentId loads documents for multiple collections in a single fetch-joined query`() {
        seedFourMoreCollectionsWithADocumentEach()

        val statistics = enableStatementCounting()

        val result = collectionService.getAllByStudyIdAndDeploymentId(studyId, deploymentId)

        assertEquals(5, result.size)
        // findAllByStudyIdAndDeploymentId is a single `LEFT JOIN FETCH` query - collections and their
        // documents come back together in one round trip, not via a separate lazy-loaded query.
        assertEquals(1, statistics.prepareStatementCount)
    }

    @Test
    fun `getAll with RSQL query batch-loads documents for multiple collections, not one query per collection`() {
        seedFourMoreCollectionsWithADocumentEach()

        val statistics = enableStatementCounting()

        // Unlike getAllByStudyIdAndDeploymentId, this path can't be fetch-joined (see
        // CollectionServiceImpl.getAll) and relies on @BatchSize(25) instead - matches all 5
        // collections seeded under this studyId. RSQL field names are snake_case (QueryUtil.toCamelCase
        // converts them to the JPA property name), unlike the studyId used directly elsewhere here.
        val result = collectionService.getAll(studyId, "study_id=='$studyId'")

        assertEquals(5, result.size)
        // One query for the collections themselves plus one batched `documents` query grouping all
        // five parents (@BatchSize(25) easily covers 5) - not five separate per-collection queries.
        assertEquals(2, statistics.prepareStatementCount)
    }

    private fun seedFourMoreCollectionsWithADocumentEach() {
        (1..4).forEach { i ->
            val extra =
                collectionRepository.save(
                    Collection(name = "extra-$i", studyId = studyId, studyDeploymentId = deploymentId),
                )
            documentRepository.save(Document(name = "extra-doc-$i", collectionId = extra.id))
        }
    }

    private fun enableStatementCounting(): Statistics {
        val statistics = entityManagerFactory.unwrap(SessionFactory::class.java).statistics
        statistics.isStatisticsEnabled = true
        statistics.clear()
        return statistics
    }

    @Test
    fun `export's metadata-only getAllMetadataOnly leaves documents uninitialized`() {
        val result = collectionService.getAllMetadataOnly(studyId)

        assertEquals(1, result.size)
        assertFailsWith<LazyInitializationException> { result.single().documents?.size }
    }

    companion object {
        @Container
        @JvmStatic
        val postgres = PostgreSQLContainer("postgres:17-alpine")

        @DynamicPropertySource
        @JvmStatic
        fun registerPostgresProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
            registry.add("spring.datasource.driver-class-name", postgres::getDriverClassName)
            // The real V1__initialize.sql assumes a docker-compose-provisioned "admin" role that a bare
            // Testcontainers Postgres doesn't have. Unrelated to what this test exercises, so let
            // Hibernate generate the schema from the entities directly instead of running Flyway.
            registry.add("spring.flyway.enabled") { false }
            registry.add("spring.jpa.hibernate.ddl-auto") { "create-drop" }
        }
    }
}
