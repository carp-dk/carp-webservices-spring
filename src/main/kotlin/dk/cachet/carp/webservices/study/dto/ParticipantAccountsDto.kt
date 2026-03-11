package dk.cachet.carp.webservices.study.dto

import dk.cachet.carp.webservices.security.authentication.domain.Account

data class ParticipantAccountsDto(
    val limit: Int?,
    val offset: Int?,
    val search: String?,
    val total: Int,
    val participants: List<Account>,
)
