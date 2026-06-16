package dk.cachet.carp.webservices.dataPoint.domain

import java.time.Instant

/**
 * The Data Class [DataPointStatistics].
 * The [DataPointStatistics] contains various statistical information about the data points connected to one deployment.
 */
@Deprecated("DataPoint is deprecated. Use DataStream instead.")
data class DataPointStatistics(
    /** The ID of the deployment the data points are connected to. */
    val deploymentId: String,
    /** The time of the last data upload. */
    val lastDataUpload: Instant,
    /** The number of data points uploaded so far. */
    val numberOfDataPoints: Long,
)
