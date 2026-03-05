package space.snapp.waygo.data.api.models

import com.google.gson.annotations.SerializedName
import java.text.SimpleDateFormat
import java.util.*

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
    val routeLongName: String?
) {
    val id: String get() = "$tripId-$stopId"

    val departureDate: Date
        get() {
            val raw = expectedDepartureUtc ?: scheduledDepartureUtc
            return parseIso(raw) ?: Date()
        }

    val liveSeconds: Int
        get() = ((departureDate.time - System.currentTimeMillis()) / 1000).toInt()

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
        val fmtMs = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
        val fmt   = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'",     Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
        fun parseIso(s: String): Date? = runCatching { fmtMs.parse(s) }.getOrNull() ?: runCatching { fmt.parse(s) }.getOrNull()
        fun parseHex(hex: String): Int? = runCatching { android.graphics.Color.parseColor("#${hex.trimStart('#')}") }.getOrNull()
    }
}
