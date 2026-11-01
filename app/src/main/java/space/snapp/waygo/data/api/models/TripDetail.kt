package space.snapp.waygo.data.api.models

import java.util.Date

/** Matches the API's stale threshold, for responses that predate isStale. */
const val STALE_AFTER_S = 180

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
    val isNearest: Boolean,
    // Nullable: older API responses omit these
    val stopSequence: Int? = null,
    val isRealtime: Boolean? = null,
    val delaySeconds: Int? = null
) {
    /** True when the time comes from GTFS-RT rather than the timetable. */
    val hasLiveTime: Boolean get() = isRealtime ?: (estimatedTime != null)

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
    val directionId: Int,
    val routeTextColor: String? = null
)

data class TripVehicle(
    val lat: Double,
    val lon: Double,
    val bearing: Float,
    val ageSeconds: Int? = null,
    val currentStopSequence: Int? = null,
    // GTFS-RT VehicleStopStatus: 0 INCOMING_AT, 1 STOPPED_AT, 2 IN_TRANSIT_TO
    val currentStatus: Int? = null,
    // False when the API couldn't work out a direction (vehicle off its route line)
    val headingKnown: Boolean? = null,
    // GPS fix older than ~3 min: not where the bus is now (e.g. parked before the trip starts)
    val isStale: Boolean? = null
) {
    val isStoppedAt: Boolean get() = currentStatus == 1

    /** Fresh enough to place on the timeline and count stops from. */
    val isLive: Boolean get() = isStale != true && (ageSeconds ?: 0) <= STALE_AFTER_S

    /** Direction of travel, or null if unknown — older API responses never had one. */
    val heading: Float? get() = bearing.takeIf { headingKnown == true }
}

data class TripDetailResponse(
    val tripInfo: TripDetailInfo,
    val shape: List<List<Double>>,
    val stops: List<TripStop>,
    val vehicle: TripVehicle?
)

data class NextTrip(
    val tripId: String,
    val scheduledTime: String,
    val estimatedTime: String?,
    val departureUtc: String? = null,
    val isRealtime: Boolean? = null,
    val delaySeconds: Int? = null
) {
    val departureDate: Date? get() = departureUtc?.let { parseUtc(it) }
}

private fun parseUtc(iso: String): Date? = runCatching {
    java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US)
        .also { it.timeZone = java.util.TimeZone.getTimeZone("UTC") }
        .parse(iso)
}.getOrNull()

data class NextTripsResponse(
    val trips: List<NextTrip>
)
