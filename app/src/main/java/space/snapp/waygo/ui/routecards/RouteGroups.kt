package space.snapp.waygo.ui.routecards

import space.snapp.waygo.data.api.models.Departure
import space.snapp.waygo.data.api.models.Stop
import space.snapp.waygo.data.api.models.VehicleType

private const val WALK_METRES_PER_MIN = 80.0

/** One route heading one way, at the nearest stop (or station) that serves it. */
data class DirectionCard(
    val key: String,
    val routeNumber: String,
    val headsign: String,
    val vehicleType: VehicleType,
    val routeColor: String?,
    val routeTextColor: String?,
    val stopName: String,
    val platformCode: String?,
    val distanceMeters: Double?,
    /** e.g. "Rail Replacement Bus" — temporary services that stand in for a closed line */
    val routeLongName: String?,
    /** Next departures from that stop, soonest first (max 3). */
    val departures: List<Departure>
) {
    val isReplacement: Boolean get() = routeLongName?.contains("replacement", ignoreCase = true) == true

    val walkSeconds: Int? get() = distanceMeters?.let { (it / WALK_METRES_PER_MIN * 60).toInt() }

    /** Seconds until the first departure you could still reach on foot — used for ordering. */
    fun catchableIn(now: Long): Long {
        val walkMs = (walkSeconds ?: 0) * 1000L
        val first = departures.firstOrNull { it.departureDate.time - now >= walkMs }
            ?: return Long.MAX_VALUE / 2
        return (first.departureDate.time - now) / 1000
    }
}

/** A route with its directions as swipeable pages (trains: one destination per group). */
data class RouteGroup(val key: String, val directions: List<DirectionCard>)

/** Modes that actually have departures, in a stable order. */
fun availableModes(departures: List<Departure>): List<VehicleType> =
    departures.map { it.mode }.distinct().sortedBy { it.ordinal }

/**
 * Collapse a flat departure list into route cards: one per route + direction at its nearest
 * stop, bus directions paired as pages, sorted by how soon you could catch one on foot.
 */
fun buildRouteGroups(
    departures: List<Departure>,
    stops: List<Stop>,
    modeFilter: VehicleType?,
    now: Long
): List<RouteGroup> {
    val stopById = stops.associateBy { it.id }
    val upcoming = departures.filter {
        it.departureDate.time - now > -60_000 && (modeFilter == null || it.mode == modeFilter)
    }

    // All platforms of a station are one place: a train to Ipswich may use platform 1 or 3,
    // and picking the platform that's a metre closer would hide trains on the others
    fun placeKey(stopId: String): String =
        stopById[stopId]?.parentStation?.takeIf { it.isNotEmpty() } ?: stopId
    val placeDistance = stops
        .groupBy { placeKey(it.id) }
        .mapValues { (_, s) -> s.mapNotNull { it.distanceMeters }.minOrNull() }

    val cards = upcoming
        .groupBy { it.directionKey() }
        .mapNotNull { (key, deps) ->
            val nearestPlace = deps.map { placeKey(it.stopId) }.distinct()
                .minByOrNull { placeDistance[it] ?: Double.MAX_VALUE }
                ?: return@mapNotNull null
            val atPlace = deps.filter { placeKey(it.stopId) == nearestPlace }
                .sortedBy { it.departureDate }
                .take(3)
            val first = atPlace.firstOrNull() ?: return@mapNotNull null
            DirectionCard(
                key = key,
                routeNumber = first.routeNumber,
                headsign = first.headsign,
                vehicleType = first.mode,
                routeColor = first.routeColor,
                routeTextColor = first.routeTextColor,
                stopName = first.stopName,
                // Platform of the next service — later ones may leave from a different platform
                platformCode = first.platformCode?.takeIf { it.isNotBlank() },
                distanceMeters = placeDistance[nearestPlace],
                routeLongName = first.routeLongName,
                departures = atPlace
            )
        }

    return cards
        .groupBy { card ->
            // Bus/ferry/tram: pair both directions of a route; rail directions are separate routes
            if (card.vehicleType == VehicleType.Rail) card.key else card.key.substringBeforeLast("|")
        }
        .map { (key, dirs) -> RouteGroup(key, dirs.sortedBy { it.catchableIn(now) }) }
        .sortedBy { it.directions.first().catchableIn(now) }
}

val Departure.mode: VehicleType
    get() = when (vehicleType) {
        "Bus" -> VehicleType.Bus
        "Ferry" -> VehicleType.Ferry
        else -> VehicleType.Rail
    }

/**
 * Grouping key per direction. Rail uses the destination so lines sharing it merge
 * (e.g. NAIP and CAIP both "Ipswich station"); others use route + direction.
 */
private fun Departure.directionKey(): String =
    if (mode == VehicleType.Rail) "rail|${headsign}" else "$routeId|$directionId"
