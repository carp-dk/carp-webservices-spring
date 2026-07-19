package dk.cachet.carp.webservices.datastream.dto

import java.time.LocalDate

data class DateQuantityPairDb(
    val date: LocalDate,
    val quantity: Long,
)
