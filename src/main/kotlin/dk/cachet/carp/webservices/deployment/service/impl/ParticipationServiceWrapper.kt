package dk.cachet.carp.webservices.deployment.service.impl

import dk.cachet.carp.common.application.UUID
import dk.cachet.carp.deployments.application.users.ParticipantData
import dk.cachet.carp.deployments.domain.users.ParticipantGroup
import dk.cachet.carp.deployments.domain.users.ParticipationRepository
import dk.cachet.carp.webservices.common.services.CoreServiceContainer
import dk.cachet.carp.webservices.deployment.service.ParticipationService
import dk.cachet.carp.webservices.export.service.ResourceExporter
import org.springframework.stereotype.Service
import java.nio.file.Path

@Service
class ParticipationServiceWrapper(
    private val repository: ParticipationRepository,
    services: CoreServiceContainer,
) : ParticipationService, ResourceExporter<ParticipantData> {
    final override val core = services.participationService

    override suspend fun getParticipantGroup(studyDeploymentId: UUID): ParticipantGroup? {
        return repository.getParticipantGroup(studyDeploymentId)
    }

    final override val dataFileName = "participant-data.json"

    final override val exportSerializer = ParticipantData.serializer()

    override suspend fun exportDataOrThrow(
        studyId: UUID,
        deploymentIds: Set<UUID>,
        target: Path,
    ) = deploymentIds.map {
        val group = repository.getParticipantGroupOrThrowBy(it)
        ParticipantData(group.studyDeploymentId, group.commonData, group.roleData)
    }
}
