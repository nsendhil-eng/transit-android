package space.snapp.waygo.ui.map

import space.snapp.waygo.data.api.models.Departure
import space.snapp.waygo.data.api.models.Stop
import space.snapp.waygo.data.api.models.VehicleType
import space.snapp.waygo.ui.tripdetail.mapStopName
import kotlin.math.ceil

/** Departures shown per route row */
const val ROUTE_ROW_TIMES = 2

/** Walking pace, and how much longer a real walk is than the straight line (corners, crossings) */
private const val WALK_M_PER_MIN = 80.0
private const val DETOUR = 1.3
/** Bus stations (underground busways): platforms are a stair or lift away from the street */
private const val BUS_STATION_MIN_WALK = 3

/** Distances within this band count as equal, so a GPS wobble doesn't swap two rows */
private const val ORDER_BAND_M = 25

/**
 * One row of the home panel: a route heading one way, at the stop nearest you that serves it
 * (whichever side of the road), with its next departures there.
 */
data class NearbyRoute(
    /** "routeId|directionId" */
    val key: String,
    val mode: VehicleType,
    val group: StopGroup,
    val stop: Stop?,
    /** From where the stops were fetched, so it only changes when the list is refetched */
    val distanceM: Double,
    val departures: List<Departure>
) {
    val first: Departure get() = departures.first()

    /**
     * A platform in a bus station or busway (King George Square, Roma Street, Cultural Centre):
     * reached via its entrances. Lettered platforms give it away even when the station's name
     * doesn't ("Cultural Centre station"); a train station's street bus bays have none.
     */
    val isBusStation: Boolean
        get() = mode == VehicleType.Bus && stop?.parentStation?.isNotBlank() == true &&
            (!first.platformCode.isNullOrBlank() ||
                stop.parentStationName?.let { it.contains("bus station", true) || it.contains("busway", true) } == true)

    val walkMinutes: Int
        get() {
            val m = ceil(distanceM * DETOUR / WALK_M_PER_MIN).toInt().coerceAtLeast(1)
            return if (isBusStation) maxOf(m, BUS_STATION_MIN_WALK) else m
        }

    /**
     * Where to catch it: "Wesley Hospital · stop 9", "Auchenflower station · platform 2",
     * "King George Square bus station · platform 1a". The stop number tells the two sides of a
     * road apart.
     */
    val stopLabel: String
        get() {
            val platform = first.platformCode?.takeIf { it.isNotBlank() }
            return when {
                isBusStation || mode == VehicleType.Rail ->
                    (stop?.parentStationName ?: group.name) + (platform?.let { " · platform $it" } ?: "")
                else -> {
                    val name = stop?.name ?: group.name
                    mapStopName(name) + (stopNumber(name)?.let { " · stop $it" } ?: "")
                }
            }
        }
}

/** "Coronation Dr at Wesley Hospital, stop 9" -> "9"; "Wickham Terrace Stop 132 near Albert St" -> "132" */
fun stopNumber(name: String): String? =
    Regex("""(?:,\s*stop\s+|\bStop\s+)(\w+)""", RegexOption.IGNORE_CASE).find(name)?.groupValues?.get(1)

private val MODE_ORDER = listOf(VehicleType.Rail, VehicleType.Bus, VehicleType.Ferry, VehicleType.Tram)

/**
 * The home panel's rows: one per route + direction that can be caught nearby, at its stop
 * nearest you (the API's per_route mode picks that stop), next departures soonest first.
 * Ordered by walk distance, then mode and route number: a stable order, so rows don't
 * reshuffle every time a bus comes and goes. Services ending at their stop are left out.
 */
fun buildNearbyRoutes(groups: List<StopGroup>, departures: List<Departure>): List<NearbyRoute> {
    val groupOfStop = groups.flatMap { g -> g.stops.map { it.id to g } }.toMap()
    val stopById = groups.flatMap { it.stops }.associateBy { it.id }
    return departures
        .filter { it.boardable && groupOfStop[it.stopId] != null }
        .groupBy { "${it.routeId}|${it.directionId}" }
        .mapNotNull { (key, deps) ->
            // per_route already sends one stop per route; if not, use the nearest of them
            val nearestStop = deps.map { it.stopId }.distinct()
                .minByOrNull { stopById[it]?.distanceMeters ?: Double.MAX_VALUE } ?: return@mapNotNull null
            val atStop = deps.filter { it.stopId == nearestStop }.sortedBy { it.liveSeconds }.take(ROUTE_ROW_TIMES)
            val first = atStop.firstOrNull() ?: return@mapNotNull null
            NearbyRoute(key, first.mode, groupOfStop.getValue(nearestStop), stopById[nearestStop],
                stopById[nearestStop]?.distanceMeters ?: 0.0, atStop)
        }
        .sortedWith(compareBy<NearbyRoute>(
            { (it.distanceM / ORDER_BAND_M).toInt() },
            { MODE_ORDER.indexOf(it.mode) },
            { it.first.routeNumber.toIntOrNull() ?: Int.MAX_VALUE },
            { it.first.routeNumber },
            { it.first.headsign }
        ))
}

/** A route this much further away than the nearest of its mode still counts as "right here" */
private const val PEEK_EXTRA_M = 160.0

/**
 * The panel's resting rows, one per mode: of the routes about as close as the nearest one
 * (within PEEK_EXTRA_M, roughly a 2 minute walk more), the one leaving soonest. At a bus
 * interchange the nearest platform's route might not leave for 40 minutes while another goes in
 * one. Modes in order of their nearest route. The full list keeps its stable distance order.
 */
fun peekRoutes(routes: List<NearbyRoute>): List<NearbyRoute> =
    routes.groupBy { it.mode }.values
        .sortedBy { list -> list.minOf { it.distanceM } }
        .map { list ->
            val nearest = list.minOf { it.distanceM }
            list.filter { it.distanceM <= nearest + PEEK_EXTRA_M }.minBy { it.first.liveSeconds }
        }
