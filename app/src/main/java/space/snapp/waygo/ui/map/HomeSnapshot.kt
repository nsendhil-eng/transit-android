package space.snapp.waygo.ui.map

import com.google.gson.Gson
import space.snapp.waygo.data.api.models.Departure
import space.snapp.waygo.data.api.models.Stop
import java.io.File

/** Predictions move: older than this, the last view could be showing the wrong times */
private const val SNAPSHOT_MAX_AGE_MS = 3 * 60_000L

/**
 * The home panel's last view, kept on disk so the next launch shows rows straight away and
 * swaps in live times as they arrive. Departure times are absolute, so the countdowns stay right.
 */
data class HomeSnapshot(
    val savedAtMs: Long,
    val lat: Double,
    val lon: Double,
    val stops: List<Stop>,
    val departures: List<Departure>
) {
    /** Recent, from here, and "here" is a recent fix: a cached one may be where you used to be */
    fun usableAt(fix: UserFix, nowMs: Long): Boolean =
        nowMs - savedAtMs in 0 until SNAPSHOT_MAX_AGE_MS &&
            nowMs - fix.timeMs < FRESH_FIX_MS &&
            approxMeters(lat, lon, fix.lat, fix.lon) < STOPS_MOVE_M

    fun toJson(): String = gson.toJson(this)

    /** Written to a temporary file first, so a crash mid-write can't leave half a snapshot */
    fun save(file: File) {
        val temp = File(file.path + ".tmp")
        temp.writeText(toJson())
        temp.renameTo(file)
    }

    companion object {
        private val gson = Gson()

        fun fromJson(json: String): HomeSnapshot? = runCatching { gson.fromJson(json, HomeSnapshot::class.java) }.getOrNull()

        fun load(file: File): HomeSnapshot? = runCatching { fromJson(file.readText()) }.getOrNull()
    }
}
