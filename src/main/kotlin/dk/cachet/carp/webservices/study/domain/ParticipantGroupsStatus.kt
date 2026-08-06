package dk.cachet.carp.webservices.study.domain

import dk.cachet.carp.common.application.UUID
import dk.cachet.carp.common.application.users.EmailAccountIdentity
import dk.cachet.carp.deployments.application.StudyDeploymentStatus
import dk.cachet.carp.studies.application.users.Participant
import dk.cachet.carp.studies.application.users.ParticipantGroupStatus
import dk.cachet.carp.webservices.security.authentication.domain.Account
import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
data class ParticipantGroupsStatus(
    val groups: List<ParticipantGroupInfo>,
    val groupStatuses: List<ParticipantGroupStatus>,
    // Total number of matching participant groups before paging. Null for unpaged (legacy) calls.
    val total: Int? = null,
)

@Serializable
data class ParticipantGroupInfo(
    val participantGroupId: UUID,
    val deploymentStatus: StudyDeploymentStatus,
    val participants: List<ParticipantAccount>,
)

@Serializable
data class ParticipantAccount(
    val participantId: UUID,
    var firstName: String? = null,
    var lastName: String? = null,
    var email: String? = null,
    var role: String? = null,
    var dateOfLastDataUpload: Instant? = null,
) {
    companion object {
        fun fromParticipant(participant: Participant): ParticipantAccount =
            ParticipantAccount(
                participantId = participant.id,
                email = (participant.accountIdentity as? EmailAccountIdentity)?.emailAddress?.address,
            )
    }

    fun lateInitFrom(account: Account) {
        (firstName ?: account.firstName).also { firstName = it }
        (lastName ?: account.lastName).also { lastName = it }
        (email ?: account.email).also { email = it }
        (role ?: account.role).also { role = it.toString() }
    }
}

data class InactiveDeploymentInfo(
    val deploymentId: UUID,
    val dateOfLastDataUpload: Instant?,
)
