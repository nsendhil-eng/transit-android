package space.snapp.waygo.ui.nearme

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import space.snapp.waygo.data.api.TransitApiService
import space.snapp.waygo.data.api.models.Departure
import space.snapp.waygo.data.api.models.Stop
import space.snapp.waygo.data.api.models.VehicleType

private const val WALK_METRES_PER_MIN = 80.0
// Re-query stops only after moving this far (location updates arrive every ~10 s)
private const val REFETCH_DISTANCE_M = 150.0
private const val DEPARTURE_REFRESH_MS = 20_000L
// Departures per stop: enough that busy stops still show their less frequent routes
private const val DEPARTURES_PER_STOP = 12
private const val MAX_STOPS = 20

/** One route heading one way, at the nearest stop that serves it. */
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
    /** Next departures from that stop, soonest first (max 3). */
    val departures: List<Departure>
) {
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

class NearMeViewModel : ViewModel() {
    private val api = TransitApiService.instance

    private val _stops = MutableStateFlow<List<Stop>>(emptyList())
    val stops = _stops.asStateFlow()
    private val _departures = MutableStateFlow<List<Departure>>(emptyList())
    val departures = _departures.asStateFlow()
    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    // null = all modes
    private val _modeFilter = MutableStateFlow<VehicleType?>(null)
    val modeFilter = _modeFilter.asStateFlow()

    private var fetchedLat: Double? = null
    private var fetchedLon: Double? = null
    private var refreshJob: Job? = null

    fun setModeFilter(type: VehicleType?) { _modeFilter.value = type }

    fun onLocation(lat: Double, lon: Double) {
        val prevLat = fetchedLat
        val prevLon = fetchedLon
        if (prevLat != null && prevLon != null && haversineMeters(prevLat, prevLon, lat, lon) < REFETCH_DISTANCE_M) return
        fetchedLat = lat
        fetchedLon = lon
        fetchStops(lat, lon)
    }

    fun retry() {
        val lat = fetchedLat ?: return
        val lon = fetchedLon ?: return
        fetchStops(lat, lon)
    }

    /** Pause polling while the tab is off screen; resume picks up immediately. */
    fun pause() { refreshJob?.cancel() }
    fun resume() { if (_stops.value.isNotEmpty()) startDepartureRefresh() }

    private fun fetchStops(lat: Double, lon: Double) {
        viewModelScope.launch {
            if (_departures.value.isEmpty()) _isLoading.value = true
            _error.value = null
            runCatching { api.stopsNearMe(lat, lon) }
                .onSuccess { stops ->
                    _stops.value = stops
                    if (stops.isEmpty()) _isLoading.value = false else startDepartureRefresh()
                }
                .onFailure {
                    if (it is CancellationException) throw it
                    _error.value = it.message
                    _isLoading.value = false
                }
        }
    }

    private fun startDepartureRefresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            while (true) {
                fetchDepartures()
                delay(DEPARTURE_REFRESH_MS)
            }
        }
    }

    private suspend fun fetchDepartures() {
        val codes = _stops.value
            .sortedBy { it.distanceMeters ?: Double.MAX_VALUE }
            .mapNotNull { it.stopCode }
            .distinct()
            .take(MAX_STOPS)
        if (codes.isEmpty()) return
        try {
            _departures.value = api.departures(codes.joinToString(","), perStop = DEPARTURES_PER_STOP)
            _error.value = null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (_departures.value.isEmpty()) _error.value = e.message
        } finally {
            _isLoading.value = false
        }
    }

    override fun onCleared() {
        super.onCleared()
        refreshJob?.cancel()
    }
}

/** Modes that actually have departures nearby, in a stable order. */
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

private val Departure.mode: VehicleType
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

private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r = 6371000.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
            Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
            Math.sin(dLon / 2) * Math.sin(dLon / 2)
    return r * 2 * Math.asin(Math.sqrt(a))
}
