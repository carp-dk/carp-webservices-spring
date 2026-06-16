package dk.cachet.carp.webservices.dataPoint.domain

import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming
import dk.cachet.carp.webservices.common.audit.Auditable
import dk.cachet.carp.webservices.dataPoint.dto.DataPointHeaderDto
import io.hypersistence.utils.hibernate.type.json.JsonBinaryType
import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.annotations.Type
import org.hibernate.type.SqlTypes
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.io.Serializable
import java.util.*

@Deprecated("DataPoint is deprecated. Use DataStream instead.")
@Entity(name = "data_points")
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
@EntityListeners(AuditingEntityListener::class)
data class DataPoint(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Int? = null,
    var deploymentId: String? = null,
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    @Type(JsonBinaryType::class)
    var carpHeader: DataPointHeaderDto? = null,
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    @Type(JsonBinaryType::class)
    var carpBody: HashMap<*, *>? = null,
    var storageName: String? = null,
) : Serializable, Auditable()
