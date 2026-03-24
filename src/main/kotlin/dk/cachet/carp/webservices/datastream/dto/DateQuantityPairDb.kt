package dk.cachet.carp.webservices.datastream.dto

import java.sql.Date

data class DateQuantityPairDb(
    val date: Date,
    val quantity: Long,
)
