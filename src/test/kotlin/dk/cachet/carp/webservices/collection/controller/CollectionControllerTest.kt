package dk.cachet.carp.webservices.collection.controller

import dk.cachet.carp.common.application.UUID
import dk.cachet.carp.webservices.collection.domain.Collection
import dk.cachet.carp.webservices.collection.dto.CollectionCreateRequestDto
import dk.cachet.carp.webservices.collection.dto.CollectionUpdateRequestDto
import dk.cachet.carp.webservices.collection.service.CollectionService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Nested
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import tools.jackson.databind.ObjectMapper
import kotlin.test.BeforeTest
import kotlin.test.Test

class CollectionControllerTest {
    private val collectionService: CollectionService = mockk()
    private val objectMapper: ObjectMapper = ObjectMapper()

    private lateinit var mockMvc: MockMvc

    @BeforeTest
    fun setup() {
        mockMvc = MockMvcBuilders.standaloneSetup(CollectionController(collectionService)).build()
    }

    @Nested
    inner class Create {
        @Test
        fun `should return 400 if request body is invalid`() =
            runTest {
                val mockStudyId = UUID.randomUUID().stringRepresentation
                val url = "/api/studies/$mockStudyId/collections"
                val collectionCreateRequestDto = CollectionCreateRequestDto("")

                mockMvc.perform(
                    post(url).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(collectionCreateRequestDto)),
                ).andExpect(status().isBadRequest)
            }

