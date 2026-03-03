package space.snapp.waygo.data.api.models

import com.google.gson.annotations.SerializedName

data class Stop(
    val id: String,
    val name: String,
    @SerializedName("stop_code") val stopCode: String?,
    val latitude: Double,
    val longitude: Double,
    @SerializedName("parent_station") val parentStation: String?,
    @SerializedName("parent_station_name") val parentStationName: String?,
    @SerializedName("servicing_routes") val servicingRoutes: String?,
    @SerializedName("route_types") val routeTypes: List<Int>?,
    @SerializedName("distance_m") val distanceMeters: Double?
) {
    val primaryVehicleType: VehicleType
        get() = routeTypes?.firstNotNullOfOrNull { VehicleType.fromGtfsType(it) } ?: VehicleType.Bus

    val distanceLabel: String?
        get() = distanceMeters?.let {
            if (it < 1000) "${it.toInt()}m" else "${"%.1f".format(it / 1000)}km"
        }
}

enum class VehicleType(val label: String, val icon: String) {
    Tram("Tram", "tram"),
    Rail("Train", "train"),
    Bus("Bus", "bus"),
    Ferry("Ferry", "ferry");

    companion object {
        fun fromGtfsType(type: Int): VehicleType? = when (type) {
            0 -> Tram
            1, 2 -> Rail
            3 -> Bus
            4 -> Ferry
            else -> null
        }
    }
}
