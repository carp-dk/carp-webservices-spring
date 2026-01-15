package dk.cachet.carp.webservices.common.input.domain

import dk.cachet.carp.common.application.data.Data
import dk.cachet.carp.webservices.common.input.WSInputDataTypes
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Information about the researcher who onboarded the participant.
 */
@Serializable
@SerialName(WSInputDataTypes.ONBOARDING_RESEARCHER_TYPE_NAME)
data class OnboardingResearcher(
    val researcherId: String,
    val researcherName: String,
) : Data
