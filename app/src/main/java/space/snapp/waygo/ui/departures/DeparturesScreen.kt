package space.snapp.waygo.ui.departures

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import space.snapp.waygo.data.api.models.Departure
import space.snapp.waygo.data.api.models.Stop
import space.snapp.waygo.ui.alerts.AlertBannerArea
import space.snapp.waygo.ui.alerts.rememberServiceAlerts
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
    // Stop ids alone are enough: the API adds every route serving them, so a closed
    // station with no departures still shows its track-work alert
    val alerts = rememberServiceAlerts(routeIds = departures.map { it.routeId }, stopIds = stops.map { it.id })

    when {
        groups.isNotEmpty() -> RouteCardList(
            groups = groups,
            now = now,
            onDepartureClick = onDepartureClick,
            modifier = Modifier.fillMaxSize(),
            showStopName = distinctPlaces > 1,
            disruptedRouteIds = alerts.disruptedRouteIds,
            // Banner scrolls with the cards
            header = { AlertBannerArea(alerts) }
        )
        // No cards (loading, error or nothing running): the banner and the state message share
        // one scrolling column, so an alert explains "no trains" and stays readable expanded
        else -> Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AlertBannerArea(alerts, Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
            Spacer(Modifier.height(if (alerts.visible.isEmpty()) 120.dp else 32.dp))
            when {
                error != null && departures.isEmpty() -> {
                    Text("Failed to load departures", style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { viewModel.startAutoRefresh(stops, getOffStopId) }) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Retry")
                    }
                }
                isLoading && departures.isEmpty() -> CircularProgressIndicator()
                else -> Text("No upcoming departures", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
