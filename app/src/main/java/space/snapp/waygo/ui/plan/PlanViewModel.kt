package space.snapp.waygo.ui.plan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import space.snapp.waygo.data.api.NominatimService
import space.snapp.waygo.data.api.TransitApiService
import space.snapp.waygo.data.api.models.AddressResult
import space.snapp.waygo.data.api.models.Itinerary

class PlanViewModel : ViewModel() {

    enum class ActiveField { FROM, TO, NONE }

    private val nominatim = NominatimService.instance
    private val api = TransitApiService.instance

    val fromQuery = MutableStateFlow("")
    val toQuery = MutableStateFlow("")

    private val _fromSelected = MutableStateFlow<AddressResult?>(null)
    val fromSelected = _fromSelected.asStateFlow()

    private val _toSelected = MutableStateFlow<AddressResult?>(null)
    val toSelected = _toSelected.asStateFlow()

    private val _suggestions = MutableStateFlow<List<AddressResult>>(emptyList())
    val suggestions = _suggestions.asStateFlow()

    private val _activeField = MutableStateFlow(ActiveField.FROM)
    val activeField = _activeField.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching = _isSearching.asStateFlow()

    private val _itineraries = MutableStateFlow<List<Itinerary>>(emptyList())
    val itineraries = _itineraries.asStateFlow()

    private val _isPlanning = MutableStateFlow(false)
    val isPlanning = _isPlanning.asStateFlow()

    private val _planError = MutableStateFlow<String?>(null)
    val planError = _planError.asStateFlow()

    var userLat: Double? = null
    var userLon: Double? = null

    private var searchJob: Job? = null
    private var planJob: Job? = null

    fun activateField(field: ActiveField) {
        _activeField.value = field
        // Show suggestions for the current text in that field when re-focused
        val q = if (field == ActiveField.FROM) fromQuery.value else toQuery.value
        if (q.trim().length >= 3) searchAddresses(q) else _suggestions.value = emptyList()
    }

    fun onFromQueryChange(q: String) {
        fromQuery.value = q
        _fromSelected.value = null
        _activeField.value = ActiveField.FROM
        searchAddresses(q)
    }

    fun onToQueryChange(q: String) {
        toQuery.value = q
        _toSelected.value = null
        _activeField.value = ActiveField.TO
        searchAddresses(q)
    }

    fun selectSuggestion(result: AddressResult) {
        when (_activeField.value) {
            ActiveField.FROM -> {
                _fromSelected.value = result
                fromQuery.value = result.shortName
                _activeField.value = ActiveField.TO
            }
            ActiveField.TO -> {
                _toSelected.value = result
                toQuery.value = result.shortName
                _activeField.value = ActiveField.NONE
            }
            ActiveField.NONE -> {}
        }
        _suggestions.value = emptyList()
        // Auto-search when both endpoints are now set
        val from = _fromSelected.value
        val to = _toSelected.value ?: if (_activeField.value == ActiveField.NONE) result else null
        if (from != null && to != null) fetchPlan(from, to)
    }

    private fun fetchPlan(from: AddressResult, to: AddressResult) {
        planJob?.cancel()
        _itineraries.value = emptyList()
        _planError.value = null
        planJob = viewModelScope.launch {
            _isPlanning.value = true
            val now = java.util.Calendar.getInstance()
            val date = "%04d-%02d-%02d".format(
                now.get(java.util.Calendar.YEAR),
                now.get(java.util.Calendar.MONTH) + 1,
                now.get(java.util.Calendar.DAY_OF_MONTH)
            )
            val h = now.get(java.util.Calendar.HOUR_OF_DAY)
            val m = now.get(java.util.Calendar.MINUTE)
            val ampm = if (now.get(java.util.Calendar.AM_PM) == java.util.Calendar.AM) "am" else "pm"
            val h12 = now.get(java.util.Calendar.HOUR).let { if (it == 0) 12 else it }
            val time = "%d:%02d%s".format(h12, m, ampm)
            runCatching { api.plan(from.lat, from.lon, to.lat, to.lon, date = date, time = time) }
                .onSuccess { _itineraries.value = it.itineraries }
                .onFailure { _planError.value = "Could not load journey options" }
            _isPlanning.value = false
        }
    }

    fun useMyLocationAsFrom() {
        val lat = userLat ?: return
        val lon = userLon ?: return
        val myLoc = AddressResult(-1L, "My Location", lat, lon)
        _fromSelected.value = myLoc
        fromQuery.value = "My Location"
        _activeField.value = ActiveField.TO
        _suggestions.value = emptyList()
    }

    fun swap() {
        val tmpQuery = fromQuery.value
        val tmpSelected = _fromSelected.value
        fromQuery.value = toQuery.value
        _fromSelected.value = _toSelected.value
        toQuery.value = tmpQuery
        _toSelected.value = tmpSelected
    }

    fun clearFrom() {
        fromQuery.value = ""
        _fromSelected.value = null
        _itineraries.value = emptyList()
        _activeField.value = ActiveField.FROM
        _suggestions.value = emptyList()
    }

    fun clearTo() {
        toQuery.value = ""
        _toSelected.value = null
        _itineraries.value = emptyList()
        _activeField.value = ActiveField.TO
        _suggestions.value = emptyList()
    }

    fun reset() {
        fromQuery.value = ""
        toQuery.value = ""
        _fromSelected.value = null
        _toSelected.value = null
        _suggestions.value = emptyList()
        _itineraries.value = emptyList()
        _activeField.value = ActiveField.FROM
    }

    private fun searchAddresses(q: String) {
        searchJob?.cancel()
        if (q.trim().length < 3) {
            _suggestions.value = emptyList()
            return
        }
        searchJob = viewModelScope.launch {
            delay(350) // debounce — also respects Nominatim's 1 req/s policy
            _isSearching.value = true
            runCatching { nominatim.search(q.trim()) }
                .onSuccess { results ->
                    _suggestions.value = results.map {
                        AddressResult(
                            placeId = it.placeId,
                            displayName = it.displayName,
                            lat = it.lat.toDoubleOrNull() ?: 0.0,
                            lon = it.lon.toDoubleOrNull() ?: 0.0
                        )
                    }
                }
            _isSearching.value = false
        }
    }
}
