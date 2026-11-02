package space.snapp.waygo.ui.tripdetail

import space.snapp.waygo.data.pollWhileVisible
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import space.snapp.waygo.data.api.TransitApiService
import space.snapp.waygo.data.api.models.NextTrip
import space.snapp.waygo.data.api.models.TripDetailResponse

private const val LOCATION_EVERY_MS = 60_000L

class TripDetailViewModel : ViewModel() {
    private val api = TransitApiService.instance

    private val _tripDetail = MutableStateFlow<TripDetailResponse?>(null)
    val tripDetail: StateFlow<TripDetailResponse?> = _tripDetail

    private val _nextTrips = MutableStateFlow<List<NextTrip>>(emptyList())
    val nextTrips: StateFlow<List<NextTrip>> = _nextTrips

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private data class LoadParams(
        val tripId: String?,
        val routeId: String,
        val directionId: Int,
        val userLat: Double?,
        val userLon: Double?
    )

    private var loadParams: LoadParams? = null
    private var refreshJob: Job? = null

    fun load(tripId: String?, routeId: String, directionId: Int, userLat: Double?, userLon: Double?) {
        loadParams = LoadParams(tripId, routeId, directionId, userLat, userLon)
        _tripDetail.value = null
        _nextTrips.value = emptyList()
        _error.value = null
        startRefresh()
    }

    // One-off messages for the screen (e.g. no trips in the other direction)
    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice

    fun clearNotice() { _notice.value = null }

    /**
     * Show the same route in the opposite direction, anchored on the user's nearest stop.
     * Fetches first and only switches on success, so a direction with no more trips today
     * (e.g. peak-only routes) leaves the current trip on screen instead of an empty error.
     */
    fun switchDirection() {
        val params = loadParams ?: return
        val info = _tripDetail.value?.tripInfo ?: return
        viewModelScope.launch {
            runCatching {
                api.tripStops(
                    routeId = info.routeId,
                    directionId = 1 - info.directionId,
                    userLat = params.userLat,
                    userLon = params.userLon
                )
            }.onSuccess { detail ->
                loadParams = params.copy(
                    tripId = detail.tripInfo.tripId,
                    routeId = detail.tripInfo.routeId,
                    directionId = detail.tripInfo.directionId
                )
                _tripDetail.value = detail
                _nextTrips.value = emptyList()
                startRefresh()
            }.onFailure { e ->
                if (e is CancellationException) throw e
                _notice.value = "No more trips in the other direction today"
            }
        }
    }

    fun switchTrip(newTripId: String) {
        loadParams = loadParams?.copy(tripId = newTripId)
            ?: LoadParams(newTripId, "", 0, null, null)
        startRefresh()
    }

    private fun startRefresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            pollWhileVisible(10_000) {
                val params = loadParams ?: return@pollWhileVisible
                try {
                    if (_tripDetail.value == null) _isLoading.value = true
                    val detail = api.tripStops(
                        tripId = params.tripId,
                        routeId = if (params.tripId == null) params.routeId else null,
                        directionId = if (params.tripId == null) params.directionId else null,
                        userLat = params.userLat,
                        userLon = params.userLon
                    )
                    _tripDetail.value = detail
                    // Cache the resolved trip_id for subsequent refreshes
                    loadParams = params.copy(tripId = detail.tripInfo.tripId)
                    _error.value = null

                    // Load next trips using nearest (or first upcoming) stop
                    val anchorStop = detail.stops.firstOrNull { it.isNearest }
                        ?: detail.stops.firstOrNull { it.isUpcoming }
                    if (anchorStop != null) {
                        runCatching {
                            val resp = api.nextTrips(
                                routeId = detail.tripInfo.routeId,
                                directionId = detail.tripInfo.directionId,
                                stopId = anchorStop.stopId
                            )
                            _nextTrips.value = resp.trips
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (_tripDetail.value == null) _error.value = e.message
                } finally {
                    _isLoading.value = false
                }
            }
        }
    }

    private var locationAppliedAt = 0L

    /**
     * Keep the user's position roughly current so "your nearest stop" follows them as they
     * walk or ride. Taken at most once a minute; it rides along with the regular refresh
     * and never triggers a request of its own.
     */
    fun updateLocation(userLat: Double?, userLon: Double?) {
        if (userLat == null || userLon == null) return
        val now = System.currentTimeMillis()
        if (now - locationAppliedAt < LOCATION_EVERY_MS) return
        locationAppliedAt = now
        loadParams = loadParams?.copy(userLat = userLat, userLon = userLon)
    }

    fun stop() {
        refreshJob?.cancel()
    }

    override fun onCleared() {
        super.onCleared()
        refreshJob?.cancel()
    }
}
