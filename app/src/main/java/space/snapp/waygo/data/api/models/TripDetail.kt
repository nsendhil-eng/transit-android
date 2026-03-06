package space.snapp.waygo.data.api.models

import java.util.Date

data class TripStop(
    val stopId: String,
    val stopName: String,
    val lat: Double,
    val lon: Double,
    val scheduledTime: String,
    val estimatedTime: String?,
    val estimatedUtc: String?,
    val secondsUntil: Int,
    val isUpcoming: Boolean,
    val isNearest: Boolean
) {
    val departureDate: Date get() = if (estimatedUtc != null) {
        try { java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US).also {
            it.timeZone = java.util.TimeZone.getTimeZone("UTC") }.parse(estimatedUtc) ?: Date()
        } catch (e: Exception) { Date() }
    } else Date()
}

data class TripDetailInfo(
    val tripId: String,
    val routeId: String,
    val routeShortName: String,
    val routeColor: String?,
    val routeType: Int,
    val headsign: String,
    val directionId: Int
)

data class TripVehicle(
    val lat: Double,
    val lon: Double,
    val bearing: Float
)

data class TripDetailResponse(
    val tripInfo: TripDetailInfo,
    val shape: List<List<Double>>,
    val stops: List<TripStop>,
    val vehicle: TripVehicle?
)

data class NextTrip(
    val tripId: String,
    val scheduledTime: String,
    val estimatedTime: String?
)

data class NextTripsResponse(
    val trips: List<NextTrip>
)
