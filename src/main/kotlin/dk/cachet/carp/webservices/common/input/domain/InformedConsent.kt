package dk.cachet.carp.webservices.common.input.domain

import dk.cachet.carp.common.application.data.Data
import dk.cachet.carp.webservices.common.input.WSInputDataTypes
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * The informed consent from a participant.
 */
@Serializable
@SerialName(WSInputDataTypes.CONSENT_TYPE_NAME)
data class InformedConsent(
    /**
     * The time this informed consent was signed.
     */
    val signedTimestamp: Instant? = Clock.System.now(),
    /**
     * The location where this informed consent was signed.
     */
    val signedLocation: String? = null,
    /**
     * The user ID of the participant who signed this consent.
     */
    val userId: String? = null,
    /**
     * The name of the participant who signed this consent.
     */
    val name: String,
    /**
     * The content of the signed consent.
     * This may be plain text or JSON.
     */
    val consent: String? = null,
    /**
     * The image of the provided signature in png format as bytes.
     */
    val signatureImage: String? = null,
) : Data
