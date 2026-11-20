package dk.cachet.carp.webservices.deployment.service.impl

import dk.cachet.carp.common.application.UUID
import dk.cachet.carp.common.application.devices.Smartphone
import dk.cachet.carp.common.application.users.AccountIdentity
import dk.cachet.carp.common.application.users.AssignedTo
import dk.cachet.carp.common.application.users.ParticipantRole
import dk.cachet.carp.deployments.application.users.ParticipantInvitation
import dk.cachet.carp.deployments.application.users.StudyInvitation
import dk.cachet.carp.deployments.domain.StudyDeployment
import dk.cachet.carp.deployments.domain.StudyDeploymentSnapshot
import dk.cachet.carp.deployments.domain.users.ParticipantGroup
import dk.cachet.carp.deployments.domain.users.ParticipationRepository
import dk.cachet.carp.protocols.domain.StudyProtocol
import dk.cachet.carp.webservices.common.input.WS_JSON
import dk.cachet.carp.webservices.deployment.repository.StudyDeploymentRepository
import dk.cachet.carp.webservices.export.service.ResourceExporter.Companion.EXPORT_BATCH_SIZE
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import tools.jackson.databind.ObjectMapper
import java.nio.file.Path
import kotlin.test.assertEquals
import dk.cachet.carp.webservices.deployment.domain.StudyDeployment as StudyDeploymentEntity

class DeploymentExportBatchingTest {
    private val studyId = UUID.randomUUID()
    private val target = Path.of("unused")
    private val deploymentIds = List(EXPORT_BATCH_SIZE + EXPORT_BATCH_SIZE / 2) { UUID.randomUUID() }.toSet()

    @Nested
    inner class Deployments {
        @Test
        fun `deployments are loaded in batches and decoded`() =
            runTest {
                val snapshot = createDeploymentSnapshot()
                val entity =
                    mockk<StudyDeploymentEntity> {
                        every { this@mockk.snapshot } returns ObjectMapper().readTree(WS_JSON.encodeToString(snapshot))
                    }
                val requested = mutableListOf<Collection<String>>()
                val repository = mockk<StudyDeploymentRepository>()
                every { repository.findAllByStudyDeploymentIds(any()) } answers {
                    firstArg<Collection<String>>().also { requested.add(it) }
                    if (requested.size == 1) listOf(entity) else emptyList()
                }
                val sut = DeploymentServiceWrapper(repository, mockk(relaxed = true))

                val batches = sut.exportBatchesOrThrow(studyId, deploymentIds, target).toList()

                assertEquals(listOf(EXPORT_BATCH_SIZE, EXPORT_BATCH_SIZE / 2), requested.map { it.size })
                assertEquals(deploymentIds.map { it.stringRepresentation }, requested.flatten())
                assertEquals(listOf(listOf(snapshot), emptyList()), batches)
            }
    }

    @Nested
    inner class ParticipantData {
        @Test
        fun `participant groups are loaded in batches and kept in deployment order`() =
            runTest {
                val requested = mutableListOf<Set<UUID>>()
                val repository = mockk<ParticipationRepository>()
                coEvery { repository.getParticipantGroupList(any()) } answers {
                    firstArg<Set<UUID>>().also { requested.add(it) }.reversed().map { participantGroup(it) }
                }
                val sut = ParticipationServiceWrapper(repository, mockk(relaxed = true))

                val batches = sut.exportBatchesOrThrow(studyId, deploymentIds, target).toList()

                assertEquals(listOf(EXPORT_BATCH_SIZE, EXPORT_BATCH_SIZE / 2), requested.map { it.size })
                assertEquals(deploymentIds.toList(), batches.flatten().map { it.studyDeploymentId })
            }

        @Test
        fun `a deployment without a participant group is skipped`() =
            runTest {
                val missing = deploymentIds.first()
                val repository = mockk<ParticipationRepository>()
                coEvery { repository.getParticipantGroupList(any()) } answers {
                    firstArg<Set<UUID>>().filter { it != missing }.map { participantGroup(it) }
                }
                val sut = ParticipationServiceWrapper(repository, mockk(relaxed = true))

                val exported = sut.exportBatchesOrThrow(studyId, deploymentIds, target).toList().flatten()

                assertEquals((deploymentIds - missing).toList(), exported.map { it.studyDeploymentId })
            }
    }

    private fun participantGroup(deploymentId: UUID) =
        mockk<ParticipantGroup> {
            every { studyDeploymentId } returns deploymentId
            every { commonData } returns emptyMap()
            every { roleData } returns emptyList()
        }

    private fun createDeploymentSnapshot(): StudyDeploymentSnapshot {
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

        return StudyDeployment.fromInvitations(protocol.getSnapshot(), listOf(invitation)).getSnapshot()
    }
}
