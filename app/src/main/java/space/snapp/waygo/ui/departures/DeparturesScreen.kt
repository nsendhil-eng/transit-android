package space.snapp.waygo.ui.departures

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import space.snapp.waygo.data.api.models.Departure
import space.snapp.waygo.data.api.models.Stop
import space.snapp.waygo.ui.routecards.RouteCardList
import space.snapp.waygo.ui.routecards.buildRouteGroups
import space.snapp.waygo.ui.routecards.rememberNow

/**
 * Departures for a set of stops (a map stop / station, selected search stops, or a favourite),
 * shown as one route card per route + direction — the same cards as Near Me.
 */
@Composable
fun DeparturesScreen(
    viewModel: DeparturesViewModel,
    stops: List<Stop>,
    getOffStopId: String? = null,
    onDepartureClick: ((Departure) -> Unit)? = null,
    // False when stop distances aren't relative to where the user is now (saved favourites
    // keep the distance from when they were added) — hides walk times and sorts by time only
    useStopDistances: Boolean = true
) {
    val departures by viewModel.departures.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val now = rememberNow()

    LaunchedEffect(stops, getOffStopId) {
        viewModel.startAutoRefresh(stops, getOffStopId)
    }
    DisposableEffect(Unit) { onDispose { viewModel.stopAutoRefresh() } }

    val groups = remember(departures, stops, now, useStopDistances) {
        val groupingStops = if (useStopDistances) stops else stops.map { it.copy(distanceMeters = null) }
        buildRouteGroups(departures, groupingStops, null, now)
    }
    // Name the stop on each card only when it isn't obvious from context: several separate
    // stops (e.g. both sides of the road). Platforms of one station still show "Platform N".
    val distinctPlaces = remember(stops) {
        stops.map { it.parentStation?.takeIf { p -> p.isNotEmpty() } ?: it.id }.distinct().size
    }

    when {
        error != null && departures.isEmpty() -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Failed to load departures", style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { viewModel.startAutoRefresh(stops, getOffStopId) }) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Retry")
                    }
                }
            }
        }
        isLoading && departures.isEmpty() -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        groups.isEmpty() -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No upcoming departures", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        else -> RouteCardList(
            groups = groups,
            now = now,
            onDepartureClick = onDepartureClick,
            modifier = Modifier.fillMaxSize(),
            showStopName = distinctPlaces > 1
        )
    }
}
