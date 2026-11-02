package space.snapp.waygo.ui.map

import space.snapp.waygo.data.api.models.Departure
import space.snapp.waygo.data.api.models.VehicleType
import space.snapp.waygo.ui.tripdetail.mapStopName
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.cos
import kotlin.math.hypot

/** One stop's share of a mode card: one side of the road, or the only stop */
data class CardSide(
    val group: StopGroup,
    val distanceM: Double,
    val departures: List<Departure>
) {
    /** Which way services leave this side, from its first departure that knows */
    val heading: Int? get() = departures.firstNotNullOfOrNull { it.heading }
}

/**
 * One card along the bottom of the home map: the nearest stop of one mode and its next
 * departures. Same-named stops close together (both sides of a road) share the card, nearer
 * side first, so the other direction is never hidden.
 */
data class ModeCard(
    val mode: VehicleType,
    /** Short name for the card and map label: "Wesley Hospital" */
    val name: String,
    val sides: List<CardSide>
) {
    val group: StopGroup get() = sides.first().group
    val groups: List<StopGroup> get() = sides.map { it.group }
    val distanceM: Double get() = sides.first().distanceM
    val departures: List<Departure> get() = sides.flatMap { it.departures }

    /** The two sides point roughly opposite ways: label the second "across the road" */
    val isAcrossTheRoad: Boolean
        get() {
            val a = sides.getOrNull(0)?.heading ?: return false
            val b = sides.getOrNull(1)?.heading ?: return false
            val diff = Math.floorMod(a - b, 360).let { minOf(it, 360 - it) }
            return diff > 120
        }

    /** All the card's stops as one group, for the departures sheet */
    fun asOneGroup(): StopGroup =
        if (sides.size == 1) group else group.copy(stops = groups.flatMap { it.stops })
}

/** A service drops off its card this long after its live time (it has left) */
const val DEPARTED_GRACE_S = 30

/** Rows per card with one stop; with two sides, rows per side */
const val MODE_CARD_ROWS = 3
const val SIDE_ROWS = 2

/** Same-named stops of one mode within this distance are one place (opposite sides, bays) */
const val PAIR_RADIUS_M = 150.0

/** Card order along the bottom */
private val MODE_ORDER = listOf(VehicleType.Rail, VehicleType.Bus, VehicleType.Ferry, VehicleType.Tram)

/**
 * One card per mode that has something running nearby (train, bus, ferry, tram): the nearest
 * stop group with a running departure of that mode, plus a same-named partner within
 * PAIR_RADIUS_M (the other side of the road), each with its next departures of that mode, one
 * per route + direction. Mode comes from each departure, not the stop, so a rail-replacement bus
 * at a station counts as a bus. Cancelled / skipped services are left out.
 */
fun buildModeCards(
    groups: List<StopGroup>,
    departures: List<Departure>,
    userLat: Double?,
    userLon: Double?
): List<ModeCard> {
    val groupOfStop = groups.flatMap { g -> g.stops.map { it.id to g } }.toMap()
    fun distance(g: StopGroup) =
        if (userLat != null && userLon != null) approxMeters(userLat, userLon, g.latitude, g.longitude)
        else g.stops.firstOrNull()?.distanceMeters ?: 0.0

    val running = departures.filter { !it.notRunningHere && it.liveSeconds > -DEPARTED_GRACE_S && groupOfStop[it.stopId] != null }
    return MODE_ORDER.mapNotNull { mode ->
        val ofMode = running.filter { it.mode == mode }
        val serving = ofMode.map { groupOfStop.getValue(it.stopId) }.distinctBy { it.id }
        val nearest = serving.minByOrNull(::distance) ?: return@mapNotNull null
        val name = mapStopName(nearest.name)
        val partner = serving
            .filter { it.id != nearest.id && mapStopName(it.name) == name &&
                approxMeters(it.latitude, it.longitude, nearest.latitude, nearest.longitude) <= PAIR_RADIUS_M }
            .minByOrNull(::distance)
        val sideGroups = listOfNotNull(nearest, partner)
        val rowsPerSide = if (sideGroups.size > 1) SIDE_ROWS else MODE_CARD_ROWS
        val sides = sideGroups.map { g ->
            CardSide(g, distance(g), ofMode
                .filter { groupOfStop[it.stopId]?.id == g.id }
                .sortedBy { it.liveSeconds }
                .distinctBy { "${it.routeId}|${it.directionId}" }
                .take(rowsPerSide))
        }
        ModeCard(mode, name, sides)
    }
}

/** Services per side of a card whose route is drawn on the map: the ones the card shows */
const val PATHS_PER_SIDE = 2

/**
 * The services whose whole route is drawn on the home map, with their card: what each card
 * shows, one per route shape (412 and 444 still get a line each; two 412s don't).
 */
fun pathDepartures(cards: List<ModeCard>): List<Pair<ModeCard, Departure>> =
    cards.flatMap { card -> card.sides.flatMap { side -> side.departures.take(PATHS_PER_SIDE).map { card to it } } }
        .filter { it.second.shapeId != null }
        .distinctBy { it.second.shapeId }

/** One nearby stop and its next departures, for the list in the expanded panel */
data class NearbyStop(val group: StopGroup, val distanceM: Double, val departures: List<Departure>)

/** Every nearby stop, nearest first, with its next departures (one per route + direction) */
fun buildNearbyStops(
    groups: List<StopGroup>,
    departures: List<Departure>,
    userLat: Double?,
    userLon: Double?
): List<NearbyStop> {
    val groupOfStop = groups.flatMap { g -> g.stops.map { it.id to g.id } }.toMap()
    val byGroup = departures
        .filter { !it.notRunningHere && it.liveSeconds > -DEPARTED_GRACE_S }
        .groupBy { groupOfStop[it.stopId] }
    return groups.map { g ->
        val d = if (userLat != null && userLon != null) approxMeters(userLat, userLon, g.latitude, g.longitude)
            else g.stops.firstOrNull()?.distanceMeters ?: 0.0
        NearbyStop(g, d, byGroup[g.id].orEmpty()
            .sortedBy { it.liveSeconds }
            .distinctBy { "${it.routeId}|${it.directionId}" }
            .take(MODE_CARD_ROWS))
    }.sortedBy { it.distanceM }
}

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

/** "120 m", "1.4 km" */
fun distanceLabel(m: Double): String = if (m < 1000) "${(m / 10).toInt() * 10} m" else "%.1f km".format(m / 1000)
