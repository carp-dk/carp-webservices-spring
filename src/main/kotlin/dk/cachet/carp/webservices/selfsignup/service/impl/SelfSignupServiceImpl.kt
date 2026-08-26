package dk.cachet.carp.webservices.selfsignup.service.impl

import dk.cachet.carp.common.application.UUID
import dk.cachet.carp.webservices.common.exception.responses.BadRequestException
import dk.cachet.carp.webservices.common.exception.responses.ConflictException
import dk.cachet.carp.webservices.common.exception.responses.ResourceNotFoundException
import dk.cachet.carp.webservices.common.services.CoreServiceContainer
import dk.cachet.carp.webservices.selfsignup.domain.StudySelfSignupConfig
import dk.cachet.carp.webservices.selfsignup.dto.EnableSelfSignupRequestDto
import dk.cachet.carp.webservices.selfsignup.dto.SelfSignupConfigResponseDto
import dk.cachet.carp.webservices.selfsignup.repository.StudySelfSignupStore
import dk.cachet.carp.webservices.selfsignup.service.SelfSignupService
import dk.cachet.carp.webservices.selfsignup.util.ShortCodeGenerator
import org.springframework.stereotype.Service

@Service
class SelfSignupServiceImpl(
    private val services: CoreServiceContainer,
    private val store: StudySelfSignupStore,
) : SelfSignupService {
    companion object {
        private const val MAX_CODE_GENERATION_ATTEMPTS = 5
    }

    override suspend fun enable(
        studyId: UUID,
        request: EnableSelfSignupRequestDto,
    ): SelfSignupConfigResponseDto {
        // Runs under the caller's own authenticated request (canManageStudy), so it's safe and correct to
        // use the decorated studyService here, unlike the public signup path.
        val status = services.studyService.getStudyStatus(studyId)
        if (!status.canDeployToParticipants) throw BadRequestException("Study $studyId is not live.")

        val protocol = services.studyService.getStudyDetails(studyId).protocolSnapshot
        if (protocol == null || protocol.participantRoles.none { it.role == request.participantRoleName }) {
            throw BadRequestException("Participant role ${request.participantRoleName} does not exist.")
        }

        val studyIdString = studyId.stringRepresentation

        if (store.findByStudyId(studyIdString) != null) {
            return updateExisting(studyIdString, request)
        }

        repeat(MAX_CODE_GENERATION_ATTEMPTS) {
            val config =
                StudySelfSignupConfig(
                    studyId = studyIdString,
                    shortCode = ShortCodeGenerator.generate(),
                    enabled = true,
                    participantRoleName = request.participantRoleName,
                    maxParticipants = request.maxParticipants,
                    currentParticipantCount = 0,
                    clientId = request.clientId,
                    redirectUri = request.redirectUri,
                    subdomain = request.subdomain,
                    expirationSeconds = request.expirationSeconds,
                )
            if (store.insert(config)) return config.toDto()

            // insert() failed on a unique-constraint violation. If a row for this study now exists, a
            // concurrent enable call for the SAME study won the race - fall back to updating it. Otherwise
            // the generated short code collided with a DIFFERENT study - retry with a fresh one.
            if (store.findByStudyId(studyIdString) != null) return updateExisting(studyIdString, request)
        }
        throw ConflictException(
            "Could not allocate a unique self-signup code for study $studyId after " +
                "$MAX_CODE_GENERATION_ATTEMPTS attempts.",
        )
    }

    private fun updateExisting(
        studyIdString: String,
        request: EnableSelfSignupRequestDto,
    ): SelfSignupConfigResponseDto {
        store.update(
            studyId = studyIdString,
            enabled = true,
            participantRoleName = request.participantRoleName,
            maxParticipants = request.maxParticipants,
            clientId = request.clientId,
            redirectUri = request.redirectUri,
            subdomain = request.subdomain,
            expirationSeconds = request.expirationSeconds,
        )
        return checkNotNull(store.findByStudyId(studyIdString)).toDto()
    }

    override suspend fun end(studyId: UUID): SelfSignupConfigResponseDto {
        val studyIdString = studyId.stringRepresentation
        val existing =
            store.findByStudyId(studyIdString)
                ?: throw ResourceNotFoundException("Self-signup is not configured for study $studyId.")
        store.setEnabled(studyIdString, enabled = false)
        return existing.copy(enabled = false).toDto()
    }

    override suspend fun getConfig(studyId: UUID): SelfSignupConfigResponseDto? =
        store.findByStudyId(studyId.stringRepresentation)?.toDto()

    private fun StudySelfSignupConfig.toDto() =
        SelfSignupConfigResponseDto(
            shortCode = shortCode,
            enabled = enabled,
            participantRoleName = participantRoleName,
            maxParticipants = maxParticipants,
            currentParticipantCount = currentParticipantCount,
        )
}
