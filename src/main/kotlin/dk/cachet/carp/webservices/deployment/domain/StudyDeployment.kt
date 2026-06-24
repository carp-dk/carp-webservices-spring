package dk.cachet.carp.webservices.deployment.domain

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
 * The Data Class [StudyDeployment].
 * The [StudyDeployment] represents a study deployment with the given [id], [snapshot] of the study.
 */
@Entity
@Table(name = "deployments")
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class StudyDeployment(
    /** The deployment [id]. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Int = 0,
    /** The study protocol [snapshot] as a JsonNode */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    @Type(JsonBinaryType::class)
    var snapshot: JsonNode? = null,
) : Auditable()
