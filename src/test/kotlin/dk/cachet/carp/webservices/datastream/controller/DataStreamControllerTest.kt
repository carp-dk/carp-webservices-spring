package dk.cachet.carp.webservices.datastream.controller

import dk.cachet.carp.common.application.UUID
import dk.cachet.carp.common.application.data.DataType
import dk.cachet.carp.data.application.DataStreamId
import dk.cachet.carp.data.infrastructure.DataStreamServiceDecorator
import dk.cachet.carp.data.infrastructure.DataStreamServiceRequest
import dk.cachet.carp.webservices.common.converter.ISO8601ToKotlinInstantConverter
import dk.cachet.carp.webservices.common.converter.UUIDConverter
import dk.cachet.carp.webservices.common.input.WS_JSON
import dk.cachet.carp.webservices.datastream.service.DataStreamService
import dk.cachet.carp.webservices.datastream.service.impl.MutableDataStreamBatchDecorator
import dk.cachet.carp.webservices.datastream.service.impl.compressData
import dk.cachet.carp.webservices.security.authorization.Claim
import dk.cachet.carp.webservices.security.authorization.service.AuthorizationService
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Nested
import org.springframework.format.support.DefaultFormattingConversionService
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import kotlin.test.*
import kotlin.time.Instant

class DataStreamControllerTest {
    private val dataStreamService: DataStreamService = mockk()
    private val authorizationService: AuthorizationService = mockk(relaxed = true)
    val dss = mockk<dk.cachet.carp.data.application.DataStreamService>()
    val core = DataStreamServiceDecorator(dss) { command -> command }

    private lateinit var mockMvc: MockMvc

    @BeforeTest
    fun setup() {
        val conversionService =
            DefaultFormattingConversionService().apply {
                addConverter(UUIDConverter())
                addConverter(ISO8601ToKotlinInstantConverter())
            }
        mockMvc =
            MockMvcBuilders.standaloneSetup(
                DataStreamController(dataStreamService, authorizationService),
            ).setConversionService(conversionService).build()
        coEvery { dataStreamService.core } returns core
    }

    @Nested
    inner class Invoke {
        val urlPath = "/api/data-stream-service"

        @Test
        fun `should invoke`() {
            runTest {
                val mockUuids = setOf(UUID.randomUUID())
                val rpcRequest = DataStreamServiceRequest.CloseDataStreams(mockUuids)
                val serializedRequest = WS_JSON.encodeToString(DataStreamServiceRequest.Serializer, rpcRequest)

                coEvery { core.closeDataStreams(any()) } returns Unit

                mockMvc.perform(
                    post(urlPath).contentType(MediaType.APPLICATION_JSON).content(serializedRequest),
                ).andExpect(status().isOk)

                coVerify { core.closeDataStreams(mockUuids) }
            }
        }
    }

    @Nested
    inner class QueryDataStreamByTime {
        val urlPath = "/api/data-stream-service/query-by-time"

        @Test
        fun `should decode DataStreamId body, authorize by deployment, and return the batch`() {
            runTest {
                val deploymentId = UUID.randomUUID()
                val dataStream =
                    DataStreamId(
                        studyDeploymentId = deploymentId,
                        deviceRoleName = "Primary Phone",
                        dataType = DataType("dk.cachet.carp", "heartbeat"),
                    )
                // Body is a core DataStreamId, serialized exactly as a mobile carp.core client would.
                val body = WS_JSON.encodeToString(DataStreamId.serializer(), dataStream)
                val from = Instant.fromEpochMilliseconds(1000L)
                val to = Instant.fromEpochMilliseconds(2000L)

                coEvery {
                    dataStreamService.getDataStreamByUpdatedAt(dataStream, from, to)
                } returns MutableDataStreamBatchDecorator()

                mockMvc.perform(
                    post(urlPath)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .param("from", from.toString())
                        .param("to", to.toString()),
                ).andExpect(status().isOk)

                // Matches core GetDataStream authorization: a single InDeployment claim.
                verify { authorizationService.require(Claim.InDeployment(deploymentId)) }
                coVerify { dataStreamService.getDataStreamByUpdatedAt(dataStream, from, to) }
            }
        }
    }

    @Nested
    inner class HandleCompressedData {
        val urlPath = "/api/data-stream-service-zip"

        @Test
        fun `should invoke`() {
            runTest {
                val mockUuids = setOf(UUID.randomUUID())
                val rpcRequest = DataStreamServiceRequest.CloseDataStreams(mockUuids)
                val serializedRequest = WS_JSON.encodeToString(DataStreamServiceRequest.Serializer, rpcRequest)
                val compressedData = compressData(serializedRequest)

                coEvery { core.closeDataStreams(any()) } returns Unit

                mockMvc.perform(
                    post(urlPath).contentType(MediaType.APPLICATION_OCTET_STREAM).content(compressedData),
                ).andExpect(status().isOk)

                coVerify { core.closeDataStreams(mockUuids) }
            }
        }
    }
}
