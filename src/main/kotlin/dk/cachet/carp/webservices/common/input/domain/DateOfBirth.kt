package dk.cachet.carp.webservices.common.input.domain

import dk.cachet.carp.common.application.data.Data
import dk.cachet.carp.webservices.common.input.WSInputDataTypes
import kotlinx.datetime.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Date of birth in ISO 8601 calendar date format.
 */
@Serializable
@SerialName(WSInputDataTypes.DATE_OF_BIRTH_TYPE_NAME)
data class DateOfBirth(
    val date: LocalDate,
) : Data
