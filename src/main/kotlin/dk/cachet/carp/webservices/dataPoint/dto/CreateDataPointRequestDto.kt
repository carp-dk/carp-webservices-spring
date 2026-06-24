package dk.cachet.carp.webservices.dataPoint.dto

import com.google.gson.annotations.SerializedName
import jakarta.validation.Valid
import jakarta.validation.constraints.NotNull
import org.jetbrains.annotations.Nullable
import tools.jackson.databind.PropertyNamingStrategies
import tools.jackson.databind.annotation.JsonNaming

@Deprecated("DataPoint is deprecated. Use DataStream instead.")
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class CreateDataPointRequestDto(
    @field:Valid
    @field:NotNull
    @SerializedName("carp_header")
    var carpHeader: DataPointHeaderDto? = null,
    @field:NotNull
    @SerializedName("carp_body")
    var carpBody: HashMap<*, *>? = null,
    @field:Nullable
    @SerializedName("storage_name")
    var storageName: String? = null,
)
