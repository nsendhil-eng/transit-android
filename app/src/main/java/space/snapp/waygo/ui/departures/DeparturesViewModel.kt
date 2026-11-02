package space.snapp.waygo.ui.departures

import space.snapp.waygo.data.pollWhileVisible
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

// Per stop, so every route at a busy stop gets its next three departures for the cards
private const val DEPARTURES_PER_STOP = 12

class DeparturesViewModel : ViewModel() {
    private val api = TransitApiService.instance

    private val _departures = MutableStateFlow<List<Departure>>(emptyList())
    val departures = _departures.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    private var refreshJob: Job? = null
    private var currentStops: List<Stop> = emptyList()
    private var currentGetOffStopId: String? = null

    fun startAutoRefresh(stops: List<Stop>, getOffStopId: String? = null) {
        // Switching stops: drop the old stop's departures so they don't flash as cards
        if (stops != currentStops || getOffStopId != currentGetOffStopId) _departures.value = emptyList()
        currentStops = stops
        currentGetOffStopId = getOffStopId
        _isLoading.value = true   // show spinner immediately, before coroutine fires
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch { pollWhileVisible(10_000) { fetch() } }
    }

    fun stopAutoRefresh() { refreshJob?.cancel() }

    private suspend fun fetch() {
        val codes = currentStops.mapNotNull { it.stopCode?.takeIf(String::isNotBlank) }
        if (codes.isEmpty()) {
            // Nothing to ask for: show "No upcoming departures" rather than an endless spinner
            _departures.value = emptyList()
            _isLoading.value = false
            return
        }
        _isLoading.value = true
        runCatching {
            api.departures(
                stops = codes.joinToString(","),
                perStop = DEPARTURES_PER_STOP,
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
