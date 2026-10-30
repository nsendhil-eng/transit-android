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

// Re-query stops only after moving this far (location updates arrive every ~10 s)
private const val REFETCH_DISTANCE_M = 150.0
private const val DEPARTURE_REFRESH_MS = 20_000L
// Departures per stop: enough that busy stops still show their less frequent routes
private const val DEPARTURES_PER_STOP = 12
private const val MAX_STOPS = 20

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
            .mapNotNull { it.stopCode?.takeIf(String::isNotBlank) }
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

private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r = 6371000.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
            Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
            Math.sin(dLon / 2) * Math.sin(dLon / 2)
    return r * 2 * Math.asin(Math.sqrt(a))
}
