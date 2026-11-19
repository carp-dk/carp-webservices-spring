package dk.cachet.carp.webservices.export.service.impl

import dk.cachet.carp.common.application.ApplicationData
import dk.cachet.carp.common.application.UUID
import dk.cachet.carp.common.application.devices.Smartphone
import dk.cachet.carp.common.application.users.AccountIdentity
import dk.cachet.carp.common.application.users.AssignedTo
import dk.cachet.carp.common.application.users.ParticipantRole
import dk.cachet.carp.deployments.application.users.ParticipantData
import dk.cachet.carp.deployments.application.users.ParticipantInvitation
import dk.cachet.carp.deployments.application.users.StudyInvitation
import dk.cachet.carp.deployments.domain.StudyDeployment
import dk.cachet.carp.deployments.domain.StudyDeploymentSnapshot
import dk.cachet.carp.protocols.domain.StudyProtocol
import dk.cachet.carp.studies.domain.Study
import dk.cachet.carp.studies.domain.StudySnapshot
import dk.cachet.carp.webservices.common.input.WSInputDataTypes
import dk.cachet.carp.webservices.common.input.WS_JSON
import dk.cachet.carp.webservices.common.input.domain.FullName
import dk.cachet.carp.webservices.datastream.service.impl.DataStreamService
import dk.cachet.carp.webservices.deployment.service.impl.DeploymentServiceWrapper
import dk.cachet.carp.webservices.deployment.service.impl.ParticipationServiceWrapper
import dk.cachet.carp.webservices.export.domain.ExportLog
import dk.cachet.carp.webservices.export.service.ResourceExporter
import dk.cachet.carp.webservices.study.service.impl.StudyServiceWrapper
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.beans.factory.ListableBeanFactory
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ResourceExporterServiceImplTest {
    private val objectMapper = ObjectMapper()

    @TempDir
    lateinit var targetDir: Path

    @Nested
    inner class CoreTypes {
        @Test
        fun `study export keeps applicationData as embedded JSON and round-trips through WS_JSON`() =
            runTest {
                val snapshot =
                    Study(
                        ownerId = UUID.randomUUID(),
                        name = "Study",
                        invitation =
                            StudyInvitation(
                                "Study",
                                "Description",
                                ApplicationData("""{"studyId":"abc","applicationName":"My App"}"""),
                            ),
                    ).getSnapshot()

                val text = export(fakeExporter("study.json", StudySnapshot.serializer(), snapshot))

                val node = objectMapper.readTree(text)[0]
                val applicationData = node.path("invitation").path("applicationData")
                assertTrue(applicationData.isObject)
                assertEquals("My App", applicationData.path("applicationName").asString())
                assertFalse(applicationData.has("data"))
                assertTrue(node.path("createdOn").isString)
                assertEquals(listOf(snapshot), decode(StudySnapshot.serializer(), text))
            }

        @Test
        fun `deployment export keeps polymorphic type info and round-trips through WS_JSON`() =
            runTest {
                val protocol =
                    StudyProtocol(ownerId = UUID.randomUUID(), name = "Protocol").apply {
                        addPrimaryDevice(Smartphone("Phone"))
                        addParticipantRole(ParticipantRole("Participant", false))
                    }
                val invitation =
                    ParticipantInvitation(
                        UUID.randomUUID(),
                        AssignedTo.Roles(setOf("Participant")),
                        AccountIdentity.fromUsername("participant"),
                        StudyInvitation("Study"),
                    )
                val snapshot = StudyDeployment.fromInvitations(protocol.getSnapshot(), listOf(invitation)).getSnapshot()

                val text = export(fakeExporter("deployments.json", StudyDeploymentSnapshot.serializer(), snapshot))

                val roles = objectMapper.readTree(text)[0].path("participants")[0].path("assignedParticipantRoles")
                assertEquals(AssignedTo.Roles::class.qualifiedName, roles.path("__type").asString())
                assertEquals(listOf(snapshot), decode(StudyDeploymentSnapshot.serializer(), text))
            }

        @Test
        fun `participant data export keeps polymorphic type info and round-trips through WS_JSON`() =
            runTest {
                val participantData =
                    ParticipantData(
                        UUID.randomUUID(),
                        mapOf(WSInputDataTypes.FULL_NAME to FullName("Ada", null, "Lovelace")),
                        emptyList(),
                    )

                val text =
                    export(fakeExporter("participant-data.json", ParticipantData.serializer(), participantData))

                val fullName: JsonNode =
                    objectMapper.readTree(text)[0].path("common").path(WSInputDataTypes.FULL_NAME_TYPE_NAME)
                assertTrue(fullName.path("__type").isString)
                assertEquals(listOf(participantData), decode(ParticipantData.serializer(), text))
            }

        @Test
        fun `Core exporters declare their kotlinx serializer`() {
            assertEquals(
                StudySnapshot.serializer().descriptor,
                StudyServiceWrapper(mockk(), mockk(), mockk(), mockk(relaxed = true)).exportSerializer.descriptor,
            )
            assertEquals(
                StudyDeploymentSnapshot.serializer().descriptor,
                DeploymentServiceWrapper(mockk(), mockk(relaxed = true)).exportSerializer.descriptor,
            )
            assertEquals(
                ParticipantData.serializer().descriptor,
                ParticipationServiceWrapper(mockk(), mockk(relaxed = true)).exportSerializer.descriptor,
            )
        }
    }

    @Nested
    inner class DomainTypes {
        @Test
        fun `exporter without a kotlinx serializer is written with Jackson`() =
            runTest {
                val exporter =
                    object : ResourceExporter<Row> {
                        override val dataFileName = "rows.json"

                        override suspend fun exportDataOrThrow(
                            studyId: UUID,
                            deploymentIds: Set<UUID>,
                            target: Path,
                        ) = listOf(Row("value"))
                    }

                assertEquals("""[{"name":"value"}]""", export(exporter))
            }
    }

    data class Row(
        val name: String,
    )

    private fun <T> fakeExporter(
        fileName: String,
        serializer: KSerializer<T>,
        vararg exports: T,
    ) = object : ResourceExporter<T> {
        override val dataFileName = fileName
        override val exportSerializer = serializer

        override suspend fun exportDataOrThrow(
            studyId: UUID,
            deploymentIds: Set<UUID>,
            target: Path,
        ) = exports.toList()
    }

    private suspend fun export(exporter: ResourceExporter<*>): String {
        val beanFactory = mockk<ListableBeanFactory>()
        every { beanFactory.getBeansOfType(ResourceExporter::class.java) } returns mapOf("exporter" to exporter)
        val dataStreamService = mockk<DataStreamService>()
        coEvery { dataStreamService.exportDataOrThrow(any(), any()) } just runs

        val log = ExportLog()
        val sut = ResourceExporterServiceImpl(objectMapper, mockk(), mockk(), beanFactory, dataStreamService)
        sut.exportStudyData(UUID.randomUUID(), setOf(UUID.randomUUID()), targetDir, log, false)

        assertTrue(log.errorLogs.isEmpty(), "Export logged errors: ${log.errorLogs}")
        return targetDir.resolve(exporter.dataFileName).readText()
    }

    private fun <T> decode(
        serializer: KSerializer<T>,
        text: String,
    ): List<T> = WS_JSON.decodeFromString(ListSerializer(serializer), text)
}
