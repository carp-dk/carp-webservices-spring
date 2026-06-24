package dk.cachet.carp.webservices.dataPoint.dto

import com.google.gson.annotations.SerializedName
import jakarta.validation.constraints.NotNull
import org.hibernate.annotations.CreationTimestamp
import org.jetbrains.annotations.Nullable
import tools.jackson.databind.PropertyNamingStrategies
import tools.jackson.databind.annotation.JsonNaming
import java.io.Serializable
import java.time.Instant

/**
 * The Data Class [DataPointHeaderDto].
 * [DataPointHeaderDto] represents a data point headers to a user with the given header values and creation timestamps.
 */
@Deprecated("DataPoint is deprecated. Use DataStream instead.")
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class DataPointHeaderDto(
    /** The [studyId] of the request. */
    @field:NotNull
    @SerializedName("study_id")
    val studyId: String? = null,
    /** The [userId] of the request. */
    @field:NotNull
    @SerializedName("user_id")
    val userId: String? = null,
    /** The [dataFormat] of the request. */
    @field:NotNull
    @SerializedName("data_format")
    val dataFormat: HashMap<*, *>? = null,
    /** The [triggerId] of the request. */
    @field:Nullable
    @SerializedName("trigger_id")
    var triggerId: String? = null,
    /** The [deviceRoleName] of the request. */
    @field:Nullable
    @SerializedName("device_role_name")
    var deviceRoleName: String? = null,
    /** The [uploadTime] of the request. */
    @field:CreationTimestamp
    @SerializedName("upload_time")
    var uploadTime: Instant = Instant.now(),
    /** The [startTime] of the request. */
    @field:NotNull
    @SerializedName("start_time")
    var startTime: Instant = Instant.now(),
    /** The [endTime] of the request. */
    @field:NotNull
    @SerializedName("end_time")
    var endTime: Instant = Instant.now(),
) : Serializable
