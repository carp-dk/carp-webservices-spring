package dk.cachet.carp.webservices.study.dto

/**
 * Paged response for the participant accounts query endpoint.
 *
 * [total] is the total number of matching participants before paging is applied.
 * [content] contains the current page of participant summaries.
 */
data class ParticipantAccountsResponseDto(
    val page: Int?,
    val size: Int?,
    val total: Int,
    val content: List<ParticipantAccountSummaryDto>,
)
