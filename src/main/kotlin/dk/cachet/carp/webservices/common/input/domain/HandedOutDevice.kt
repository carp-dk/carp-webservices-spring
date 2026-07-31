package dk.cachet.carp.webservices.common.input.domain

import dk.cachet.carp.common.application.data.Data
import dk.cachet.carp.webservices.common.input.WSInputDataTypes
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Information about devices handed out to a participant.
 */
@Serializable
@SerialName(WSInputDataTypes.HANDED_OUT_DEVICE_TYPE_NAME)
data class HandedOutDevice(
    /** Devices handed out to the participant. */
    val devices: List<Device> = emptyList(),
) : Data {
    /**
     * Details about a handed out device.
     */
    @Serializable
    data class Device(
        /** Identifier or serial/asset tag of the handed out device. */
        val deviceId: String,
        /** Optional device model or description to help inventory tracking. */
        val deviceModel: String? = null,
        /** When the device was handed out to the participant. */
        val handedOutAt: Instant? = Clock.System.now(),
        /** Free-form notes about the handover (e.g., accessories, condition). */
        val notes: String? = null,
    )
}
