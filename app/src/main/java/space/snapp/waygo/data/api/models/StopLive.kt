package space.snapp.waygo.data.api.models

data class TripLiveVehicle(
    val lat: Double,
    val lon: Double,
    val bearing: Float
)

data class TripLive(
    val tripId: String,
    val routeShortName: String,
    val routeColor: String?,
    val shapeBefore: List<List<Double>>,
    val shapeAfter: List<List<Double>>,
    val vehicle: TripLiveVehicle?,
    val hasPassed: Boolean
)

data class StopLiveResponse(
    val trips: List<TripLive>
)
