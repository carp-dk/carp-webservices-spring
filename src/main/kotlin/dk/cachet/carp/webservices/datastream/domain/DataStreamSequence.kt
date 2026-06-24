package dk.cachet.carp.webservices.datastream.domain

import dk.cachet.carp.webservices.common.audit.Auditable
import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import tools.jackson.databind.JsonNode
import tools.jackson.databind.PropertyNamingStrategies
import tools.jackson.databind.annotation.JsonNaming
import java.io.Serializable

@Entity(name = "data_stream_sequence")
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class DataStreamSequence(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Int = 0,
    val dataStreamId: Int? = 0,
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    var snapshot: JsonNode? = null,
    var firstSequenceId: Long? = 0,
    var lastSequenceId: Long? = 0,
) : Auditable(), Serializable
