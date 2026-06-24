package dk.cachet.carp.webservices.protocol.domain

import dk.cachet.carp.webservices.common.audit.Auditable
import io.hypersistence.utils.hibernate.type.json.JsonBinaryType
import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.annotations.Type
import org.hibernate.type.SqlTypes
import tools.jackson.databind.JsonNode
import tools.jackson.databind.PropertyNamingStrategies
import tools.jackson.databind.annotation.JsonNaming

/**
 * The Class [Protocol].
 * The [Protocol] represents the domain protocol with the given [id], [versionTag], and [snapshot].
 */
@Entity
@Table(name = "protocols")
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class Protocol(
    /**
     * The protocol [id].
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Int = 0,
    /**
     * The protocol [versionTag].
     */
    @Column(name = "version_tag")
    var versionTag: String = "",
    /**
     * The core [StudyProtocolSnapshot] class as a [JsonNode].
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    @Type(JsonBinaryType::class)
    var snapshot: JsonNode? = null,
) : Auditable()
