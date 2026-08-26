package dk.cachet.carp.webservices.common.services

import dk.cachet.carp.common.application.UUID
import dk.cachet.carp.common.application.services.createApplicationServiceAdapter
import dk.cachet.carp.data.infrastructure.DataStreamServiceDecorator
import dk.cachet.carp.deployments.application.DeploymentService
import dk.cachet.carp.deployments.application.DeploymentServiceHost
import dk.cachet.carp.deployments.application.ParticipationService
import dk.cachet.carp.deployments.application.ParticipationServiceHost
import dk.cachet.carp.deployments.domain.users.AccountService
import dk.cachet.carp.deployments.domain.users.ParticipantGroupService
import dk.cachet.carp.deployments.infrastructure.DeploymentServiceDecorator
import dk.cachet.carp.deployments.infrastructure.ParticipationServiceDecorator
import dk.cachet.carp.protocols.application.ProtocolFactoryServiceHost
import dk.cachet.carp.protocols.application.ProtocolServiceHost
import dk.cachet.carp.protocols.infrastructure.ProtocolFactoryServiceDecorator
import dk.cachet.carp.protocols.infrastructure.ProtocolServiceDecorator
import dk.cachet.carp.studies.application.RecruitmentService
import dk.cachet.carp.studies.application.RecruitmentServiceHost
import dk.cachet.carp.studies.application.StudyService
import dk.cachet.carp.studies.application.StudyServiceHost
import dk.cachet.carp.studies.infrastructure.RecruitmentServiceDecorator
import dk.cachet.carp.studies.infrastructure.StudyServiceDecorator
import dk.cachet.carp.webservices.common.authorization.ApplicationServiceRequestAuthorizer
import dk.cachet.carp.webservices.common.eventbus.CoreEventBus
import dk.cachet.carp.webservices.common.input.WSInputDataTypes
import dk.cachet.carp.webservices.datastream.authorization.DataStreamServiceAuthorizer
import dk.cachet.carp.webservices.datastream.service.core.CoreDataStreamService
import dk.cachet.carp.webservices.deployment.authorization.DeploymentServiceAuthorizer
import dk.cachet.carp.webservices.deployment.authorization.ParticipationServiceAuthorizer
import dk.cachet.carp.webservices.deployment.repository.CoreDeploymentRepository
import dk.cachet.carp.webservices.deployment.repository.CoreParticipationRepository
import dk.cachet.carp.webservices.protocol.authorization.ProtocolFactoryServiceAuthorizer
import dk.cachet.carp.webservices.protocol.authorization.ProtocolServiceAuthorizer
import dk.cachet.carp.webservices.protocol.repository.CoreProtocolRepository
import dk.cachet.carp.webservices.study.authorization.RecruitmentServiceAuthorizer
import dk.cachet.carp.webservices.study.authorization.StudyServiceAuthorizer
import dk.cachet.carp.webservices.study.repository.CoreParticipantRepository
import dk.cachet.carp.webservices.study.repository.CoreStudyRepository
import org.springframework.stereotype.Service

/**
 * Initializes all services used in the application. Services depending on other services can get a reference of the
 * plain service without any decorators, while anyone accessing them from the outside will always have to be authorized.
 */
@Suppress("LongParameterList")
@Service
class CoreServiceContainer(
    coreEventBus: CoreEventBus,
    // repositories
    participationRepository: CoreParticipationRepository,
    deploymentRepository: CoreDeploymentRepository,
    participantRepository: CoreParticipantRepository,
    protocolRepository: CoreProtocolRepository,
    studyRepository: CoreStudyRepository,
    // services
    coreDataStreamService: CoreDataStreamService,
    accountService: AccountService,
    // authorizers
    dataStreamServiceAuthorizer: DataStreamServiceAuthorizer,
    deploymentServiceAuthorizer: DeploymentServiceAuthorizer,
    participationServiceAuthorizer: ParticipationServiceAuthorizer,
    protocolFactoryServiceAuthorizer: ProtocolFactoryServiceAuthorizer,
    protocolServiceAuthorizer: ProtocolServiceAuthorizer,
    recruitmentServiceAuthorizer: RecruitmentServiceAuthorizer,
    studyServiceAuthorizer: StudyServiceAuthorizer,
) {
    final val dataStreamService =
        DataStreamServiceDecorator(
            coreDataStreamService,
        ) { command -> ApplicationServiceRequestAuthorizer(dataStreamServiceAuthorizer, command) }

    private val _deploymentService =
        DeploymentServiceHost(
            deploymentRepository,
            coreDataStreamService,
            coreEventBus.createApplicationServiceAdapter(DeploymentService::class),
        )
    final val deploymentService =
        DeploymentServiceDecorator(
            _deploymentService,
        ) { command -> ApplicationServiceRequestAuthorizer(deploymentServiceAuthorizer, command) }

    private val _participationService =
        ParticipationServiceHost(
            participationRepository,
            ParticipantGroupService(accountService),
            coreEventBus.createApplicationServiceAdapter(ParticipationService::class),
            WSInputDataTypes,
        )
    final val participationService =
        ParticipationServiceDecorator(
            _participationService,
        ) { command -> ApplicationServiceRequestAuthorizer(participationServiceAuthorizer, command) }

    private val _protocolFactoryService = ProtocolFactoryServiceHost()
    final val protocolFactoryService =
        ProtocolFactoryServiceDecorator(
            _protocolFactoryService,
        ) { command -> ApplicationServiceRequestAuthorizer(protocolFactoryServiceAuthorizer, command) }

    private val _protocolService = ProtocolServiceHost(protocolRepository)
    final val protocolService =
        ProtocolServiceDecorator(
            _protocolService,
        ) { command -> ApplicationServiceRequestAuthorizer(protocolServiceAuthorizer, command) }

    private val _recruitmentService =
        RecruitmentServiceHost(
            participantRepository,
            _deploymentService,
            coreEventBus.createApplicationServiceAdapter(RecruitmentService::class),
        )
    final val recruitmentService =
        RecruitmentServiceDecorator(
            _recruitmentService,
        ) { command -> ApplicationServiceRequestAuthorizer(recruitmentServiceAuthorizer, command) }

    private val _studyService =
        StudyServiceHost(
            studyRepository,
            coreEventBus.createApplicationServiceAdapter(StudyService::class),
        )
    final val studyService =
        StudyServiceDecorator(
            _studyService,
        ) { command -> ApplicationServiceRequestAuthorizer(studyServiceAuthorizer, command) }

    /**
     * A read-only, undecorated view of the study service, with NO authorization check. Only for trusted,
     * non-request-scoped internal callers that have already established authorization by other means -
     * e.g. self-signup's public endpoint, which has no authenticated principal on the request at all and
     * instead gates access via its own short-code lookup and capacity reservation. Deliberately typed as
     * [UnauthorizedStudyReader] rather than the full [StudyService] - narrower than `_studyService` itself
     * - so a future caller reaching for this from another unauthenticated context can only ever read study
     * details/status, not accidentally call a mutating/lifecycle method (`goLive`, `setProtocol`, `remove`,
     * ...) that was never meant to be reachable without [StudyServiceAuthorizer]. Never call this from a
     * controller or anything else reachable from an HTTP request without an equivalent independent
     * authorization check.
     */
    final val internalStudyService: UnauthorizedStudyReader =
        object : UnauthorizedStudyReader {
            override suspend fun getStudyDetails(studyId: UUID) = _studyService.getStudyDetails(studyId)

            override suspend fun getStudyStatus(studyId: UUID) = _studyService.getStudyStatus(studyId)
        }
}
