package dk.cachet.carp.webservices.protocol.dto

import dk.cachet.carp.protocols.application.StudyProtocolSnapshot
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/**
 * A DTO representing the latest [StudyProtocolSnapshot] with auxiliary information.
 *
 */
@Serializable
data class ProtocolOverview(
    val ownerName: String?,
    val firstVersionCreatedDate: Instant?,
    val lastVersionCreatedDate: Instant?,
    val versionTag: String,
    val snapshot: StudyProtocolSnapshot,
)
