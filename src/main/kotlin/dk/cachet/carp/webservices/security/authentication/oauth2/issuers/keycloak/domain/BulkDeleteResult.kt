package dk.cachet.carp.webservices.security.authentication.oauth2.issuers.keycloak.domain

/**
 * Outcome of one bulk anonymous-account delete request to the carp-keycloak extension
 * (`DELETE /bulk-users/anonymous/{groupId}`): how many users were removed ([deleted]) and kept ([skipped])
 * this request, how many of the kept were held back by an active session ([activeSkipped]), whether the whole
 * group was scanned ([exhausted]), and the [cursor] to pass to the next request to resume without re-scanning
 * kept members.
 *
 * The caller keeps calling with the returned [cursor] until [exhausted]; the study is fully cleaned for its
 * schedule only if no request reported an [activeSkipped]. Accounts created on/after the request's
 * `createdBefore` are kept but never counted in [activeSkipped] — they belong to a newer generation's schedule.
 */
data class BulkDeleteResult(
    val deleted: Int = 0,
    val skipped: Int = 0,
    val activeSkipped: Int = 0,
    val exhausted: Boolean = false,
    val cursor: Int = 0,
)
