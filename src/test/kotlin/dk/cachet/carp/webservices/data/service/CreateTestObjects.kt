/**
 * Utility functions to create stubs for testing (based on the CARP core implementations)
 */
package dk.cachet.carp.webservices.data.service

import dk.cachet.carp.common.application.UUID
import dk.cachet.carp.common.application.data.Data
import dk.cachet.carp.common.application.toEpochMicroseconds
import dk.cachet.carp.data.application.*
import dk.cachet.carp.data.infrastructure.dataStreamId
import dk.cachet.carp.data.infrastructure.measurement
import kotlinx.datetime.Clock

val now = Clock.System.now()
val stubSyncPoint = SyncPoint(now, now.toEpochMicroseconds())
val stubTriggerIds = listOf(1)
const val stubSequenceDeviceRoleName = "Device"

inline fun <reified T : Data> createStubSequence(
    studyDeploymentId: UUID,
    firstSequenceId: Long,
    vararg data: T,
): DataStreamSequence<T> =
    createStubSequence(
        dataStreamId<T>(studyDeploymentId, stubSequenceDeviceRoleName),
        firstSequenceId,
        *data.map { measurement(it, 0) }.toTypedArray(),
    )

inline fun <reified T : Data> createStubSequence(
    dataStreamId: DataStreamId,
    firstSequenceId: Long,
    vararg measurements: Measurement<T>,
): DataStreamSequence<T> =
    MutableDataStreamSequence<T>(
        dataStreamId,
        firstSequenceId,
        stubTriggerIds,
        stubSyncPoint,
    ).apply { appendMeasurements(measurements.toList()) }
