package dk.cachet.carp.webservices.account.serdes

import dk.cachet.carp.common.application.users.AccountIdentity
import dk.cachet.carp.common.infrastructure.serialization.JSON
import dk.cachet.carp.webservices.common.configuration.internationalisation.service.MessageBase
import dk.cachet.carp.webservices.common.exception.serialization.SerializationException
import kotlinx.serialization.encodeToString
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import tools.jackson.core.JsonGenerator
import tools.jackson.databind.SerializationContext
import tools.jackson.databind.ValueSerializer

/**
 * The Class [AccountIdentitySerializer].
 * The [AccountIdentitySerializer] implements the serialization mechanism for the [AccountIdentity].
 */
@Suppress("TooGenericExceptionCaught", "SwallowedException")
class AccountIdentitySerializer(private val validationMessages: MessageBase) : ValueSerializer<AccountIdentity>() {
    companion object {
        private val LOGGER: Logger = LogManager.getLogger()
    }

    /**
     * The function [serialize] is used to serialize the [accountIdentity] account identity.
     *
     * @param accountIdentity The [accountIdentity] of the users.
     * @param jsonGenerator The [jsonGenerator] to write serialized account identity.
     * @param serializers The [serializers] for serializing the account identity.
     * @return The serialized account identity.
     */
    override fun serialize(
        accountIdentity: AccountIdentity,
        jsonGenerator: JsonGenerator,
        serializers: SerializationContext,
    ) {
        if (accountIdentity == null) {
            LOGGER.error("The core [AccountIdentity] request is null.")
            throw SerializationException(validationMessages.get("account.identity.request-object-null-value"))
        }

        val serialized: String
        try {
            serialized = JSON.encodeToString(accountIdentity)
        } catch (ex: Exception) {
            LOGGER.error("The core [AccountIdentity] request is not valid. Exception: $ex")
            throw SerializationException(validationMessages.get("account.identity.request-serialization-not-valid"))
        }

        jsonGenerator!!.writeRawValue(serialized)
    }
}
