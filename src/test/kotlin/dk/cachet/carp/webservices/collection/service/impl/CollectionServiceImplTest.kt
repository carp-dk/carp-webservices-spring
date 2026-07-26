package dk.cachet.carp.webservices.collection.service.impl

import dk.cachet.carp.common.application.users.AccountIdentity
import dk.cachet.carp.webservices.account.service.AccountService
import dk.cachet.carp.webservices.collection.domain.Collection
import dk.cachet.carp.webservices.collection.dto.CollectionCreateRequestDto
import dk.cachet.carp.webservices.collection.dto.CollectionUpdateRequestDto
import dk.cachet.carp.webservices.collection.repository.CollectionRepository
import dk.cachet.carp.webservices.common.configuration.internationalisation.service.MessageBase
import dk.cachet.carp.webservices.common.exception.responses.AlreadyExistsException
import dk.cachet.carp.webservices.common.exception.responses.ResourceNotFoundException
import dk.cachet.carp.webservices.security.authentication.domain.Account
import dk.cachet.carp.webservices.security.authentication.service.AuthenticationService
import dk.cachet.carp.webservices.security.authorization.Claim
import io.mockk.*
import org.junit.jupiter.api.Nested
import org.springframework.data.jpa.domain.Specification
import tools.jackson.databind.ObjectMapper
import java.util.*
import kotlin.test.*

class CollectionServiceImplTest {
    private val collectionRepository: CollectionRepository = mockk()
    private val accountService: AccountService = mockk()
    private val authenticationService: AuthenticationService = mockk()
    private val validationMessages: MessageBase = mockk()
    private val objectMapper: ObjectMapper = mockk()

    @Nested
    inner class Delete {
        @Test
        fun `collection is deleted and relevant side tasks are executed`() {
            val mockStudyId = "123"
            val mockId = 1
            val mockCollection = mockk<Collection>(relaxed = true)
            val mockAccountIdentity = mockk<AccountIdentity>()
            every { collectionRepository.findCollectionByStudyIdAndId(mockStudyId, mockId) } returns
                Optional.of(
                    mockCollection,
                )
            every { collectionRepository.delete(mockCollection) } just Runs
            every { authenticationService.getCarpIdentity() } returns mockAccountIdentity
            coEvery {
                accountService.revoke(
                    mockAccountIdentity,
                    setOf(Claim.CollectionOwner(mockCollection.id)),
                )
            } returns mockk<Account>()
            coEvery { objectMapper.writeValueAsString(mockCollection) } returns ""
            val sut =
                CollectionServiceImpl(
                    collectionRepository,
                    accountService,
                    authenticationService,
                    validationMessages,
                    objectMapper,
                )

            sut.delete(mockStudyId, mockId)

            verify(exactly = 1) { collectionRepository.delete(mockCollection) }
            verify(exactly = 1) { authenticationService.getCarpIdentity() }
            coVerify(
                exactly = 1,
            ) { accountService.revoke(mockAccountIdentity, setOf(Claim.CollectionOwner(mockCollection.id))) }
        }

        @Test
        fun `collection should not be deleted and relevant side tasks not executed if collection is not found`() {
            val mockStudyId = "123"
            val mockId = 1
            every { collectionRepository.findCollectionByStudyIdAndId(mockStudyId, mockId) } returns Optional.empty()
            every { authenticationService.getCarpIdentity() } returns mockk<AccountIdentity>()
            every { collectionRepository.delete(ofType<Collection>()) } just Runs
            coEvery {
                accountService.revoke(
                    any(),
                    any(),
                )
            } returns mockk<Account>()

            every {
                validationMessages.get(
                    "collection.studyId-and-collectionId.not_found",
                    mockStudyId,
                    mockId,
                )
            } returns "Collection not found"
            val sut =
                CollectionServiceImpl(
                    collectionRepository,
                    accountService,
                    authenticationService,
                    validationMessages,
                    objectMapper,
                )

            assertFailsWith(ResourceNotFoundException::class) {
                sut.delete(mockStudyId, mockId)
            }

            verify(exactly = 0) { collectionRepository.delete(ofType<Collection>()) }
            verify(exactly = 0) { authenticationService.getCarpIdentity() }
            coVerify(exactly = 0) { accountService.revoke(any(), any()) }
        }
    }

