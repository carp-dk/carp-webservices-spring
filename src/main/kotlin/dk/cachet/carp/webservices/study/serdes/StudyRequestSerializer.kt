package dk.cachet.carp.webservices.study.serdes

import dk.cachet.carp.common.application.services.ApplicationService
import dk.cachet.carp.common.infrastructure.services.ApplicationServiceRequest
import dk.cachet.carp.studies.application.StudyDetails
import dk.cachet.carp.studies.application.StudyStatus
import dk.cachet.carp.studies.application.users.Participant
import dk.cachet.carp.studies.application.users.ParticipantGroupStatus
import dk.cachet.carp.studies.infrastructure.RecruitmentServiceRequest
import dk.cachet.carp.studies.infrastructure.StudyServiceRequest
import dk.cachet.carp.webservices.common.serialisers.ApplicationRequestSerializer
import kotlinx.serialization.serializer

class StudyRequestSerializer : ApplicationRequestSerializer<StudyServiceRequest<*>>() {
    @Suppress("UNCHECKED_CAST")
    override fun <TService : ApplicationService<TService, *>> serializeResponse(
        request: ApplicationServiceRequest<TService, *>,
        content: Any?,
    ): Any? {
        return when (request) {
            is StudyServiceRequest.CreateStudy,
            is StudyServiceRequest.GetStudyStatus,
            is StudyServiceRequest.SetInternalDescription,
            is StudyServiceRequest.SetInvitation,
            is StudyServiceRequest.SetProtocol,
            is StudyServiceRequest.RemoveProtocol,
            is StudyServiceRequest.GoLive,
            ->
                json.encodeToString(serializer<StudyStatus>(), content as StudyStatus)
            is StudyServiceRequest.GetStudyDetails ->
                json.encodeToString(serializer<StudyDetails>(), content as StudyDetails)
            is StudyServiceRequest.GetStudiesOverview ->
                json.encodeToString(serializer<List<StudyDetails>>(), content as List<StudyDetails>)
            is StudyServiceRequest.Remove ->
                json.encodeToString(serializer<Boolean>(), content as Boolean)
            else -> content
        }
    }
}

class RecruitmentRequestSerializer : ApplicationRequestSerializer<RecruitmentServiceRequest<*>>() {
    @Suppress("UNCHECKED_CAST")
    override fun <TService : ApplicationService<TService, *>> serializeResponse(
        request: ApplicationServiceRequest<TService, *>,
        content: Any?,
    ): Any? {
        return when (request) {
            is RecruitmentServiceRequest.AddParticipantByEmailAddress,
            is RecruitmentServiceRequest.AddParticipantByUsername,
            is RecruitmentServiceRequest.GetParticipant,
            ->
                json.encodeToString(serializer<Participant>(), content as Participant)
            is RecruitmentServiceRequest.GetParticipants ->
                json.encodeToString(serializer<List<Participant>>(), content as List<Participant>)
            is RecruitmentServiceRequest.InviteNewParticipantGroup,
            is RecruitmentServiceRequest.StopParticipantGroup,
            ->
                json.encodeToString(serializer<ParticipantGroupStatus>(), content as ParticipantGroupStatus)
            is RecruitmentServiceRequest.GetParticipantGroupStatusList ->
                json.encodeToString(serializer<List<ParticipantGroupStatus>>(), content as List<ParticipantGroupStatus>)
            else -> content
        }
    }
}
