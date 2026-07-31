package dk.cachet.carp.webservices.common.input.domain

import dk.cachet.carp.common.application.data.Data
import dk.cachet.carp.webservices.common.input.WSInputDataTypes
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Instant

/**
 * Represents a diagnosis of a participant.
 *
 * We are using the
 * WHO [ICD-11](https://www.who.int/standards/classifications/classification-of-diseases) classification.
 */
@Serializable
@SerialName(WSInputDataTypes.DIAGNOSIS_TYPE_NAME)
data class Diagnosis(
    /**
     * The date when the diagnosis was effective.
     */
    val effectiveDate: Instant? = null,
    /**
     * A free text description of the diagnosis.
     */
    val diagnosis: String? = null,
    /**
     * The [ICD-11](https://www.who.int/standards/classifications/classification-of-diseases) code of the diagnosis.
     */
    val icd11Code: String,
    /**
     * Any conclusion or notes from the physician.
     */
    val conclusion: String? = null,
) : Data
