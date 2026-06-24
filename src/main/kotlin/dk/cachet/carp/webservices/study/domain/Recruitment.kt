package dk.cachet.carp.webservices.study.domain

import dk.cachet.carp.webservices.common.audit.Auditable
import io.hypersistence.utils.hibernate.type.json.JsonBinaryType
import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.annotations.Type
import org.hibernate.type.SqlTypes
import tools.jackson.databind.PropertyNamingStrategies
import tools.jackson.databind.annotation.JsonNaming

/**
 * The Class [Recruitment].
 * The [Recruitment] represents the core model Recruitment as a persistable entity.
 */
@Entity(name = "recruitments")
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class Recruitment(
    /** The webservices-only [id]. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Int = 0,
    /** The core Recruitment class ([snapshot]) as a JsonNode. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    @Type(JsonBinaryType::class)
    var snapshot: String? = null,
) : Auditable()
