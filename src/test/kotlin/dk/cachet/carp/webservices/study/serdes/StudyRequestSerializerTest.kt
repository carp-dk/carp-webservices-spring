package dk.cachet.carp.webservices.study.serdes

import dk.cachet.carp.common.application.UUID
import dk.cachet.carp.studies.application.StudyStatus
import dk.cachet.carp.studies.infrastructure.StudyServiceRequest
import dk.cachet.carp.webservices.common.serialisers.ApplicationRequestSerializer
import io.mockk.mockk
import kotlinx.datetime.Instant
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class StudyRequestSerializerTest {
    @Test
    fun `serializes GetStudiesOverview response as study statuses`() {
        val request: StudyServiceRequest.GetStudiesOverview = mockk()
        val status =
            StudyStatus.Configuring(
                studyId = UUID.randomUUID(),
                name = "Study",
                createdOn = Instant.parse("2026-01-01T00:00:00Z"),
                studyProtocolId = null,
                canSetInvitation = true,
                canSetStudyProtocol = true,
                canDeployToParticipants = false,
                canGoLive = false,
            )
        val serializer = StudyRequestSerializer()

        val result = serializer.serializeResponse(request, listOf(status)) as String
        val decoded =
            ApplicationRequestSerializer.json.decodeFromString<List<StudyStatus>>(result)

        assertEquals(listOf(status), decoded)
    }
}
