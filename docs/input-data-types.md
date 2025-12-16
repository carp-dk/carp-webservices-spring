# Input Data Types Documentation

- Input Data Usage
  * [Protocol Example](#protocol-example)
  * [Endpoint Example](#endpoint-example)
- Supported Input Data Types
  * [Address](#address)
  * [Diagnosis](#diagnosis)
  * [HandedOutDevice](#handedoutdevice)
  * [FullName](#fullname)
  * [InformedConsent](#informedconsent)
  * [PhoneNumber](#phonenumber)
  * [SocialSecurityNumber](#socialsecuritynumber)
- Supported CARP.CORE Input Data Types
  * [Sex](#sex)

## Protocol Example
The `expectedParticipantData` section of the study protocol is used to define type information for the study. Any data type utilized must be defined here.

consider a study protocol with the following Roles

```json

"participantRoles":[
    {
        "role":"Participant",
        "isOptional":false
    },
    {
        "role":"Guardian",
        "isOptional":true
    }
],
```
The `expectedParticipantData` can be set as
- when no `assignedTo` used, the data is shared and can be set by any participant
- data can be assigned to multiple roles


```json

"expectedParticipantData":[
    {
        "attribute":{
            "__type":"dk.cachet.carp.common.application.users.ParticipantAttribute.DefaultParticipantAttribute",
            "inputDataType":"dk.carp.webservices.input.address"
        }
    },
    {
        "attribute":{
            "__type":"dk.cachet.carp.common.application.users.ParticipantAttribute.DefaultParticipantAttribute",
            "inputDataType":"dk.carp.webservices.input.phone_number"
        },
        "assignedTo":{
            "__type":"dk.cachet.carp.common.application.users.AssignedTo.Roles",
            "roleNames":[
                "Participant"
            ]
        }
    },
    {
        "attribute":{
            "__type":"dk.cachet.carp.common.application.users.ParticipantAttribute.DefaultParticipantAttribute",
            "inputDataType":"dk.cachet.carp.input.sex"
        },
        "assignedTo":{
            "__type":"dk.cachet.carp.common.application.users.AssignedTo.Roles",
            "roleNames":[
                "Participant",
                "Guardian"
            ]
        }
    }
]

```

## Endpoint Example
The `setParticipantData` and `getParticipantData` service requests from the `participation-service` endpoint are used to set and retrieve participation data.

for common data, any role can set the data
Example setting participant data
```json
{
    "__type": "dk.cachet.carp.deployments.infrastructure.ParticipationServiceRequest.SetParticipantData",
    "apiVersion": "1.2",
    "studyDeploymentId": "36bb3081-44e6-4c32-b162-37ac27656174",
    "data": {
     "dk.carp.webservices.input.informed_consent": {
            "name": "John Smith",
            "__type": "dk.carp.webservices.input.informed_consent",
            "userId": "12345",
            "consent": "you know..",
            "signatureImage": "parsed signature image",
            "signedTimestamp": "2024-07-26T08:59:52.311225Z"
        }
    },
    "inputByParticipantRole": "Participant"
}
```

## Address

Represents an address with various fields.

### Data Type Name

`dk.carp.webservices.input.address`

### Fields

- `address1: String?` - The first line of the address. This field is optional and can be `null`.
- `address2: String?` - The second line of the address. This field is optional and can be `null`.
- `street: String?` - The street of the address. This field is optional and can be `null`.
- `city: String?` - The city of the address. This field is optional and can be `null`.
- `postalCode: String?` - The postal code of the address. This field is optional and can be `null`.
- `country: String?` - The country of the address. This field is optional and can be `null`.

### Example

Here is an example of how to create an instance of the `Address` class:

```kotlin
val address = Address(
    address1 = "123 Main St",
    address2 = "Apt 4B",
    street = "Main St",
    city = "Springfield",
    postalCode = "12345",
    country = "USA"
)
```

## Diagnosis

Represents a medical diagnosis with various fields.

### Data Type Name

`dk.carp.webservices.input.diagnosis`

### Fields

- `effectiveDate: Instant?` - The date when the diagnosis was effective. This field is optional and can be `null`.
- `diagnosis: String?` - A free text description of the diagnosis. This field is optional and can be `null`.
- `icd11Code: String` - The [ICD-11](https://www.who.int/standards/classifications/classification-of-diseases) code of the diagnosis. This field is required.
- `conclusion: String?` - Any conclusion or notes from the physician. This field is optional and can be `null`.

### Example

Here is an example of how to create an instance of the `Diagnosis` class:

```kotlin
val diagnosis = Diagnosis(
    effectiveDate = Instant.parse("2023-07-01T00:00:00Z"),
    diagnosis = "Chronic obstructive pulmonary disease",
    icd11Code = "CA40",
    conclusion = "Patient needs regular follow-ups"
)
```

## HandedOutDevice

Represents devices handed out to a participant for the study.

### Data Type Name

`dk.carp.webservices.input.handed_out_device`

### Fields

  - `devices: List<Device>` - Devices handed out to the participant, each containing:
  - `deviceId: String` - Identifier or serial/asset tag of the handed out device.
  - `deviceModel: String?` - Optional model or description to help inventory tracking.
  - `handedOutAt: Instant?` - When the device was handed out to the participant. Defaults to now if omitted.
  - `notes: String?` - Free-form notes about accessories, condition, or anything noteworthy.

### Example

Here is an example of how to create an instance of the `HandedOutDevice` class:

```kotlin
val handedOutDevice = HandedOutDevice(
    devices = listOf(
        HandedOutDevice.Device(
            deviceId = "Device-123",
            deviceModel = "Polar watch 2024",
            handedOutAt = Instant.parse("2024-08-01T09:00:00Z"),
            notes = "Includes charging dock and spare strap"
        ),
        HandedOutDevice.Device(
            deviceId = "Device-456",
            deviceModel = "Garmin chest strap",
            notes = "No spare batteries"
        )
    )
)
```

## FullName

Represents a full name of a participant.

### Data Type Name

`dk.carp.webservices.input.full_name`

### Fields

- `firstName: String?` - The first name of the participant. This field is optional and can be `null`.
- `middleName: String?` - The middle name of the participant. This field is optional and can be `null`.
- `lastName: String?` - The last name of the participant. This field is optional and can be `null`.

### Example

Here is an example of how to create an instance of the `FullName` class:

```kotlin
val fullName = FullName(
    firstName = "John",
    middleName = "A.",
    lastName = "Doe"
)
```

## InformedConsent

Represents an informed consent form signed by a participant.

### Data Type Name

`dk.carp.webservices.input.informed_consent`
### Fields

- `signedTimestamp: Instant?` - The time this informed consent was signed. This field is optional and defaults to the current time.
- `signedLocation: String?` - The location where this informed consent was signed. This field is optional and can be `null`.
- `userId: String?` - The user ID of the participant who signed this consent. This field is optional and can be `null`.
- `name: String` - The name of the participant who signed this consent. This field is required.
- `consent: String?` - The content of the signed consent. This may be plain text or JSON. This field is optional and can be `null`.
- `signatureImage: String?` - The image of the provided signature in PNG format as bytes. This field is optional and can be `null`.

### Example

Here is an example of how to create an instance of the `InformedConsent` class:

```kotlin
val informedConsent = InformedConsent(
    signedTimestamp = Instant.parse("2023-07-01T12:00:00Z"),
    signedLocation = "New York, NY",
    userId = "123456",
    name = "Jane Doe",
    consent = """{"terms":"I agree to participate..."}""",
    signatureImage = "iVBORw0KGgoAAAANSUhEUgAA..."
)
```

## PhoneNumber

Represents a phone number with country code and ISO 3166 code.

### Data Type Name

`dk.carp.webservices.input.phone_number`

### Fields

- `countryCode: String` - The country code of this phone number. The country code is represented by a string, since some country codes contain a hyphen ('-'). For example, "1-246" for Barbados or "44-1481" for Guernsey. See [Country Codes](https://countrycode.org/) or [List of Country Calling Codes](https://en.wikipedia.org/wiki/List_of_country_calling_codes).
- `isoCode: String?` - The ISO 3166 code of the `countryCode`, if available. See [List of ISO 3166 Country Codes](https://en.wikipedia.org/wiki/List_of_ISO_3166_country_codes). This field is optional and can be `null`.
- `number: String` - The phone number. The phone number is represented as a string since it may be pretty-printed with spaces.

### Example

Here is an example of how to create an instance of the `PhoneNumber` class:

```kotlin
val phoneNumber = PhoneNumber(
    countryCode = "1-246",
    isoCode = "BB",
    number = "123 456 7890"
)
```

## SocialSecurityNumber

Represents a social security number (SSN) with associated country information.

### Data Type Name

`dk.carp.webservices.input.ssn`
### Fields

- `socialSecurityNumber: String` - The social security number (SSN).
- `country: String` - The country in which this `socialSecurityNumber` was issued.

### Example

Here is an example of how to create an instance of the `SocialSecurityNumber` class:

```kotlin
val ssn = SocialSecurityNumber(
    socialSecurityNumber = "123-45-6789",
    country = "USA"
)
```

## Sex

An enumeration representing the sex of a participant.

### Data Type Name

`dk.cachet.carp.input.sex`

### Enum Values

- `Male` - Represents a male participant.
- `Female` - Represents a female participant.
- `Intersex` - Represents an intersex participant.

### Example

Here is an example of how to use the `Sex` enum:

```kotlin
val participantSex: Sex = Sex.Male
