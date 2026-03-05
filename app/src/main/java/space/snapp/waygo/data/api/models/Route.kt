package space.snapp.waygo.data.api.models

import com.google.gson.annotations.SerializedName

data class Route(
    @SerializedName("route_id") val routeId: String,
    @SerializedName("route_short_name") val routeShortName: String,
    @SerializedName("route_long_name") val routeLongName: String,
    @SerializedName("route_color") val routeColor: String?,
    @SerializedName("route_text_color") val routeTextColor: String?,
    @SerializedName("trip_headsign") val tripHeadsign: String?,
    @SerializedName("direction_id") val directionId: Int?,
    @SerializedName("route_type") val routeType: Int?
) {
    val id: String get() = "$routeId-${tripHeadsign ?: ""}-${directionId ?: -1}"
}
