package dk.cachet.carp.webservices.study.dto

/**
 * Aggregate counts of a study's participant-group deployment statuses.
 *
 * Powers the study overview pie chart without shipping the full participant-group status list to the
 * client. The four buckets count groups that are in a deployment, by deployment state; [total] is the
 * total number of participant groups (matching the pie's center label).
 */
data class DeploymentStatusCountsDto(
    val invited: Int,
    val deployingDevices: Int,
    val running: Int,
    val stopped: Int,
    val total: Int,
)
