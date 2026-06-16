package dk.cachet.carp.webservices.deployment.dto

/**
 * DTO to group statistical information for different kinds of dataFormats from [DataPointHeaderDto].
 */
class StatisticsDto(
    /** The total number of data points. */
    var count: Int = 0,
    /** Data structure to hold the number of datapoints uploaded on a particular day.
     *  The key is the timestamp and value is the number of datapoints uploaded on that day.
     */
    val uploads: MutableMap<String, Int> = mutableMapOf(),
)