    @Nested
    inner class Update {
        @Test
        fun `collection is updated and returned`() {
            val mockStudyId = "123"
            val mockId = 1
            val updateRequest = CollectionUpdateRequestDto(name = "dsa")
            val collection = Collection(name = "old")
            every { collectionRepository.findCollectionByStudyIdAndId(mockStudyId, mockId) } returns
                Optional.of(
                    collection,
                )
            coEvery { objectMapper.writeValueAsString(ofType<Collection>()) } returns ""
            val sut =
                CollectionServiceImpl(
                    collectionRepository,
                    accountService,
                    authenticationService,
                    validationMessages,
                    objectMapper,
                )

            val result = sut.update(mockStudyId, mockId, updateRequest)

            assertEquals("dsa", result.name)
        }

        @Test
        fun `collection is not updated if not found`() {
            val mockStudyId = "123"
            val mockId = 1
            val updateRequest = CollectionUpdateRequestDto(name = "dsa")
            every { collectionRepository.findCollectionByStudyIdAndId(mockStudyId, mockId) } returns Optional.empty()
            every {
                validationMessages.get(
                    "collection.studyId-and-collectionId.not_found",
                    mockStudyId,
                    mockId,
                )
            } returns "Collection not found"
            val sut =
                CollectionServiceImpl(
                    collectionRepository,
                    accountService,
                    authenticationService,
                    validationMessages,
                    objectMapper,
                )

            assertFailsWith(ResourceNotFoundException::class) {
                sut.update(mockStudyId, mockId, updateRequest)
            }
        }
    }

    @Nested
    inner class Create {
        @Test
        fun `collection is created and returned`() {
            val mockStudyId = "123"
            val mockDeploymentId = "321"
            val mockRequest = CollectionCreateRequestDto(name = "dsa")
            val mockCollection =
                Collection().apply {
                    name = mockRequest.name
                    studyId = mockStudyId
                    studyDeploymentId = mockDeploymentId
                }
            val mockAccountIdentity = mockk<AccountIdentity>()
            every {
                collectionRepository.findByStudyDeploymentIdAndName(mockDeploymentId, mockCollection.name)
            } returns Optional.empty()
            every { collectionRepository.save(ofType<Collection>()) } returns mockCollection
            every { authenticationService.getCarpIdentity() } returns mockAccountIdentity
            coEvery {
                accountService.grant(
                    mockAccountIdentity,
                    setOf(Claim.CollectionOwner(mockCollection.id)),
                )
            } returns mockk<Account>()
            val sut =
                CollectionServiceImpl(
                    collectionRepository,
                    accountService,
                    authenticationService,
                    validationMessages,
                    objectMapper,
                )

            val result = sut.create(mockRequest, mockStudyId, mockDeploymentId)

            assertEquals("dsa", result.name)
            assertEquals(mockStudyId, result.studyId)
            assertEquals(mockDeploymentId, result.studyDeploymentId)

            verify { collectionRepository.save(any()) }
            coVerify { accountService.grant(mockAccountIdentity, setOf(Claim.CollectionOwner(mockCollection.id))) }
        }

        @Test
        fun `collection is not created if already exists`() {
            val mockStudyId = "123"
            val mockDeploymentId = "321"
            val mockRequest = CollectionCreateRequestDto(name = "dsa", deploymentId = mockDeploymentId)
            val mockCollection =
                Collection().apply {
                    name = mockRequest.name
                    studyId = mockStudyId
                    studyDeploymentId = mockDeploymentId
                }
            every { collectionRepository.findByStudyDeploymentIdAndName(mockDeploymentId, mockCollection.name) } returns
                Optional.of(
                    mockCollection,
                )
            every {
                validationMessages.get(
                    "collection.already-exists",
                    mockDeploymentId,
                    mockCollection.name,
                )
            } returns "Collection already exists"
            val sut =
                CollectionServiceImpl(
                    collectionRepository,
                    accountService,
                    authenticationService,
                    validationMessages,
                    objectMapper,
                )

            assertFailsWith(AlreadyExistsException::class) {
                sut.create(mockRequest, mockStudyId, mockDeploymentId)
            }

            verify(exactly = 0) { collectionRepository.save(any()) }
        }
    }

