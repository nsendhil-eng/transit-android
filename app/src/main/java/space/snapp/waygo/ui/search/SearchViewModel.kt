package space.snapp.waygo.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import space.snapp.waygo.data.api.TransitApiService
import space.snapp.waygo.data.api.models.Departure
import space.snapp.waygo.data.api.models.SearchResponse
import space.snapp.waygo.data.api.models.Stop

class SearchViewModel : ViewModel() {
    private val api = TransitApiService.instance

    val query = MutableStateFlow("")
    private val _results = MutableStateFlow(SearchResponse())
    val results = _results.asStateFlow()
    private val _selectedStops = MutableStateFlow<List<Stop>>(emptyList())
    val selectedStops = _selectedStops.asStateFlow()
    private val _stopDepartures = MutableStateFlow<Map<String, List<Departure>>>(emptyMap())
    val stopDepartures = _stopDepartures.asStateFlow()
    private val _isSearching = MutableStateFlow(false)
    val isSearching = _isSearching.asStateFlow()
    private val _hasSearched = MutableStateFlow(false)
    val hasSearched = _hasSearched.asStateFlow()

    var userLat: Double? = null
    var userLon: Double? = null

    private var searchJob: Job? = null

    fun onQueryChange(q: String) {
        query.value = q
        searchJob?.cancel()
        if (q.trim().length < 2) {
            _results.value = SearchResponse()
            _hasSearched.value = false
            _stopDepartures.value = emptyMap()
            return
        }
        searchJob = viewModelScope.launch {
            delay(300)
            _isSearching.value = true
            runCatching {
                api.search(q.trim(), userLat, userLon)
            }.onSuccess { response ->
                _results.value = response
                _hasSearched.value = true
                // Inline departures
                val codes = response.stops.mapNotNull { it.stopCode }
                if (codes.isNotEmpty()) {
                    runCatching { api.departures(codes.joinToString(","), perStop = 3) }
                        .onSuccess { deps ->
                            val grouped = mutableMapOf<String, MutableList<Departure>>()
                            deps.forEach { dep ->
                                grouped.getOrPut(dep.stopId) { mutableListOf() }
                                    .takeIf { it.size < 3 }?.add(dep)
                            }
                            _stopDepartures.value = grouped
                        }
                }
            }
            _isSearching.value = false
        }
    }

    fun addStop(stop: Stop) {
        if (_selectedStops.value.none { it.id == stop.id }) {
            _selectedStops.value = _selectedStops.value + stop
        }
    }

    fun removeStop(stop: Stop) {
        _selectedStops.value = _selectedStops.value.filter { it.id != stop.id }
    }

    fun clearAll() { _selectedStops.value = emptyList() }

    fun isSelected(stop: Stop) = _selectedStops.value.any { it.id == stop.id }

    fun expandedStops(selected: List<Stop>, searchResults: List<Stop>): List<Stop> {
        val result = mutableListOf<Stop>()
        val seen = mutableSetOf<String>()
        for (stop in selected) {
            val parent = stop.parentStation?.takeIf { it.isNotEmpty() }
            if (parent != null) {
                val siblings = searchResults.filter { it.parentStation == parent }
                for (s in siblings) if (seen.add(s.id)) result.add(s)
            }
            if (seen.add(stop.id)) result.add(stop)
        }
        return result.ifEmpty { selected }
    }
}
