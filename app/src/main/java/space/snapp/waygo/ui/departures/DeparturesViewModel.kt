package space.snapp.waygo.ui.departures

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import space.snapp.waygo.data.api.TransitApiService
import space.snapp.waygo.data.api.models.Departure
import space.snapp.waygo.data.api.models.Stop

class DeparturesViewModel : ViewModel() {
    private val api = TransitApiService.instance

    private val _departures = MutableStateFlow<List<Departure>>(emptyList())
    val departures = _departures.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    private val _routeFilter = MutableStateFlow<String?>(null)
    val routeFilter = _routeFilter.asStateFlow()

    private var refreshJob: Job? = null
    private var currentStops: List<Stop> = emptyList()
    private var currentGetOffStopId: String? = null

    val filteredDepartures get() = _departures.value.filter { dep ->
        _routeFilter.value == null || dep.routeNumber == _routeFilter.value
    }

    val availableRoutes get() = _departures.value.map { it.routeNumber }.distinct().sorted()

    fun setRouteFilter(route: String?) { _routeFilter.value = route }

    fun startAutoRefresh(stops: List<Stop>, getOffStopId: String? = null) {
        currentStops = stops
        currentGetOffStopId = getOffStopId
        _isLoading.value = true   // show spinner immediately, before coroutine fires
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            while (true) {
                fetch()
                delay(10_000)
            }
        }
    }

    fun stopAutoRefresh() { refreshJob?.cancel() }

    private suspend fun fetch() {
        val codes = currentStops.mapNotNull { it.stopCode }
        if (codes.isEmpty()) return
        _isLoading.value = true
        runCatching {
            api.departures(
                stops = codes.joinToString(","),
                getOffStop = currentGetOffStopId
            )
        }.onSuccess {
            _departures.value = it
            _error.value = null
        }.onFailure {
            _error.value = it.message
        }
        _isLoading.value = false
    }

    override fun onCleared() {
        super.onCleared()
        stopAutoRefresh()
    }
}
