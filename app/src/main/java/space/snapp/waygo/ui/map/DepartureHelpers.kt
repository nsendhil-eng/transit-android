package space.snapp.waygo.ui.map

import space.snapp.waygo.data.api.models.Departure
import space.snapp.waygo.data.api.models.VehicleType
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.cos
import kotlin.math.hypot

/** A service drops off the panel this long after its live time (it has left) */
const val DEPARTED_GRACE_S = 30

/**
 * The stops to draw when zoomed out: [keep] first (the selected stop, the panel's top rows),
 * then the rest nearest-first, leaving out any stop within [metersApart] of one already drawn.
 * In the city that stops a dozen dots piling up around a station; zoomed in, the caller shows all.
 */
fun declutterStops(
    groups: List<StopGroup>,
    keep: Set<String>,
    userLat: Double?,
    userLon: Double?,
    metersApart: Double
): List<StopGroup> {
    fun distance(g: StopGroup) =
        if (userLat != null && userLon != null) approxMeters(userLat, userLon, g.latitude, g.longitude)
        else g.stops.firstOrNull()?.distanceMeters ?: 0.0
    val kept = groups.filter { it.id in keep }.toMutableList()
    for (g in groups.filter { it.id !in keep }.sortedBy(::distance)) {
        if (kept.none { approxMeters(it.latitude, it.longitude, g.latitude, g.longitude) < metersApart }) kept += g
    }
    return kept
}

/**
 * Worth showing as something to catch: running from this stop, not yet gone, and not ending
 * here (a train terminating at Roma Street can't be boarded there)
 */
val Departure.boardable: Boolean
    get() = !notRunningHere && liveSeconds > -DEPARTED_GRACE_S && isLastStop != true

val Departure.mode: VehicleType
    get() = when (vehicleType) {
        VehicleType.Rail.label -> VehicleType.Rail
        VehicleType.Ferry.label -> VehicleType.Ferry
        VehicleType.Tram.label -> VehicleType.Tram
        else -> VehicleType.Bus
    }

/**
 * Label for the route pill: the route number, except rail, whose short names are internal
 * codes ("IPCA"), so where the train goes: "Springfield Central"
 */
val Departure.pillLabel: String
    get() = if (mode == VehicleType.Rail) headsign.removeSuffix(" station").removeSuffix(" Station") else routeNumber

/** Late enough to colour amber */
const val LATE_S = 120

val Departure.isLate: Boolean get() = isRealtime == true && (delaySeconds ?: 0) > LATE_S

/**
 * "Now", "6 min", or past an hour a clock time: "4:15 PM", "Tomorrow 6:05 AM". The live time
 * already includes any delay, so lateness is shown by colour, not a "+2".
 */
fun departureTimeLabel(d: Departure, nowMs: Long = System.currentTimeMillis()): String {
    val s = d.liveSeconds
    return when {
        s < 60 -> "Now"
        s < 3600 -> "${s / 60} min"
        else -> {
            val at = Calendar.getInstance().apply { timeInMillis = d.departureDate.time }
            val today = Calendar.getInstance().apply { timeInMillis = nowMs }
            val prefix = if (at.get(Calendar.DAY_OF_YEAR) != today.get(Calendar.DAY_OF_YEAR)) "Tomorrow " else ""
            prefix + SimpleDateFormat("h:mm a", Locale.getDefault()).format(d.departureDate)
        }
    }
}

/** Equirectangular distance — plenty at walking scale */
fun approxMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val mPerDegLat = 111_320.0
    val mPerDegLon = 111_320.0 * cos(Math.toRadians((lat1 + lat2) / 2))
    return hypot((lat2 - lat1) * mPerDegLat, (lon2 - lon1) * mPerDegLon)
}

/** Roughly the area Translink serves: Sunshine Coast to the Gold Coast, Ipswich to the bay */
fun inSeq(lat: Double, lon: Double): Boolean = lat in -28.5..-25.9 && lon in 151.8..153.7