        @Test
        fun `should return 400 if studyId is not a valid UUID`() =
            runTest {
                val collectionCreateRequestDto = CollectionCreateRequestDto("someName", "someDeploymentId")
                val badUrl = "/api/studies/1/collections"

                mockMvc.perform(
                    post(badUrl).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(collectionCreateRequestDto)),
                ).andExpect(status().isBadRequest)
            }

        @Test
        fun `should succeed if request is valid`() =
            runTest {
                val mockStudyId = UUID.randomUUID().stringRepresentation
                val url = "/api/studies/$mockStudyId/collections"
                val collectionCreateRequestDto = CollectionCreateRequestDto("someName", "someDeploymentId")
                val collectionMock: Collection = mockk(relaxed = true)
                every { collectionService.create(any(), any(), any()) } returns collectionMock

                mockMvc.perform(
                    post(url).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(collectionCreateRequestDto)),
                ).andExpect(status().isCreated)

                verify { collectionService.create(collectionCreateRequestDto, mockStudyId, "someDeploymentId") }
            }
    }

    @Nested
    inner class GetByStudyIdAndCollectionId {
        @Test
        fun `should return 200 if collection exists`() =
            runTest {
                val mockStudyId = UUID.randomUUID().stringRepresentation
                val mockCollectionId = 1
                val url = "/api/studies/$mockStudyId/collections/id/$mockCollectionId"

                val collectionMock: Collection = mockk(relaxed = true)
                every {
                    collectionService.getCollectionByStudyIdAndId(
                        mockStudyId, mockCollectionId,
                    )
                } returns collectionMock

                mockMvc.perform(
                    get(url).contentType(MediaType.APPLICATION_JSON),
                ).andExpect(status().isOk)

                verify { collectionService.getCollectionByStudyIdAndId(mockStudyId, mockCollectionId) }
            }
    }

    @Nested
    inner class GetByStudyIdAndCollectionName {
        @Test
        fun `should return 200 if collection exists`() =
            runTest {
                val mockStudyId = UUID.randomUUID().stringRepresentation
                val mockCollectionName = "someName"
                val url = "/api/studies/$mockStudyId/collections/$mockCollectionName"

                val collectionMock: Collection = mockk(relaxed = true)
                every {
                    collectionService.getCollectionByStudyIdAndByName(
                        mockStudyId, mockCollectionName,
                    )
                } returns collectionMock

                mockMvc.perform(
                    get(url).contentType(MediaType.APPLICATION_JSON),
                ).andExpect(status().isOk)

                verify { collectionService.getCollectionByStudyIdAndByName(mockStudyId, mockCollectionName) }
            }
    }

    @Nested
    inner class GetAll {
        @Test
        fun `should return the collection of collection if query is not specified`() =
            runTest {
                val mockStudyId = UUID.randomUUID().stringRepresentation
                val url = "/api/studies/$mockStudyId/collections"

                val collectionMock: Collection = mockk(relaxed = true)
                every {
                    collectionService.getAll(mockStudyId, null)
                } returns listOf(collectionMock)

                mockMvc.perform(
                    get(url).contentType(MediaType.APPLICATION_JSON),
                ).andExpect(status().isOk)

                verify { collectionService.getAll(mockStudyId, null) }
            }

        @Test
        fun `should return the collection of collection if query is specified`() =
            runTest {
                val mockStudyId = UUID.randomUUID().stringRepresentation
                val query = "someQuery"
                val url = "/api/studies/$mockStudyId/collections?query=$query"

                val collectionMock: Collection = mockk(relaxed = true)
                every {
                    collectionService.getAll(mockStudyId, query)
                } returns listOf(collectionMock)

                mockMvc.perform(
                    get(url).contentType(MediaType.APPLICATION_JSON),
                ).andExpect(status().isOk)

                verify { collectionService.getAll(mockStudyId, query) }
            }
    }

    @Nested
    inner class GetByStudyIdAndDeploymentId {
        @Test
        fun `should return the collection of collection if url is correct`() =
            runTest {
                val mockStudyId = UUID.randomUUID().stringRepresentation
                val mockDeploymentId = UUID.randomUUID().stringRepresentation
                val url = "/api/studies/$mockStudyId/collections/deployments/$mockDeploymentId"

                val collectionMock: Collection = mockk(relaxed = true)
                every {
                    collectionService.getAllByStudyIdAndDeploymentId(
                        mockStudyId, mockDeploymentId,
                    )
                } returns listOf(collectionMock)

                mockMvc.perform(
                    get(url).contentType(MediaType.APPLICATION_JSON),
                ).andExpect(status().isOk)

                verify { collectionService.getAllByStudyIdAndDeploymentId(mockStudyId, mockDeploymentId) }
            }

        @Test
        fun `should return 400 if deploymentId is not UUID`() =
            runTest {
                val mockStudyId = UUID.randomUUID().stringRepresentation
                val mockDeploymentId = 1
                val badUrl = "/api/studies/$mockStudyId/collections/deployments/$mockDeploymentId"

                mockMvc.perform(
                    get(badUrl).contentType(MediaType.APPLICATION_JSON),
                ).andExpect(status().isBadRequest)
            }
    }

    @Nested
    inner class Delete {
        @Test
        fun `should return 200 if collection is deleted`() =
            runTest {
                val mockStudyId = UUID.randomUUID().stringRepresentation
                val mockCollectionId = 1
                val url = "/api/studies/$mockStudyId/collections/id/$mockCollectionId"

                every {
                    collectionService.delete(mockStudyId, mockCollectionId)
                } returns Unit

                mockMvc.perform(
                    delete(url).contentType(MediaType.APPLICATION_JSON),
                ).andExpect(status().isOk)

                verify { collectionService.delete(mockStudyId, mockCollectionId) }
            }

        @Test
        fun `should return 400 if collectionId is not an integer`() =
            runTest {
                val mockStudyId = UUID.randomUUID().stringRepresentation
                val mockCollectionId = UUID.randomUUID().stringRepresentation
                val badUrl = "/api/studies/$mockStudyId/collections/id/$mockCollectionId"

                mockMvc.perform(
                    delete(badUrl).contentType(MediaType.APPLICATION_JSON),
                ).andExpect(status().isBadRequest)
            }
    }

    @Nested
    inner class Update {
        @Test
        fun `should return 400 if body is not valid`() =
            runTest {
                val mockStudyId = UUID.randomUUID().stringRepresentation
                val mockCollectionId = 1
                val url = "/api/studies/$mockStudyId/collections/id/$mockCollectionId"
                val request = CollectionUpdateRequestDto("")

                mockMvc.perform(
                    put(url).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(request)),
                ).andExpect(status().isBadRequest)
            }

        @Test
        fun `should succeed if request is valid`() =
            runTest {
                val mockStudyId = UUID.randomUUID().stringRepresentation
                val mockCollectionId = 1
                val url = "/api/studies/$mockStudyId/collections/id/$mockCollectionId"
                val request = CollectionUpdateRequestDto("someName")
                val collectionMock: Collection = mockk(relaxed = true)
                every { collectionService.update(any(), any(), any()) } returns collectionMock

                mockMvc.perform(
                    put(url).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(request)),
                ).andExpect(status().isOk)

                verify { collectionService.update(mockStudyId, mockCollectionId, request) }
            }
    }
}
