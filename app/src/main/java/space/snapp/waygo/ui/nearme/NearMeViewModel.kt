package space.snapp.waygo.ui.nearme

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import space.snapp.waygo.data.api.TransitApiService
import space.snapp.waygo.data.api.models.Stop
import space.snapp.waygo.data.api.models.VehicleType

class NearMeViewModel : ViewModel() {
    private val api = TransitApiService.instance

    private val _allStops = MutableStateFlow<List<Stop>>(emptyList())
    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    private val _selectedType = MutableStateFlow<VehicleType?>(null)
    val selectedType = _selectedType.asStateFlow()

    val availableTypes get() = VehicleType.entries.filter { type ->
        _allStops.value.any { it.primaryVehicleType == type }
    }

    val allStops = _allStops.asStateFlow()

    fun selectType(type: VehicleType) { _selectedType.value = type }

    fun closestStops(type: VehicleType): List<Stop> {
        val filtered = _allStops.value.filter { it.primaryVehicleType == type }
        val stations = mutableListOf<String>()
        for (stop in filtered) {
            val key = stop.parentStation?.takeIf { it.isNotEmpty() } ?: stop.id
            if (!stations.contains(key)) {
                stations.add(key)
                if (stations.size == 2) break
            }
        }
        return filtered.filter { stop ->
            val key = stop.parentStation?.takeIf { it.isNotEmpty() } ?: stop.id
            stations.contains(key)
        }
    }

    fun fetchNearby(lat: Double, lon: Double) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            runCatching { api.stopsNearMe(lat, lon) }
                .onSuccess { stops ->
                    _allStops.value = stops
                    val types = availableTypes
                    if (_selectedType.value == null || !types.contains(_selectedType.value)) {
                        _selectedType.value = types.firstOrNull()
                    }
                }
                .onFailure { _error.value = it.message }
            _isLoading.value = false
        }
    }
}
