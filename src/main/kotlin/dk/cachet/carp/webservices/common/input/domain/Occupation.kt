package dk.cachet.carp.webservices.common.input.domain

import dk.cachet.carp.common.application.data.Data
import dk.cachet.carp.webservices.common.input.WSInputDataTypes
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Occupation details of a participant.
 */
@Serializable
@SerialName(WSInputDataTypes.OCCUPATION_TYPE_NAME)
data class Occupation(
    /** One or more selected occupations for the participant. */
    val roles: List<String> = emptyList(),
    /** Optional free-text explanation if none of the predefined roles fit. */
    val other: String? = null,
) : Data
