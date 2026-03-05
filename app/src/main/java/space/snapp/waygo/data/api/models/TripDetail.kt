package space.snapp.waygo.data.api.models

data class TripStop(
    val stopId: String,
    val stopName: String,
    val lat: Double,
    val lon: Double,
    val scheduledTime: String,
    val estimatedTime: String?,
    val isUpcoming: Boolean,
    val isNearest: Boolean
)

data class TripDetailInfo(
    val tripId: String,
    val routeId: String,
    val routeShortName: String,
    val routeColor: String?,
    val headsign: String,
    val directionId: Int
)

data class TripDetailResponse(
    val tripInfo: TripDetailInfo,
    val shape: List<List<Double>>,
    val stops: List<TripStop>
)

data class NextTrip(
    val tripId: String,
    val scheduledTime: String,
    val estimatedTime: String?
)

data class NextTripsResponse(
    val trips: List<NextTrip>
)
