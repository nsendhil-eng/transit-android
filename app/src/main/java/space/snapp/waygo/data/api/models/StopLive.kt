package space.snapp.waygo.data.api.models

data class TripLiveVehicle(
    val lat: Double,
    val lon: Double,
    val bearing: Float,
    // False when the API couldn't work out a direction (vehicle off its route line)
    val headingKnown: Boolean? = null,
    val ageSeconds: Int? = null,
    val isStale: Boolean? = null
) {
    val isLive: Boolean get() = isStale != true && (ageSeconds ?: 0) <= STALE_AFTER_S

    /** Direction of travel, or null if unknown — older API responses never had one. */
    val heading: Float? get() = bearing.takeIf { headingKnown == true }
}

data class TripLive(
    val tripId: String,
    val routeShortName: String,
    val routeColor: String?,
    val shapeBefore: List<List<Double>>,
    val shapeAfter: List<List<Double>>,
    val vehicle: TripLiveVehicle?,
    val hasPassed: Boolean,
    // Nullable: older API responses omit these
    val routeTextColor: String? = null,
    val routeType: Int? = null
)

data class StopLiveResponse(
    val trips: List<TripLive>
)
