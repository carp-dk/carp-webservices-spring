package dk.cachet.carp.webservices.export.domain

import dk.cachet.carp.webservices.common.audit.Auditable
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import tools.jackson.databind.PropertyNamingStrategies
import tools.jackson.databind.annotation.JsonNaming

/**
 * The Data Class [Export].
 * The [Export] is a database entry which indicates an exported resource (e.g. a study data export).
 */
@Entity(name = "exports")
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class Export(
    @Id
    var id: String = "",
    var fileName: String = "",
    @Enumerated(EnumType.STRING)
    var status: ExportStatus = ExportStatus.UNKNOWN,
    var studyId: String = "",
    @Enumerated(EnumType.STRING)
    var type: ExportType = ExportType.UNKNOWN,
    var relativePath: String = "",
) : Auditable()

/** Persisted by name in `exports.status`: renaming a constant requires a data migration. */
enum class ExportStatus {
    UNKNOWN,
    IN_PROGRESS,
    AVAILABLE,
    ERROR,
    EXPIRED,
}

/** Persisted by name in `exports.type`: renaming a constant requires a data migration. */
enum class ExportType {
    UNKNOWN,
    STUDY_DATA,
    DEPLOYMENT_DATA,
    ANONYMOUS_PARTICIPANTS,
}
