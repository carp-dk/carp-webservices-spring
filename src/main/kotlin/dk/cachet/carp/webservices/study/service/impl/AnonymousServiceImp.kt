package dk.cachet.carp.webservices.study.service.impl

import dk.cachet.carp.common.application.UUID
import dk.cachet.carp.common.application.users.AccountIdentity
import dk.cachet.carp.common.application.users.AssignedTo
import dk.cachet.carp.common.application.users.UsernameAccountIdentity
import dk.cachet.carp.common.domain.users.Account
import dk.cachet.carp.deployments.application.users.ParticipantInvitation
import dk.cachet.carp.deployments.application.users.Participation
import dk.cachet.carp.deployments.domain.StudyDeploymentSnapshot
import dk.cachet.carp.deployments.domain.users.ParticipantGroupSnapshot
import dk.cachet.carp.deployments.domain.users.getAssignedDeviceRoleNames
import dk.cachet.carp.studies.application.StudyDetails
import dk.cachet.carp.studies.application.users.AssignedParticipantRoles
import dk.cachet.carp.studies.application.users.Participant
import dk.cachet.carp.studies.domain.users.StagedParticipantGroup
import dk.cachet.carp.webservices.common.exception.responses.ResourceNotFoundException
import dk.cachet.carp.webservices.common.input.WS_JSON
import dk.cachet.carp.webservices.common.services.CoreServiceContainer
import dk.cachet.carp.webservices.deployment.domain.ParticipantGroup
import dk.cachet.carp.webservices.deployment.domain.StudyDeployment
import dk.cachet.carp.webservices.deployment.repository.CoreDeploymentRepository
import dk.cachet.carp.webservices.deployment.repository.CoreParticipationRepository
import dk.cachet.carp.webservices.security.config.SecurityCoroutineContext
import dk.cachet.carp.webservices.study.domain.normalization.RecruitmentNormalizationStore
import dk.cachet.carp.webservices.study.repository.RecruitmentRepository
import dk.cachet.carp.webservices.study.service.AnonymousService
import kotlinx.coroutines.*
import kotlinx.serialization.encodeToString
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import tools.jackson.databind.ObjectMapper
import kotlin.time.Clock
import dk.cachet.carp.deployments.domain.StudyDeployment as CoreStudyDeployment

