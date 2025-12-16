package dk.cachet.carp.webservices.common.input.domain

import dk.cachet.carp.common.application.data.Data
import dk.cachet.carp.webservices.common.input.WSInputDataTypes
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Preferred language of the participant.
 */
@Serializable
@SerialName(WSInputDataTypes.LANGUAGE_TYPE_NAME)
data class PreferredLanguage(
    /** ISO 639-1 or 639-3 language code (e.g., "en", "da"). */
    val languageCode: String,
    /** Optional locale/region qualifier (e.g., "US", "DK"). */
    val region: String? = null,
    /** Human-readable language name if needed. */
    val displayName: String? = null,
) : Data
