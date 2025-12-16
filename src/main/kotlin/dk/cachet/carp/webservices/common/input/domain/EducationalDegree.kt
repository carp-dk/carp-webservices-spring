package dk.cachet.carp.webservices.common.input.domain

import dk.cachet.carp.common.application.data.Data
import dk.cachet.carp.webservices.common.input.WSInputDataTypes
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Highest completed educational degree using the ISCED framework.
 *
 * Reference: https://uis.unesco.org/en/topic/international-standard-classification-education-isced
 */
@Serializable
@SerialName(WSInputDataTypes.EDUCATIONAL_DEGREE_TYPE_NAME)
data class EducationalDegree(
    /** ISCED level describing the degree. */
    val level: IscedLevel,
    /** Optional free-text details (e.g., subject, institution). */
    val details: String? = null,
) : Data {
    @Serializable
    enum class IscedLevel {
        @SerialName("ISCED_0_1")
        ISCED_0_1,

        @SerialName("ISCED_2")
        ISCED_2,

        @SerialName("ISCED_3")
        ISCED_3,

        @SerialName("ISCED_4")
        ISCED_4,

        @SerialName("ISCED_5")
        ISCED_5,

        @SerialName("ISCED_6")
        ISCED_6,

        @SerialName("ISCED_7")
        ISCED_7,

        @SerialName("ISCED_8")
        ISCED_8,
    }
}
