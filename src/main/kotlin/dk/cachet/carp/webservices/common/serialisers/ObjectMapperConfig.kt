package dk.cachet.carp.webservices.common.serialisers

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.datatype.jsr310.ser.InstantSerializer
import com.fasterxml.jackson.module.kotlin.KotlinModule
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
import kotlinx.datetime.toJavaInstant
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary

/**
 * The Configuration Class [ObjectMapperConfig].
 * The [ObjectMapperConfig] implements the [SimpleModule] that allows registration of serializers and deserializers,
 * bean serializer and deserializer modifiers, registration of subtypes and mix-ins as well as some other commonly
 * needed aspects [com.fasterxml.jackson.databind.AbstractTypeResolver].
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

        this.addSerializer(java.time.Instant::class.java, InstantSerializer.INSTANCE)

        this.addSerializer(StudyProtocolSnapshot::class.java, StudyProtocolSnapshotSerializer(validationMessages))
    }

    class KInstantSerializer : JsonSerializer<Instant>() {
        override fun serialize(
            value: Instant,
            gen: JsonGenerator,
            serializers: SerializerProvider,
        ) {
            return(InstantSerializer.INSTANCE.serialize(value.toJavaInstant(), gen, serializers))
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
    fun objectMapper(): ObjectMapper {
        val objectMapper = ObjectMapper()
        objectMapper.registerModule(KotlinModule.Builder().build())

        objectMapper.registerModule(this)

//        objectMapper.registerModule(JavaTimeModule())
        objectMapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)

        return objectMapper
    }
}
