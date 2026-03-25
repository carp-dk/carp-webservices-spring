package dk.cachet.carp.webservices.common.input

import dk.cachet.carp.common.application.data.Data
import dk.cachet.carp.common.application.data.input.Sex
import dk.cachet.carp.webservices.common.input.domain.*
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.serialization.PolymorphicSerializer
import kotlin.test.Test
import kotlin.test.assertEquals

class WSInputDataTypesSerializationTest {
    @Suppress("LongMethod")
    @Test
    fun `all input data types serialize and deserialize with WS_JSON`() {
        val fixedInstant = Instant.parse("2024-08-01T09:00:00Z")
        val dataObjects =
            listOf<Data>(
                Sex.Male,
                PhoneNumber(countryCode = "1-246", isoCode = "BB", number = "123456789"),
                SocialSecurityNumber(socialSecurityNumber = "123-45-6789", country = "USA"),
                FullName(firstName = "John", middleName = "A.", lastName = "Doe"),
                Address(
                    address1 = "123 Main St",
                    address2 = "Apt 4B",
                    street = "Main St",
                    city = "Springfield",
                    postalCode = "12345",
                    country = "USA",
                ),
                InformedConsent(
                    signedTimestamp = fixedInstant,
                    signedLocation = "New York, NY",
                    userId = "user-1",
                    name = "Jane Doe",
                    consent = """{"agree":true}""",
                    signatureImage = "signature-bytes",
                ),
                Diagnosis(
                    effectiveDate = fixedInstant,
                    diagnosis = "Chronic obstructive pulmonary disease",
                    icd11Code = "CA40",
                    conclusion = "Patient needs regular follow-ups",
                ),
                HandedOutDevice(
                    devices =
                        listOf(
                            HandedOutDevice.Device(
                                deviceId = "Device-123",
                                deviceModel = "Polar watch 2024",
                                handedOutAt = fixedInstant,
                                notes = "Includes charging dock and spare strap",
                            ),
                            HandedOutDevice.Device(
                                deviceId = "Device-456",
                                handedOutAt = fixedInstant,
                                notes = "Charger missing",
                            ),
                        ),
                ),
                ParticipantNote(note = "Requires wheelchair access"),
                EducationalDegree(
                    level = EducationalDegree.IscedLevel.ISCED_6,
                    details = "Bachelor of Science in Computer Science",
                ),
                OnboardingResearcher(
                    researcherId = "researcher-001",
                    researcherName = "Dr. `John Smith",
                    institutionName = "Copenhagen Research Platform",
                ),
                PreferredLanguage(languageCode = "da", region = "DK", displayName = "Danish"),
                Occupation(
                    roles = listOf("Engineer", "Researcher"),
                    other = "Part-time lecturer",
                ),
                Age(years = 42),
                DateOfBirth(date = LocalDate.parse("1982-04-12")),
            )

        val dataSerializer = PolymorphicSerializer(Data::class)

        dataObjects.forEach { data ->
            val serialized = WS_JSON.encodeToString(dataSerializer, data)
            val deserialized = WS_JSON.decodeFromString(dataSerializer, serialized)
            assertEquals(data, deserialized)
        }
    }
}
