package dk.cachet.carp.webservices.common.serialisers

import dk.cachet.carp.common.application.UUID
import dk.cachet.carp.common.application.users.AccountIdentity
import dk.cachet.carp.data.application.DataStreamBatch
import dk.cachet.carp.data.application.Measurement
import dk.cachet.carp.data.application.SyncPoint
import dk.cachet.carp.protocols.application.StudyProtocolSnapshot
import dk.cachet.carp.webservices.account.serdes.AccountIdentityDeserializer
import dk.cachet.carp.webservices.account.serdes.AccountIdentitySerializer
import dk.cachet.carp.webservices.account.serdes.StudyProtocolSnapshotSerializer
import dk.cachet.carp.webservices.common.configuration.internationalisation.service.MessageBase
import dk.cachet.carp.webservices.common.serialisers.serdes.UUIDDeserializer
import dk.cachet.carp.webservices.common.serialisers.serdes.UUIDSerializer
import dk.cachet.carp.webservices.datastream.serdes.*
import kotlinx.datetime.Instant
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import tools.jackson.core.JsonGenerator
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.SerializationContext
import tools.jackson.databind.ValueSerializer
import tools.jackson.databind.json.JsonMapper
import tools.jackson.databind.module.SimpleModule
import tools.jackson.module.kotlin.KotlinModule

/**
 * The Configuration Class [ObjectMapperConfig].
 * The [ObjectMapperConfig] implements the [SimpleModule] that allows registration of serializers and deserializers,
 * bean serializer and deserializer modifiers, registration of subtypes and mix-ins as well as some other commonly
 * needed aspects [tools.jackson.databind.AbstractTypeResolver].
 */
@Configuration
class ObjectMapperConfig(validationMessages: MessageBase) : SimpleModule() {
    init
    {
        // AccountIdentity
        this.addSerializer(AccountIdentity::class.java, AccountIdentitySerializer(validationMessages))
        this.addDeserializer(AccountIdentity::class.java, AccountIdentityDeserializer(validationMessages))
        // UUID
        this.addSerializer(UUID::class.java, UUIDSerializer(validationMessages))
        this.addDeserializer(UUID::class.java, UUIDDeserializer(validationMessages))

        // SyncPoint
        this.addSerializer(SyncPoint::class.java, SyncPointSerializer(validationMessages))
        this.addDeserializer(SyncPoint::class.java, SyncPointDeserializer(validationMessages))
        // Measurement
        this.addSerializer(Measurement::class.java, MeasurementSerializer(validationMessages))
        this.addDeserializer(Measurement::class.java, MeasurementDeserializer(validationMessages))

        // DataStreamBatch
        this.addSerializer(DataStreamBatch::class.java, DataStreamBatchSerializer(validationMessages))
        this.addDeserializer(DataStreamBatch::class.java, DataStreamBatchDeserializer(validationMessages))

        this.addSerializer(Instant::class.java, KInstantSerializer.INSTANCE)

        this.addSerializer(StudyProtocolSnapshot::class.java, StudyProtocolSnapshotSerializer(validationMessages))
    }

    class KInstantSerializer : ValueSerializer<Instant>() {
        override fun serialize(
            value: Instant,
            gen: JsonGenerator,
            serializers: SerializationContext,
        ) {
            gen.writeString(value.toString())
        }

        companion object {
            val INSTANCE = KInstantSerializer()
        }
    }

    /**
     * The function [objectMapper] provides functionality for reading and writing JSON, either to and from
     * basic POJOs (Plain Old Java Objects), or to and from a general-purpose JSON Tree Model.
     *
     * @return The [ObjectMapper] to register the object serialization.
     */
    @Bean
    @Primary
    fun objectMapper(): ObjectMapper =
        JsonMapper.builder()
            .addModule(KotlinModule.Builder().build())
            .addModule(this)
            .build()
}