@Service
@Suppress("LongParameterList") // Spring constructor injection
class AnonymousServiceImp(
    private val objectMapper: ObjectMapper,
    services: CoreServiceContainer,
    private val deploymentRepository: CoreDeploymentRepository,
    private val recruitmentRepository: RecruitmentRepository,
    private val participantGroupRepository: CoreParticipationRepository,
    private val normalizationStore: RecruitmentNormalizationStore,
    transactionManager: PlatformTransactionManager,
    @Value("\${carp.recruitment.normalized-store-enabled:false}")
    private val normalizedStoreEnabled: Boolean,
) : AnonymousService {
    val studyService = services.studyService

    private val transactionTemplate = TransactionTemplate(transactionManager)

    data class ParticipantBundle(
        val participant: Participant,
        val group: Pair<UUID, StagedParticipantGroup>,
        val deployment: StudyDeployment,
        val participation: ParticipantGroup,
    )

    data class Entities(
        val participants: List<Participant>,
        val groups: Map<UUID, StagedParticipantGroup>,
        val deployments: List<StudyDeployment>,
        val participation: List<ParticipantGroup>,
    )

    @Suppress("LongMethod")
    suspend fun buildParticipants(
        pair: List<Pair<String, String>>,
        roleName: String,
        study: StudyDetails,
    ) = coroutineScope {
        val bundles =
            pair.map {
                async(Dispatchers.Default) {
                    // Anonymous participants deliberately use ONE UUID — the Keycloak account id
                    // (== username) — as the participant id, the participant group id AND the study
                    // deployment id. This is REQUIRED, not incidental: the carp-keycloak bulk extension
                    // stamps each account with attribute inDeployment = <the account's own id>, which the
                    // realm mapper turns into the JWT `in_deployment` claim. Authorization
                    // (DeploymentServiceAuthorizer / DataStreamServiceAuthorizer) checks
                    // Claim.InDeployment(deploymentId), so the deployment id MUST equal the account id, or
                    // the participant authenticates but is 403'd on every deployment/data-stream call.
                    // Do NOT split these into distinct ids — it breaks anonymous participant auth.
                    // (group id == deployment id is also carp.core convention; see StagedParticipantGroup.id.)
                    val id = UUID(it.first)

                    val participant =
                        Participant(AccountIdentity.fromUsername(it.first), id)

                    // Deployment
                    val stagedGroup =
                        StagedParticipantGroup(id).apply {
                            addParticipants(setOf(AssignedParticipantRoles(id, AssignedTo.Roles(setOf(roleName)))))
                            markAsDeployed()
                        }

                    val invitations =
                        listOf(
                            ParticipantInvitation(
                                id,
                                AssignedTo.Roles(setOf(roleName)),
                                UsernameAccountIdentity(it.first),
                                study.invitation,
                            ),
                        )

                    val newDeployment =
                        CoreStudyDeployment.fromInvitations(
                            study.protocolSnapshot!!,
                            invitations,
                            id,
                            Clock.System.now(),
                        )

                    val studyDeploymentToSave = StudyDeployment()

                    val snapshot =
                        WS_JSON.encodeToString(
                            StudyDeploymentSnapshot.serializer(),
                            newDeployment.getSnapshot(),
                        )

                    studyDeploymentToSave.snapshot = objectMapper.readTree(snapshot)

                    // ParticipantGroup
                    val group =
                        dk.cachet.carp.deployments.domain.users.ParticipantGroup.fromNewDeployment(
                            id,
                            study.protocolSnapshot!!.toObject(),
                        )

                    for (invitation in invitations) {
                        val participation =
                            Participation(
                                id,
                                invitation.assignedRoles,
                                invitation.participantId,
                            )
                        val assignedDevices =
                            study
                                .protocolSnapshot!!
                                .getAssignedDeviceRoleNames(invitation.assignedRoles)
                                .map { role ->
                                    study
                                        .protocolSnapshot!!
                                        .toObject()
                                        .primaryDevices
                                        .first { pd -> pd.roleName == role }
                                }

                        val account = Account(AccountIdentity.fromUsername(it.first), id)
                        val studyInvitation = invitation.invitation

                        group.addParticipation(
                            account,
                            studyInvitation,
                            participation,
                            assignedDevices.toSet(),
                        )
                    }

                    val snapshotToSave =
                        WS_JSON.encodeToString(ParticipantGroupSnapshot.serializer(), group.getSnapshot())

                    val participation = ParticipantGroup()
                    participation.snapshot = objectMapper.readTree(snapshotToSave)

                    ParticipantBundle(
                        participant,
                        id to stagedGroup,
                        studyDeploymentToSave,
                        participation,
                    )
                }
            }.awaitAll()

        val participants = bundles.map { it.participant }
        val participantGroups = bundles.associate { it.group }
        val deployments = bundles.map { it.deployment }
        val participation = bundles.map { it.participation }

        Entities(participants, participantGroups, deployments, participation)
    }

    @Suppress("DestructuringDeclarationWithTooManyEntries")
    override suspend fun bulkAddParticipantsAndGroups(
        studyId: UUID,
        roleName: String,
        pair: List<Pair<String, String>>,
    ): List<StagedParticipantGroup> =
        withContext(Dispatchers.IO + SecurityCoroutineContext()) {
            val study = studyService.getStudyDetails(studyId)

            val (participants, groups, deployments, participation) = buildParticipants(pair, roleName, study)

            // One transaction per batch: the recruitment rows, deployments and participant groups either
            // all commit or all roll back, so a failure part-way through a batch cannot leave orphaned rows
            // (e.g. recruitment participants without their deployments). Runs synchronously on this IO
            // thread, which carries the security context via SecurityCoroutineContext.
            transactionTemplate.executeWithoutResult {
                if (normalizedStoreEnabled) {
                    val recruitment =
                        recruitmentRepository.findRecruitmentByStudyId(studyId.stringRepresentation)
                            ?: throw ResourceNotFoundException("Recruitment with studyId $studyId is not found.")
                    normalizationStore.append(recruitment.id, studyId.stringRepresentation, participants, groups)
                } else {
                    recruitmentRepository.bulkAddParticipantsAndGroups(
                        studyId.stringRepresentation,
                        objectMapper.writeValueAsString(participants),
                        WS_JSON.encodeToString(groups),
                    )
                }
                deploymentRepository.addAll(deployments)
                participantGroupRepository.addAll(participation)
            }
            groups.values.toList()
        }
}
