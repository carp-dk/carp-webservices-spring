package dk.cachet.carp.webservices.common.input

import dk.cachet.carp.common.application.data.Data
import dk.cachet.carp.common.infrastructure.serialization.createDefaultJSON
import dk.cachet.carp.webservices.common.input.domain.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

/**
 * Types in the [dk.cachet.carp.webservices.common] module which need to be registered when using [Json] serializer.
 */
private val WS_MODULE =
    SerializersModule {
        polymorphic(Data::class) {
            subclass(InformedConsent::class)
            subclass(SocialSecurityNumber::class)
            subclass(PhoneNumber::class)
            subclass(FullName::class)
            subclass(Address::class)
            subclass(Diagnosis::class)
            subclass(HandedOutDevice::class)
            subclass(ParticipantNote::class)
            subclass(EducationalDegree::class)
            subclass(OnboardingResearcher::class)
            subclass(PreferredLanguage::class)
            subclass(Occupation::class)
            subclass(Age::class)
            subclass(DateOfBirth::class)
        }
    }

/** Carp Webservice JSON serializer. */
val WS_JSON = createDefaultJSON(WS_MODULE)
