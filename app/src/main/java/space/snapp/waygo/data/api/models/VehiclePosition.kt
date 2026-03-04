package space.snapp.waygo.data.api.models

data class VehiclePosition(
    val vehicleId: String,
    val lat: Double,
    val lon: Double,
    val bearing: Float,
    val routeShortName: String,
    val routeColor: String?,
    val routeType: Int      // 0=tram, 1/2=rail, 3=bus, 4=ferry
)
