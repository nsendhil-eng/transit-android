package space.snapp.waygo.data.api.models

/** /api/trip-vehicles: the live vehicle on a trip. Heading is degrees from north, from the trip's shape. */
data class LiveTripVehicle(
    val tripId: String,
    val lat: Double,
    val lon: Double,
    val heading: Int,
    val headingKnown: Boolean,
    val ageSeconds: Int?,
    val isStale: Boolean
)

data class TripVehiclesResponse(val vehicles: List<LiveTripVehicle> = emptyList())
