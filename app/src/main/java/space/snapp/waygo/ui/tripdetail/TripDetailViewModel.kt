package space.snapp.waygo.ui.tripdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import space.snapp.waygo.data.api.TransitApiService
import space.snapp.waygo.data.api.models.NextTrip
import space.snapp.waygo.data.api.models.TripDetailResponse

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

    fun switchTrip(newTripId: String) {
        loadParams = loadParams?.copy(tripId = newTripId)
            ?: LoadParams(newTripId, "", 0, null, null)
        startRefresh()
    }

    private fun startRefresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            while (true) {
                val params = loadParams ?: break
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
                delay(10_000)
            }
        }
    }

    fun stop() {
        refreshJob?.cancel()
    }

    override fun onCleared() {
        super.onCleared()
        refreshJob?.cancel()
    }
}
