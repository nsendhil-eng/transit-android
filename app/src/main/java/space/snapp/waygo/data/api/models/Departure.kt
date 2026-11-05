package space.snapp.waygo.data.api.models

import com.google.gson.annotations.SerializedName
import java.time.Instant
import java.util.Date

data class Departure(
    @SerializedName("trip_id") val tripId: String,
    @SerializedName("stop_id") val stopId: String,
    val stopName: String,
    val vehicleType: String,
    val routeNumber: String,
    val routeId: String = "",
    val headsign: String,
    val directionId: Int,
    val scheduledDepartureUtc: String,
    val expectedDepartureUtc: String?,
    val isDelayed: Boolean = false,
    val secondsUntilDeparture: Int,
    val platformCode: String?,
    val routeColor: String?,
    val routeTextColor: String?,
    val routeLongName: String?,
    // Nullable: older API responses omit these
    val isRealtime: Boolean? = null,
    val delaySeconds: Int? = null,
    val isCancelled: Boolean? = null,
    // This stop is skipped on this trip (e.g. a diversion)
    val isSkipped: Boolean? = null,
    // Which way it leaves the stop, degrees from north; only with with_heading=1, null at a trip's last stop
    val heading: Int? = null,
    // The trip's shape, for /api/shapes
    val shapeId: String? = null,
    // The trip ends at this stop (only with with_heading=1): arriving, not something to board
    val isLastStop: Boolean? = null
) {
    /** Not running from this stop: the trip is cancelled, or skips it. */
    val notRunningHere: Boolean get() = isCancelled == true || isSkipped == true

    val id: String get() = "$tripId-$stopId"

    // Read many times per row and inside sorts, so parsed once. A plain field, not `by lazy`:
    // Gson skips the constructor, which would leave a delegate null. 0 = not parsed yet.
    @Transient private var parsedDepartureMs = 0L

    /** The live time if there is one, else the timetable's; "now" if it can't be read */
    val departureMs: Long
        get() {
            if (parsedDepartureMs == 0L) parsedDepartureMs = parseIso(expectedDepartureUtc ?: scheduledDepartureUtc) ?: return System.currentTimeMillis()
            return parsedDepartureMs
        }

    val departureDate: Date get() = Date(departureMs)

    val liveSeconds: Int
        get() = ((departureMs - System.currentTimeMillis()) / 1000).toInt()

    val countdownText: String
        get() {
            val s = liveSeconds
            if (s < 60) return "Now"
            val m = s / 60
            if (m < 60) return "$m min"
            return "${m / 60}h ${m % 60}m"
        }

    val routeColorInt: Int?
        get() = routeColor?.let { parseHex(it) }

    val routeTextColorInt: Int?
        get() = routeTextColor?.let { parseHex(it) }

    private companion object {
        fun parseIso(s: String): Long? = runCatching { Instant.parse(s).toEpochMilli() }.getOrNull()
        fun parseHex(hex: String): Int? = runCatching { android.graphics.Color.parseColor("#${hex.trimStart('#')}") }.getOrNull()
    }
}
