package dk.cachet.carp.webservices.study.domain

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue

enum class ParticipantOrderBy {
    AccountIdentity,
    IsDeployed,
    InvitedOn,
    ;

    @JsonValue
    fun toJson(): String =
        when (this) {
            AccountIdentity -> "accountIdentity"
            IsDeployed -> "isDeployed"
            InvitedOn -> "invitedOn"
        }

    companion object {
        @JvmStatic
        @JsonCreator
        fun fromJson(value: String): ParticipantOrderBy =
            when (value.lowercase()) {
                "accountidentity", "account_identity", "username", "email" -> AccountIdentity
                "isdeployed", "is_deployed" -> IsDeployed
                "invitedon", "invited_on" -> InvitedOn
                else -> throw IllegalArgumentException("Unsupported sortBy value: $value")
            }
    }
}
