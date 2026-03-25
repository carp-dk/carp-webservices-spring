package dk.cachet.carp.webservices.study.service.impl

import com.fasterxml.jackson.databind.ObjectMapper
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
import dk.cachet.carp.studies.application.users.Participant
import dk.cachet.carp.studies.domain.users.StagedParticipantGroup
import dk.cachet.carp.webservices.common.input.WS_JSON
import dk.cachet.carp.webservices.common.services.CoreServiceContainer
import dk.cachet.carp.webservices.deployment.domain.ParticipantGroup
import dk.cachet.carp.webservices.deployment.domain.StudyDeployment
import dk.cachet.carp.webservices.deployment.repository.CoreDeploymentRepository
import dk.cachet.carp.webservices.deployment.repository.CoreParticipationRepository
import dk.cachet.carp.webservices.security.config.SecurityCoroutineContext
import dk.cachet.carp.webservices.study.repository.RecruitmentRepository
import dk.cachet.carp.webservices.study.service.AnonymousService
import kotlinx.coroutines.*
import kotlinx.datetime.Clock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.springframework.stereotype.Service
import dk.cachet.carp.deployments.domain.StudyDeployment as CoreStudyDeployment

@Service
class AnonymousServiceImp(
    private val objectMapper: ObjectMapper,
    services: CoreServiceContainer,
    private val deploymentRepository: CoreDeploymentRepository,
    private val recruitmentRepository: RecruitmentRepository,
    private val participantGroupRepository: CoreParticipationRepository
) : AnonymousService {
    val studyService = services.studyService

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
                    // Participant
                    val uuid = UUID(it.first)

                    val participant =
                        Participant(AccountIdentity.fromUsername(it.first), uuid)

                    // Deployment
                    val stagedGroup =
                        StagedParticipantGroup(uuid).apply {
                            addParticipants(setOf(uuid))
                            markAsDeployed()
                        }

                    val invitations =
                        listOf(
                            ParticipantInvitation(
                                uuid,
                                AssignedTo.Roles(setOf(roleName)),
                                UsernameAccountIdentity(it.first),
                                study.invitation,
                            ),
                        )

                    val newDeployment =
                        CoreStudyDeployment.fromInvitations(
                            study.protocolSnapshot!!,
                            invitations,
                            uuid,
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
                    val group = dk.cachet.carp.deployments.domain.users.ParticipantGroup.fromNewDeployment(
                        uuid,
                        study.protocolSnapshot!!.toObject()
                    )

                    for (invitation in invitations) {
                        val participation =
                            Participation(
                                uuid,
                                invitation.assignedRoles,
                                invitation.participantId
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

                        val account = Account(AccountIdentity.fromUsername(it.first), uuid)
                        val studyInvitation = invitation.invitation

                        group.addParticipation(
                            account,
                            studyInvitation,
                            participation,
                            assignedDevices.toSet()
                        )
                    }

                    val snapshotToSave =
                        WS_JSON.encodeToString(ParticipantGroupSnapshot.serializer(), group.getSnapshot())

                    val participation = ParticipantGroup()
                    participation.snapshot = objectMapper.readTree(snapshotToSave)

                    ParticipantBundle(
                        participant,
                        uuid to stagedGroup,
                        studyDeploymentToSave,
                        participation
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
            val participantGroupString = Json.encodeToString(groups)
            recruitmentRepository.bulkAddParticipantsAndGroups(
                studyId.stringRepresentation,
                objectMapper.writeValueAsString(participants),
                participantGroupString,
            )
            deploymentRepository.addAll(deployments)
            participantGroupRepository.addAll(participation)
            groups.values.toList()
        }
}
