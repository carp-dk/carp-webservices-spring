package dk.cachet.carp.webservices.common.input.domain

import dk.cachet.carp.common.application.data.Data
import dk.cachet.carp.webservices.common.input.WSInputDataTypes
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A general note about a participant.
 */
@Serializable
@SerialName(WSInputDataTypes.NOTE_TYPE_NAME)
data class ParticipantNote(
    /** Free-text note tied to the participant. */
    val note: String,
) : Data
