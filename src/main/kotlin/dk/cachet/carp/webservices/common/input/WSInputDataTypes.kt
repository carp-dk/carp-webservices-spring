package dk.cachet.carp.webservices.common.input

import dk.cachet.carp.common.application.data.input.InputDataType
import dk.cachet.carp.common.application.data.input.InputDataTypeList
import dk.cachet.carp.common.application.data.input.Sex
import dk.cachet.carp.common.application.data.input.elements.SelectOne
import dk.cachet.carp.common.application.data.input.elements.Text
import dk.cachet.carp.webservices.common.input.domain.*

object WSInputDataTypes : InputDataTypeList() {
    /**
     * The [InputDataType] namespace of all CARP input data type definitions.
     *
     * TODO: This is a copy of Core input types (only one),
     * should be included via other method once core have more meaningful types.
     */
    private const val CARP_NAMESPACE: String = "dk.cachet.carp.input"
    private const val SEX_TYPE_NAME = "$CARP_NAMESPACE.sex"

    /**
     * Biological sex assigned at birth.
     */
    val SEX =
        add(
            inputDataType = InputDataType.fromString(SEX_TYPE_NAME),
            inputElement = SelectOne("Sex", Sex.entries.map { it.toString() }.toSet()),
            dataClass = Sex::class,
            inputToData = { Sex.valueOf(it) },
            dataToInput = { it.name },
        )

    /**
     * The [WSInputDataTypes] namespace of all Webservice input data type definitions.
     */

    private const val WS_NAMESPACE: String = "dk.carp.webservices.input"

    internal const val CONSENT_TYPE_NAME = "$WS_NAMESPACE.informed_consent"
    internal const val PHONE_NUMBER_TYPE_NAME = "$WS_NAMESPACE.phone_number"
    internal const val SSN_TYPE_NAME = "$WS_NAMESPACE.ssn"
    internal const val FULL_NAME_TYPE_NAME = "$WS_NAMESPACE.full_name"
    internal const val ADDRESS_TYPE_NAME = "$WS_NAMESPACE.address"
    internal const val DIAGNOSIS_TYPE_NAME = "$WS_NAMESPACE.diagnosis"
    internal const val HANDED_OUT_DEVICE_TYPE_NAME = "$WS_NAMESPACE.handed_out_device"

    /**
     * Phone name of a participant.
     */
    val PHONE_NUMBER =
        add(
            inputDataType = InputDataType.fromString(PHONE_NUMBER_TYPE_NAME),
            inputElement = Text("Phone Number"),
            dataClass = PhoneNumber::class,
            inputToData = { WS_JSON.decodeFromString(PhoneNumber.serializer(), it) },
            dataToInput = { WS_JSON.encodeToString(PhoneNumber.serializer(), it) },
        )

    /**
     * Social security number of a participant.
     */
    val SSN =
        add(
            inputDataType = InputDataType.fromString(SSN_TYPE_NAME),
            inputElement = Text("Social Security Number"),
            dataClass = SocialSecurityNumber::class,
            inputToData = { WS_JSON.decodeFromString(SocialSecurityNumber.serializer(), it) },
            dataToInput = { WS_JSON.encodeToString(SocialSecurityNumber.serializer(), it) },
        )

    /**
     * Full name of a participant.
     */
    val FULL_NAME =
        add(
            inputDataType = InputDataType.fromString(FULL_NAME_TYPE_NAME),
            inputElement = Text("Full Name"),
            dataClass = FullName::class,
            inputToData = { WS_JSON.decodeFromString(FullName.serializer(), it) },
            dataToInput = { WS_JSON.encodeToString(FullName.serializer(), it) },
        )

    /**
     * Address of a participant.
     */
    val ADDRESS =
        add(
            inputDataType = InputDataType.fromString(ADDRESS_TYPE_NAME),
            inputElement = Text("Address"),
            dataClass = Address::class,
            inputToData = { WS_JSON.decodeFromString(Address.serializer(), it) },
            dataToInput = { WS_JSON.encodeToString(Address.serializer(), it) },
        )

    /**
     * Informed consent signed by participant.
     */
    val INFORMED_CONSENT =
        add(
            inputDataType = InputDataType.fromString(CONSENT_TYPE_NAME),
            inputElement = Text("Informed Consent"),
            dataClass = InformedConsent::class,
            inputToData = { WS_JSON.decodeFromString(InformedConsent.serializer(), it) },
            dataToInput = { WS_JSON.encodeToString(InformedConsent.serializer(), it) },
        )

    /**
     * Diagnosis of a participant.
     */
    val DIAGNOSIS =
        add(
            inputDataType = InputDataType.fromString(DIAGNOSIS_TYPE_NAME),
            inputElement = Text("Diagnosis"),
            dataClass = Diagnosis::class,
            inputToData = { WS_JSON.decodeFromString(Diagnosis.serializer(), it) },
            dataToInput = { WS_JSON.encodeToString(Diagnosis.serializer(), it) },
        )

    /**
     * Device handed out to a participant.
     */
    val HANDED_OUT_DEVICE =
        add(
            inputDataType = InputDataType.fromString(HANDED_OUT_DEVICE_TYPE_NAME),
            inputElement = Text("Handed Out Device"),
            dataClass = HandedOutDevice::class,
            inputToData = { WS_JSON.decodeFromString(HandedOutDevice.serializer(), it) },
            dataToInput = { WS_JSON.encodeToString(HandedOutDevice.serializer(), it) },
        )
}
