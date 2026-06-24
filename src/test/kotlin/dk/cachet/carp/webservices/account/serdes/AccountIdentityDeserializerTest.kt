package dk.cachet.carp.webservices.account.serdes

import dk.cachet.carp.common.application.users.AccountIdentity
import dk.cachet.carp.webservices.common.configuration.internationalisation.service.MessageBase
import dk.cachet.carp.webservices.common.exception.serialization.SerializationException
import io.mockk.*
import org.junit.jupiter.api.Nested
import tools.jackson.databind.json.JsonMapper
import tools.jackson.databind.module.SimpleModule
import kotlin.test.*

class AccountIdentityDeserializerTest {
    private val validationMessage: MessageBase = mockk<MessageBase>()
    private val mapper =
        JsonMapper.builder()
            .addModule(
                SimpleModule().addDeserializer(
                    AccountIdentity::class.java,
                    AccountIdentityDeserializer(validationMessage),
                ),
            )
            .build()

    @Nested
    inner class Deserialize {
        @Test
        fun `should deserialize valid JSON`() {
            val validJsonString =
                "{\"__type\":\"dk.cachet.carp.common.application.users.EmailAccountIdentity\"," +
                    "\"emailAddress\":\"test@dtu.dk\"}"
            val result = mapper.readValue(validJsonString, AccountIdentity::class.java)

            val expectedAccountIdentity = AccountIdentity.fromEmailAddress("test@dtu.dk")
            assertEquals(expectedAccountIdentity, result)
        }

        @Test
        fun `should throw SerializationException if json string is invalid`() {
            every { validationMessage.get(any()) } returns "err"

            assertFailsWith<Exception> {
                mapper.readValue("not valid json", AccountIdentity::class.java)
            }
        }

        @Test
        fun `should throw SerializationException if failed to `() {
            val invalidJsonString =
                "{\"__type\":\"dk.cachet!.carp.common.application.users.EmailAccountIdentity\"," +
                    "\"emailAddress\":\"test@dtu.dk\"}"

            every { validationMessage.get(any()) } returns "err"

            assertFailsWith<Exception> {
                mapper.readValue(invalidJsonString, AccountIdentity::class.java)
            }

            verify(exactly = 1) { validationMessage.get("account.identity.request-deserialization-not-valid") }
        }
    }
}
