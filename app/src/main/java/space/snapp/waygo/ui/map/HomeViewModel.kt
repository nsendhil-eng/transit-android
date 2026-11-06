package space.snapp.waygo.ui.map

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import org.osmdroid.util.GeoPoint
import space.snapp.waygo.data.api.TransitApiService
import space.snapp.waygo.data.api.models.Departure
import java.io.File

/** Trains and ferries are looked for this far away (about a 15 minute walk); buses within 500 m */
private const val FAR_RADIUS_M = 1200
/** With nothing at all within those, the nearest stops this far away (Pullenvale: 1.9 km) */
private const val WIDE_RADIUS_M = 3000
/** Refetch nearby stops only after moving this far (GPS updates every 10 s) */
private const val REFETCH_STOPS_AFTER_M = 150.0
/** The saved view is rewritten at most this often */
private const val SAVE_EVERY_MS = 60_000L

/**
 * The home screen's data. It outlives the screen, so coming back from another tab shows the
 * rows at once and the map where it was, rather than starting over. The screen still runs the
 * departures refresh loop, so nothing is fetched while another tab is showing.
 */
class HomeViewModel(app: Application) : AndroidViewModel(app) {
    private val api = TransitApiService.instance
    private val snapshotFile = File(app.filesDir, "home-snapshot.json")
    private val prefs = app.getSharedPreferences("home", Context.MODE_PRIVATE)
    private val savedSnapshot = viewModelScope.async(Dispatchers.IO) { HomeSnapshot.load(snapshotFile) }
    private var lastSavedMs = 0L

    var stopGroups by mutableStateOf<List<StopGroup>>(emptyList())
    // Next departures nearby: each route + direction at its stop nearest you
    var nearbyDeps by mutableStateOf<List<Departure>>(emptyList())
    // When departures last arrived; the panel dims if refreshes keep failing
    var depsUpdatedMs by mutableLongStateOf(0L)
    // A refresh is on its way, so old rows aren't a connection problem yet
    var refreshing by mutableStateOf(false)
    var lastStopsFetch by mutableStateOf<GeoPoint?>(null)
    // Rebuilt every 15 s too, so a service that has just left drops off without waiting for a fetch
    var nowMs by mutableLongStateOf(System.currentTimeMillis())

    // The panel row tapped once: its stop, route and live vehicles on the map; a second tap opens the stop
    var selectedKey by mutableStateOf<String?>(null)
    // The map follows you until you pan it; the re-centre button turns following back on
    var following by mutableStateOf(true)
    // Tracked to a quarter level, for thinning out stops
    var mapZoom by mutableDoubleStateOf(DEFAULT_ZOOM)
    /** Where the map was left, to reopen there */
    var leftMapAt: GeoPoint? = null
    var leftMapZoom = DEFAULT_ZOOM
    // Shapes depend only on the timetable, so each is fetched once and kept for the session
    val shapeCache = mutableStateMapOf<String, List<GeoPoint>>()

    var peekPx by mutableIntStateOf(0)
    var headerPx by mutableIntStateOf(0)
    // Dragged down to just the header, so the map gets the screen
    var lowered by mutableStateOf(false)

    /** Where to open the map before there's a GPS fix: where you were last time */
    val lastKnownLocation: GeoPoint? = run {
        val lat = prefs.getString("lat", null)?.toDoubleOrNull()
        val lon = prefs.getString("lon", null)?.toDoubleOrNull()
        if (lat != null && lon != null) GeoPoint(lat, lon) else null
    }

    /**
     * Fetch nearby stops on the first fix, then only after moving a fair way. No fix, no stops:
     * showing the CBD's departures as if you were there would mislead.
     */
    suspend fun onLocation(lat: Double, lon: Double) {
        if (!inSeq(lat, lon)) return
        restoreSnapshot(lat, lon)
        val last = lastStopsFetch
        if (last != null && approxMeters(last.latitude, last.longitude, lat, lon) < REFETCH_STOPS_AFTER_M) return
        runCatching {
            // Semi-rural streets can be kilometres from a stop: look wider rather than show nothing
            api.stopsNearMe(lat, lon, radius = 500, farRadius = FAR_RADIUS_M)
                .ifEmpty { api.stopsNearMe(lat, lon, radius = WIDE_RADIUS_M) }
        }.onSuccess { stops ->
            lastStopsFetch = GeoPoint(lat, lon)
            stopGroups = groupStops(stops)
            // No usable stops: nothing will fetch departures, so end the "finding" state here
            if (stopGroups.isEmpty()) { nearbyDeps = emptyList(); depsUpdatedMs = System.currentTimeMillis() }
        }
    }

    /** The last view, if it's recent and from here. Its stops also spare a fetch. */
    private suspend fun restoreSnapshot(lat: Double, lon: Double) {
        if (lastStopsFetch != null) return
        val saved = savedSnapshot.await() ?: return
        if (lastStopsFetch != null || !saved.usableAt(lat, lon, System.currentTimeMillis())) return
        lastStopsFetch = GeoPoint(saved.lat, saved.lon)
        stopGroups = groupStops(saved.stops)
        nearbyDeps = saved.departures
        depsUpdatedMs = saved.savedAtMs
        refreshing = true
    }

    /** One refresh of the departures at [stopCodes]; the screen decides when */
    suspend fun refreshDepartures(stopCodes: List<String>) {
        // Distances from where the stops were fetched, so the API's nearest-stop choice (and the
        // panel's order) only changes when the stops do
        val from = lastStopsFetch
        runCatching {
            api.departures(stopCodes.joinToString(","), withHeading = 1,
                perRoute = ROUTE_ROW_TIMES, lat = from?.latitude, lon = from?.longitude)
        }.onSuccess {
            nearbyDeps = it
            depsUpdatedMs = System.currentTimeMillis()
            saveSnapshot()
        }
        refreshing = false
    }

    private fun saveSnapshot() {
        val from = lastStopsFetch ?: return
        val now = System.currentTimeMillis()
        if (now - lastSavedMs < SAVE_EVERY_MS) return
        lastSavedMs = now
        val snapshot = HomeSnapshot(now, from.latitude, from.longitude, stopGroups.flatMap { it.stops }, nearbyDeps)
        prefs.edit { putString("lat", from.latitude.toString()); putString("lon", from.longitude.toString()) }
        viewModelScope.launch(Dispatchers.IO) { runCatching { snapshot.save(snapshotFile) } }
    }
}
