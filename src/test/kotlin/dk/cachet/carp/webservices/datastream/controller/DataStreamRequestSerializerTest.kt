package dk.cachet.carp.webservices.datastream.controller

import dk.cachet.carp.common.application.UUID
import dk.cachet.carp.common.application.data.DataType
import dk.cachet.carp.common.application.data.NoData
import dk.cachet.carp.data.application.*
import dk.cachet.carp.data.infrastructure.DataStreamServiceRequest
import dk.cachet.carp.studies.infrastructure.StudyServiceRequest
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class DataStreamRequestSerializerTest {
    @Nested
    inner class SerializeResponse {
        @Test
        fun `DataStreamRequestSerializerTest works with DataStreamServiceRequest_GetDataStream`() {
            runTest {
                val mockUUID = UUID.randomUUID()
                val mockRequest: DataStreamServiceRequest.GetDataStream = mockk()
                val mockContent = MutableDataStreamBatch()
                mockContent.appendSequence(
                    MutableDataStreamSequence<NoData>(
                        dataStream =
                            DataStreamId(
                                studyDeploymentId = mockUUID,
                                deviceRoleName = "Device",
                                dataType =
                                    DataType(
                                        namespace = "bar",
                                        name = "foo",
                                    ),
                            ),
                        firstSequenceId = 1L,
                        triggerIds =
                            listOf(
                                1,
                            ),
                        syncPoint =
                            SyncPoint(
                                Instant.parse("2023-01-02T22:35:01Z"),
                                1L,
                                1.0,
                            ),
                    ),
                )
                val serializer = DataStreamRequestSerializer()

                val result = serializer.serializeResponse(mockRequest, mockContent) as String

                val expectedResult =
                    "[{\"dataStream\":{\"studyDeploymentId\":\"${mockUUID}\",\"deviceRoleName\":\"Device\"," +
                        "\"dataType\":\"bar.foo\"}," +
                        "\"firstSequenceId\":1,\"measurements\":[],\"triggerIds\":[1],\"syncPoint\"" +
                        ":{\"synchronizedOn\":\"${
                            Instant.parse("2023-01-02T22:35:01Z")
                        }\",\"sensorTimestampAtSyncPoint\":1,\"relativeClockSpeed\":1.0}}]"
                assertEquals(expectedResult, result)
            }
        }

        @Test
        fun `DataStreamRequestSerializerTest works with DataStreamServiceRequest_OpenDataStreams`() {
            runTest {
                val mockRequest: DataStreamServiceRequest.OpenDataStreams = mockk()
                val mockContent = Unit
                val serializer = DataStreamRequestSerializer()

                val result = serializer.serializeResponse(mockRequest, mockContent) as String

                assertEquals("{}", result)
            }
        }

        @Test
        fun `DataStreamRequestSerializerTest works with DataStreamServiceRequest_AppendToDataStreams`() {
            runTest {
                val mockRequest: DataStreamServiceRequest.AppendToDataStreams = mockk()
                val mockContent = Unit
                val serializer = DataStreamRequestSerializer()

                val result = serializer.serializeResponse(mockRequest, mockContent) as String

                assertEquals("{}", result)
            }
        }

        @Test
        fun `DataStreamRequestSerializerTest works with DataStreamServiceRequest_CloseDataStreams`() {
            runTest {
                val mockRequest: DataStreamServiceRequest.CloseDataStreams = mockk()
                val mockContent = Unit
                val serializer = DataStreamRequestSerializer()

                val result = serializer.serializeResponse(mockRequest, mockContent) as String

                assertEquals("{}", result)
            }
        }

        @Test
        fun `DataStreamRequestSerializerTest works with DataStreamServiceRequest_RemoveDataStreams`() {
            runTest {
                val mockRequest: DataStreamServiceRequest.RemoveDataStreams = mockk()
                val deploymentId = UUID.randomUUID()
                val serializer = DataStreamRequestSerializer()

                val result = serializer.serializeResponse(mockRequest, setOf(deploymentId)) as String

                assertEquals("[\"$deploymentId\"]", result)
            }
        }

        @Test
        fun `should return content if request is not recognized`() {
            runTest {
                val mockRequest: StudyServiceRequest.CreateStudy = mockk()
                val mockContent = "content"
                val serializer = DataStreamRequestSerializer()

                val result = serializer.serializeResponse(mockRequest, mockContent)

                assertEquals("content", result)
            }
        }
    }
}
