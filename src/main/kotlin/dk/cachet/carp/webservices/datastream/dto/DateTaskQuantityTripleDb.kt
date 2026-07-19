package dk.cachet.carp.webservices.datastream.dto

import java.time.LocalDateTime

data class DateTaskQuantityTripleDb(
    val date: LocalDateTime,
    val task: String,
    val quantity: Long,
)
