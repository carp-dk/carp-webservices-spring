package dk.cachet.carp.webservices.datastream.dto

import dk.cachet.carp.webservices.datastream.domain.DateTaskQuantityTriple
import kotlin.time.Instant

data class DataStreamsSummaryDto(
    val data: List<DateTaskQuantityTriple>,
    val studyId: String,
    val deploymentId: String?,
    val participantId: String?,
    val scope: String,
    val type: String,
    val from: Instant,
    val to: Instant,
)
