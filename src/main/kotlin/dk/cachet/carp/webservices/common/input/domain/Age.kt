package dk.cachet.carp.webservices.common.input.domain

import dk.cachet.carp.common.application.data.Data
import dk.cachet.carp.webservices.common.input.WSInputDataTypes
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Age in completed years.
 */
@Serializable
@SerialName(WSInputDataTypes.AGE_TYPE_NAME)
data class Age(
    val years: Int,
) : Data
