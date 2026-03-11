package dk.cachet.carp.webservices.study.dto

import dk.cachet.carp.webservices.study.domain.ParticipantOrderBy
import dk.cachet.carp.webservices.study.domain.SortDirection
import jakarta.validation.constraints.AssertTrue
import jakarta.validation.constraints.Min

/**
 * Request body for the participant accounts query endpoint.
 *
 * Paging is zero-based: `page = 0` refers to the first page.
 * If paging is used, both [page] and [size] must be provided.
 *
 * [search] matches participant ID and account identity in the recruitment snapshot.
 * [isDeployed] filters by whether a participant is part of any deployed participant group.
 * [sortBy] and [sortDirection] control ordering of the returned page.
 */
data class ParticipantAccountsRequestDto(
    @field:Min(0)
    val page: Int? = null,
    @field:Min(1)
    val size: Int? = null,
    val search: String? = null,
    val isDeployed: Boolean? = null,
    val sortDirection: SortDirection? = null,
    val sortBy: ParticipantOrderBy? = null,
) {
    /** Paging must be specified as a pair to avoid ambiguous partial pagination requests. */
    @AssertTrue(message = "page and size must either both be provided or both be omitted")
    fun hasValidPagination(): Boolean = (page == null) == (size == null)

    /** Direction without a field would silently change ordering semantics, so reject that input. */
    @AssertTrue(message = "sortDirection requires sortBy")
    fun hasValidSorting(): Boolean = sortDirection == null || sortBy != null
}