    @Nested
    inner class GetCollectionByStudyIdAndId {
        @Test
        fun `collection is returned if present in database`() {
            val mockStudyId = "123"
            val mockId = 1
            val mockCollection = mockk<Collection>(relaxed = true)
            every { collectionRepository.findCollectionByStudyIdAndId(mockStudyId, mockId) } returns
                Optional.of(
                    mockCollection,
                )
            coEvery { objectMapper.writeValueAsString(mockCollection) } returns ""
            val sut =
                CollectionServiceImpl(
                    collectionRepository,
                    accountService,
                    authenticationService,
                    validationMessages,
                    objectMapper,
                )

            val result = sut.getCollectionByStudyIdAndId(mockStudyId, mockId)
            assertEquals(mockCollection, result)
        }

        @Test
        fun `exception is thrown if collection is not present in database`() {
            val mockStudyId = "123"
            val mockId = 1
            every { collectionRepository.findCollectionByStudyIdAndId(mockStudyId, mockId) } returns Optional.empty()
            every {
                validationMessages.get(
                    "collection.studyId-and-collectionId.not_found",
                    mockStudyId,
                    mockId,
                )
            } returns "Collection not found"
            val sut =
                CollectionServiceImpl(
                    collectionRepository,
                    accountService,
                    authenticationService,
                    validationMessages,
                    objectMapper,
                )

            assertFailsWith(ResourceNotFoundException::class) {
                sut.getCollectionByStudyIdAndId(mockStudyId, mockId)
            }
        }
    }

    @Nested
    inner class GetCollectionByStudyIdAndByName {
        @Test
        fun `collection is returned if present in database`() {
            val mockStudyId = "123"
            val mockName = "name"
            val mockCollection = mockk<Collection>(relaxed = true)
            every { collectionRepository.findCollectionByStudyIdAndName(mockStudyId, mockName) } returns
                Optional.of(
                    mockCollection,
                )
            coEvery { objectMapper.writeValueAsString(mockCollection) } returns ""
            val sut =
                CollectionServiceImpl(
                    collectionRepository,
                    accountService,
                    authenticationService,
                    validationMessages,
                    objectMapper,
                )

            val result = sut.getCollectionByStudyIdAndByName(mockStudyId, mockName)

            assertEquals(mockCollection, result)
        }

        @Test
        fun `empty collection is returned if not present in database`() {
            val mockStudyId = "123"
            val mockName = "name"
            every {
                collectionRepository.findCollectionByStudyIdAndName(
                    mockStudyId,
                    mockName,
                )
            } returns Optional.empty()
            val sut =
                CollectionServiceImpl(
                    collectionRepository,
                    accountService,
                    authenticationService,
                    validationMessages,
                    objectMapper,
                )

            // A missing collection is an expected state (created lazily on first document), not a 404.
            val result = sut.getCollectionByStudyIdAndByName(mockStudyId, mockName)

            assertEquals(mockName, result.name)
            assertEquals(mockStudyId, result.studyId)
            assertEquals(0, result.id)
            assertTrue(result.documents!!.isEmpty())
        }
    }

    @Nested
    inner class GetAll {
        @Test
        fun `all collections are returned when no query specified`() {
            val mockStudyId = "123"
            val mockCollections = listOf(mockk<Collection>(relaxed = true))
            every { collectionRepository.findAllByStudyId(mockStudyId) } returns mockCollections
            val sut =
                CollectionServiceImpl(
                    collectionRepository,
                    accountService,
                    authenticationService,
                    validationMessages,
                    objectMapper,
                )

            val result = sut.getAll(mockStudyId)

            assertEquals(mockCollections, result)
        }

        @Test
        fun `should return all collections with validated query`() {
            val mockStudyId = "123"
            val mockQuery = "status=='active'"
            val mockCollection = Collection(name = "Test Collection", studyId = mockStudyId)
            val mockCollections = listOf(mockCollection)

            every { collectionRepository.findAll(ofType<Specification<Collection>>()) } returns mockCollections

            val sut =
                CollectionServiceImpl(
                    collectionRepository,
                    accountService,
                    authenticationService,
                    validationMessages,
                    objectMapper,
                )

            val result = sut.getAll(mockStudyId, mockQuery)

            assertEquals(mockCollections, result)
            verify { collectionRepository.findAll(ofType<Specification<Collection>>()) }
        }
    }

    @Nested
    inner class GetAllByStudyIdAndDeploymentId {
        @Test
        fun `should return all collections with studyId and deploymentId`() {
            val mockStudyId = "123"
            val mockDeploymentId = "321"
            val mockCollections = listOf(mockk<Collection>(relaxed = true))
            every {
                collectionRepository.findAllByStudyIdAndDeploymentId(mockStudyId, mockDeploymentId)
            } returns mockCollections
            val sut =
                CollectionServiceImpl(
                    collectionRepository,
                    accountService,
                    authenticationService,
                    validationMessages,
                    objectMapper,
                )

            val result = sut.getAllByStudyIdAndDeploymentId(mockStudyId, mockDeploymentId)

            assertEquals(mockCollections, result)
        }
    }
}
