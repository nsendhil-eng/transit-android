package space.snapp.waygo.ui.map

/** Moving this far from where the nearby stops were fetched refreshes them */
const val STOPS_MOVE_M = 100.0
/** A fix vaguer than this still moves the dot, but changes no data */
private const val POOR_ACCURACY_M = 50f
/** Stops refresh at most this often on foot… */
private const val STOPS_MIN_GAP_MS = 30_000L
/** …and this often at vehicle speed, when the nearest stops change every few seconds */
private const val STOPS_MIN_GAP_VEHICLE_MS = 60_000L
/** About 22 km/h: faster than anyone runs for a bus */
private const val VEHICLE_SPEED_MPS = 6f
/** The list holds still while you're using it, until you've gone this far */
private const val HOLD_MAX_M = 400.0
/** A cached fix older than this may not be where you are now */
const val FRESH_FIX_MS = 2 * 60_000L
/** Walk times follow you in steps of this, so a drifting fix doesn't flick them */
const val WALK_ORIGIN_STEP_M = 20.0

/** Moved at least this far (or the fix's own error, if bigger), or this fast, counts as moving */
private const val MOVED_M = 15.0
private const val MOVING_SPEED_MPS = 1f
/** No movement for this long counts as standing still */
private const val STILL_AFTER_MS = 60_000L

/** One GPS reading */
data class UserFix(
    val lat: Double,
    val lon: Double,
    val accuracyM: Float? = null,
    val speedMps: Float? = null,
    /** When it was taken: a cached fix can be old */
    val timeMs: Long
) {
    val accurate: Boolean get() = accuracyM == null || accuracyM <= POOR_ACCURACY_M
}

/**
 * Whether this fix should refresh the nearby stops. Moving your dot needs nothing; only a real
 * move away from [anchorLat]/[anchorLon] (where the stops were fetched) changes the data.
 * [held]: you're using the list, which then stays put unless you've really gone somewhere else.
 */
fun shouldRefetchStops(
    anchorLat: Double?,
    anchorLon: Double?,
    fix: UserFix,
    lastFetchMs: Long,
    nowMs: Long,
    held: Boolean
): Boolean {
    if (anchorLat == null || anchorLon == null) return true
    if (!fix.accurate) return false
    val moved = approxMeters(anchorLat, anchorLon, fix.lat, fix.lon)
    if (moved < maxOf(STOPS_MOVE_M, (fix.accuracyM ?: 0f).toDouble())) return false
    if (held && moved < HOLD_MAX_M) return false
    val gap = if ((fix.speedMps ?: 0f) >= VEHICLE_SPEED_MPS) STOPS_MIN_GAP_VEHICLE_MS else STOPS_MIN_GAP_MS
    return nowMs - lastFetchMs >= gap
}

/** Whether you're on the move, so GPS can ease off when you aren't */
class MovementTracker {
    private var anchor: UserFix? = null
    private var movedAtMs = 0L

    /** Assume moving: called when the app opens */
    fun reset(nowMs: Long) { anchor = null; movedAtMs = nowMs }

    /** Takes a fix; true while moving */
    fun update(fix: UserFix, nowMs: Long): Boolean {
        val from = anchor
        val moved = from == null ||
            (fix.speedMps ?: 0f) >= MOVING_SPEED_MPS ||
            approxMeters(from.lat, from.lon, fix.lat, fix.lon) >= maxOf(MOVED_M, (fix.accuracyM ?: 0f).toDouble())
        if (moved) { anchor = fix; movedAtMs = nowMs }
        return nowMs - movedAtMs < STILL_AFTER_MS
    }
}
