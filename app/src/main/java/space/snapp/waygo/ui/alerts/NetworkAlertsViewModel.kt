package space.snapp.waygo.ui.alerts

import space.snapp.waygo.data.pollWhileVisible
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import space.snapp.waygo.data.api.TransitApiService
import space.snapp.waygo.data.api.models.ServiceAlert
import kotlin.math.roundToInt

private const val REFRESH_MS = 120_000L
// Close enough to the user to headline on the home screen / count as "near you"
const val NEAR_YOU_M = 2_000

/**
 * Every network alert, nearest first (for the home screen and the alerts page).
 * Lives for the app session, so the banner's collapsed state survives tab switches:
 * it opens on each launch and stays a warning icon once closed.
 */
class NetworkAlertsViewModel : ViewModel() {
    private val api = TransitApiService.instance

    private val _alerts = MutableStateFlow<List<ServiceAlert>>(emptyList())
    val alerts = _alerts.asStateFlow()

    private val _bannerCollapsed = MutableStateFlow(false)
    val bannerCollapsed = _bannerCollapsed.asStateFlow()

    private var lat: Double? = null
    private var lon: Double? = null
    private var job: Job? = null

    fun collapseBanner() { _bannerCollapsed.value = true }

    /** Refetch when the location changes meaningfully (~500 m grid), then every 2 min. */
    fun onLocation(newLat: Double?, newLon: Double?) {
        val key = { v: Double? -> v?.let { (it * 200).roundToInt() } }
        if (job != null && key(newLat) == key(lat) && key(newLon) == key(lon)) return
        lat = newLat
        lon = newLon
        job?.cancel()
        job = viewModelScope.launch {
            pollWhileVisible(REFRESH_MS) {
                try {
                    _alerts.value = api.allAlerts(lat, lon).alerts
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // keep the last list
                }
            }
        }
    }
}

/** The alert worth opening the app with: a current disruption close by, most important first. */
fun headlineAlert(alerts: List<ServiceAlert>): ServiceAlert? =
    alerts
        .filter { (it.distanceM ?: Int.MAX_VALUE) <= NEAR_YOU_M && !it.longRunning && it.priority >= 50 }
        .maxWithOrNull(compareBy<ServiceAlert> { it.priority }.thenByDescending { it.distanceM ?: Int.MAX_VALUE })
