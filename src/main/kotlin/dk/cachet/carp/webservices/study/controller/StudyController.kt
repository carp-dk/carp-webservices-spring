package dk.cachet.carp.webservices.study.controller

import dk.cachet.carp.common.application.EmailAddress
import dk.cachet.carp.common.application.UUID
import dk.cachet.carp.studies.infrastructure.RecruitmentServiceRequest
import dk.cachet.carp.studies.infrastructure.StudyServiceRequest
import dk.cachet.carp.webservices.account.service.AccountService
import dk.cachet.carp.webservices.common.constants.PathVariableName
import dk.cachet.carp.webservices.common.constants.RequestParamName
import dk.cachet.carp.webservices.common.input.WS_JSON
import dk.cachet.carp.webservices.common.serialisers.ApplicationRequestSerializer
import dk.cachet.carp.webservices.security.authentication.domain.Account
import dk.cachet.carp.webservices.security.authentication.service.AuthenticationService
import dk.cachet.carp.webservices.security.authorization.Claim
import dk.cachet.carp.webservices.security.authorization.Role
import dk.cachet.carp.webservices.study.domain.InactiveDeploymentInfo
import dk.cachet.carp.webservices.study.domain.ParticipantGroupsStatus
import dk.cachet.carp.webservices.study.domain.StudyOverview
import dk.cachet.carp.webservices.study.dto.AddParticipantsRequestDto
import dk.cachet.carp.webservices.study.dto.ParticipantAccountsDto
import dk.cachet.carp.webservices.study.serdes.RecruitmentRequestSerializer
import dk.cachet.carp.webservices.study.serdes.StudyRequestSerializer
import dk.cachet.carp.webservices.study.service.RecruitmentService
import dk.cachet.carp.webservices.study.service.StudyService
import io.swagger.v3.oas.annotations.Operation
import jakarta.validation.Valid
import kotlinx.coroutines.runBlocking
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@RestController
@Suppress("TooManyFunctions")
class StudyController(
    private val authenticationService: AuthenticationService,
    private val accountService: AccountService,
    private val studyService: StudyService,
    private val recruitmentService: RecruitmentService,
) {
    companion object {
        val LOGGER: Logger = LogManager.getLogger()
        val studySerializer: ApplicationRequestSerializer<*> = StudyRequestSerializer()
        val recruitmentSerializer: ApplicationRequestSerializer<*> = RecruitmentRequestSerializer()

        /** Endpoint URI constants */
        const val STUDY_SERVICE = "/api/study-service"
        const val RECRUITMENT_SERVICE = "/api/recruitment-service"
        const val RESEARCHERS = "/api/studies/{${PathVariableName.STUDY_ID}}/researchers"
        const val RESEARCH_ASSISTANTS = "/api/studies/{${PathVariableName.STUDY_ID}}/research-assistants"
        const val ADD_RESEARCHER = "/api/studies/{${PathVariableName.STUDY_ID}}/researchers/add"
        const val GET_STUDIES_OVERVIEW = "/api/studies/studies-overview"
        const val GET_PARTICIPANTS_ACCOUNTS = "/api/studies/{${PathVariableName.STUDY_ID}}/participants/accounts"
        const val GET_PARTICIPANT_GROUP_STATUS = "/api/studies/{${PathVariableName.STUDY_ID}}/participantGroup/status"
        const val ADD_PARTICIPANTS = "/api/studies/{${PathVariableName.STUDY_ID}}/participants/add"
        const val GET_INACTIVE_DEPLOYMENTS = "/api/studies/{${PathVariableName.STUDY_ID}}/inactive_deployments"
    }

    @PostMapping(value = [ADD_RESEARCHER])
    @PreAuthorize("canManageStudy(#studyId)")
    @ResponseStatus(HttpStatus.OK)
    suspend fun addResearcher(
        @PathVariable(PathVariableName.STUDY_ID) studyId: UUID,
        @RequestParam(RequestParamName.EMAIL) email: String,
        @RequestParam(RequestParamName.ROLE) role: Role,
    ) {
        LOGGER.info("Start POST: /api/studies/$studyId/researchers")
        return recruitmentService.inviteUserWithRole(studyId, email, role)
    }

    @GetMapping(value = [GET_PARTICIPANTS_ACCOUNTS])
    @PreAuthorize("canManageStudy(#studyId) or canLimitedManageStudy(#studyId)")
    @ResponseStatus(HttpStatus.OK)
    @Suppress("LongParameterList")
    suspend fun getParticipantAccounts(
        @PathVariable(PathVariableName.STUDY_ID) studyId: UUID,
        @RequestParam(name = RequestParamName.OFFSET, required = false) offset: Int?,
        @RequestParam(name = RequestParamName.LIMIT, required = false) limit: Int?,
        @RequestParam(name = RequestParamName.SEARCH, required = false) search: String?,
        @RequestParam(name = RequestParamName.IS_DESCENDING, required = false) isDescending: Boolean?,
        @RequestParam(name = "response_as_dto", required = false) responseAsDto: Boolean?,
    ): Any {
        LOGGER.info("Start GET: /api/studies/$studyId/participants/accounts")

        if (responseAsDto == true) {
            return ParticipantAccountsDto(
                offset = offset,
                search = search,
                limit = limit,
                total = recruitmentService.countParticipants(studyId, search),
                participants =
                    recruitmentService.getParticipants(
                        studyId,
                        offset,
                        limit,
                        search,
                        isDescending,
                    ),
            )
        }

        return recruitmentService.getParticipants(studyId, offset, limit, search, isDescending)
    }

    @GetMapping(value = [RESEARCHERS])
    @PreAuthorize("canManageStudy(#studyId) or canLimitedManageStudy(#studyId)")
    @ResponseStatus(HttpStatus.OK)
    suspend fun getResearchers(
        @PathVariable(PathVariableName.STUDY_ID) studyId: UUID,
    ): List<Account> {
        LOGGER.info("Start GET: /api/studies/$studyId/researchers")
        return accountService.findAllByClaim(Claim.ManageStudy(studyId))
    }

    @GetMapping(value = [RESEARCH_ASSISTANTS])
    @PreAuthorize("canManageStudy(#studyId) or canLimitedManageStudy(#studyId)")
    @ResponseStatus(HttpStatus.OK)
    suspend fun getResearcherAssistants(
        @PathVariable(PathVariableName.STUDY_ID) studyId: UUID,
    ): List<Account> {
        LOGGER.info("Start GET: /api/studies/$studyId/research-assistants")
        return accountService.findAllByClaim(Claim.LimitedManageStudy(studyId))
    }

    @GetMapping(value = [GET_PARTICIPANT_GROUP_STATUS])
    @PreAuthorize("canManageStudy(#studyId) or canLimitedManageStudy(#studyId)")
    @ResponseStatus(HttpStatus.OK)
    suspend fun getParticipantGroupStatus(
        @PathVariable(PathVariableName.STUDY_ID) studyId: UUID,
    ): String {
        LOGGER.info("Start GET: /api/studies/$studyId/participantGroup/status")
        val result = recruitmentService.getParticipantGroupsStatus(studyId)
        return WS_JSON.encodeToString(ParticipantGroupsStatus.serializer(), result)
    }

    @DeleteMapping(value = [RESEARCHERS])
    @PreAuthorize("canManageStudy(#studyId)")
    @ResponseStatus(HttpStatus.OK)
    suspend fun removeResearcher(
        @PathVariable(PathVariableName.STUDY_ID) studyId: UUID,
        @RequestParam(RequestParamName.EMAIL) email: String,
    ): Boolean = recruitmentService.removeStudyManager(studyId, email)

    @GetMapping(value = [GET_STUDIES_OVERVIEW])
    @ResponseStatus(HttpStatus.OK)
    suspend fun getStudiesOverview(): List<StudyOverview> {
        LOGGER.info("Start POST: /api/studies/studies-overview")
        return studyService.getStudiesOverview(authenticationService.getId())
    }

    @PostMapping(value = [ADD_PARTICIPANTS])
    @PreAuthorize("canManageStudy(#studyId) or canLimitedManageStudy(#studyId)")
    @ResponseStatus(HttpStatus.OK)
    suspend fun addParticipants(
        @PathVariable(PathVariableName.STUDY_ID) studyId: UUID,
        @Valid @RequestBody request: AddParticipantsRequestDto,
    ) {
        LOGGER.info("Start POST: /api/studies/$studyId/participants/add")
        request.emails.forEach { e -> recruitmentService.core.addParticipant(studyId, EmailAddress(e)) }
    }

    /**
     * Get inactive participants.
     * @param studyId The study id.
     * @param lastUpdate The last updated time in HOURS.
     */
    @GetMapping(value = [GET_INACTIVE_DEPLOYMENTS])
    @PreAuthorize("canManageStudy(#studyId) or canLimitedManageStudy(#studyId)")
    @ResponseStatus(HttpStatus.OK)
    suspend fun getInactiveParticipants(
        @PathVariable(PathVariableName.STUDY_ID) studyId: UUID,
        @RequestParam(name = RequestParamName.OFFSET, required = false, defaultValue = "0") offset: Int,
        @RequestParam(name = RequestParamName.LIMIT, required = false, defaultValue = "-1") limit: Int,
        @RequestParam(name = RequestParamName.LAST_UPDATE, required = true) lastUpdate: Int,
    ): List<InactiveDeploymentInfo> {
        LOGGER.info("Start GET: /api/studies/$studyId/participants/inactive")
        return runBlocking { recruitmentService.getInactiveDeployments(studyId, lastUpdate, offset, limit) }
    }

    @Operation(tags = ["study/recruitments.json"])
    @PostMapping(value = [RECRUITMENT_SERVICE])
    suspend fun recruitments(
        @RequestBody httpMessage: String,
    ): ResponseEntity<*> {
        val request = recruitmentSerializer.deserializeRequest(RecruitmentServiceRequest.Serializer, httpMessage)
        LOGGER.info("Start POST: $RECRUITMENT_SERVICE -> ${request::class.simpleName}")
        val result = recruitmentService.core.invoke(request)
        return recruitmentSerializer.serializeResponse(request, result).let { ResponseEntity.ok(it) }
    }

    @PostMapping(value = [STUDY_SERVICE])
    @Operation(tags = ["study/studies.json"])
    suspend fun studies(
        @RequestBody httpMessage: String,
    ): ResponseEntity<Any> {
        val request = studySerializer.deserializeRequest(StudyServiceRequest.Serializer, httpMessage)
        LOGGER.info("Start POST: $STUDY_SERVICE -> ${request::class.simpleName}")
        val result = studyService.invoke(request)
        return studySerializer.serializeResponse(request, result).let { ResponseEntity.ok(it) }
    }
}
