package dk.cachet.carp.webservices.account.serdes

import dk.cachet.carp.common.application.UUID
import dk.cachet.carp.protocols.application.StudyProtocolSnapshot
import dk.cachet.carp.webservices.common.configuration.internationalisation.service.MessageBase
import dk.cachet.carp.webservices.common.exception.serialization.SerializationException
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.datetime.Clock
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.assertThrows
import tools.jackson.core.JsonGenerator
import tools.jackson.databind.SerializationContext
import kotlin.test.Test

class StudyProtocolSnapshotSerializerTest {
    private val validationMessages: MessageBase = mockk<MessageBase>()
    private val jsonGenerator: JsonGenerator = mockk<JsonGenerator>()
    private val serializationContext = mockk<SerializationContext>()

    @Nested
    inner class Serialize {
        @Test
        fun `should serialize a StudyProtocolSnapshot`() {
            val studyProtocolSnapshot =
                StudyProtocolSnapshot(
                    id = UUID.randomUUID(),
                    createdOn = Clock.System.now(),
                    version = 1,
                    ownerId = UUID.randomUUID(),
                    name = "name",
                )
            every { validationMessages.get(any()) } returns "err"
            every { jsonGenerator.writeRawValue(any<String>()) } returns jsonGenerator

            val sut = StudyProtocolSnapshotSerializer(validationMessages)

            sut.serialize(studyProtocolSnapshot, jsonGenerator, serializationContext)

            val expectedString =
                """{"id":"${studyProtocolSnapshot.id}","createdOn":"${studyProtocolSnapshot
                    .createdOn}","version":${studyProtocolSnapshot.version},"ownerId":"${studyProtocolSnapshot
                    .ownerId}","name":"${studyProtocolSnapshot.name}"}"""
            verify { jsonGenerator.writeRawValue(expectedString) }
        }

        @Test
        fun `should throw SerializationException if serialization fails`() {
            val invalidStudyProtocolSnapshot = mockk<StudyProtocolSnapshot>()

            every { validationMessages.get(any()) } returns "err"
            every { validationMessages.get(any(), any()) } returns "err"
            every { jsonGenerator.writeRawValue(any<String>()) } returns jsonGenerator

            val sut = StudyProtocolSnapshotSerializer(validationMessages)

            assertThrows<SerializationException> {
                sut.serialize(invalidStudyProtocolSnapshot, jsonGenerator, serializationContext)
            }

            verify { validationMessages.get("protocol.snapshot.serialization.error", any()) }
            verify(exactly = 0) { jsonGenerator.writeRawValue(any<String>()) }
        }
    }
}
