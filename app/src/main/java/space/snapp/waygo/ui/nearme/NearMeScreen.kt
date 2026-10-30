package space.snapp.waygo.ui.nearme

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import space.snapp.waygo.data.api.models.Departure
import space.snapp.waygo.ui.routecards.ModeFilterRow
import space.snapp.waygo.ui.routecards.RouteCardList
import space.snapp.waygo.ui.routecards.availableModes
import space.snapp.waygo.ui.routecards.buildRouteGroups
import space.snapp.waygo.ui.routecards.rememberNow

@Composable
fun NearMeScreen(
    viewModel: NearMeViewModel,
    hasLocationPermission: Boolean,
    userLat: Double?,
    userLon: Double?,
    onRequestPermission: () -> Unit,
    onDepartureClick: ((Departure) -> Unit)? = null
) {
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val stops by viewModel.stops.collectAsState()
    val departures by viewModel.departures.collectAsState()
    val modeFilter by viewModel.modeFilter.collectAsState()

    LaunchedEffect(hasLocationPermission, userLat, userLon) {
        if (hasLocationPermission && userLat != null && userLon != null) {
            viewModel.onLocation(userLat, userLon)
        }
    }
    // Only poll departures while this tab is on screen
    DisposableEffect(Unit) {
        viewModel.resume()
        onDispose { viewModel.pause() }
    }

    val now = rememberNow()

    if (!hasLocationPermission) {
        // Location permission prompt — matches iOS locationPermissionPrompt
        CenteredMessage(
            icon = Icons.Default.LocationOn,
            title = "Location Access Needed",
            body = "Allow location access to find transit stops near you.",
            action = "Allow Location Access" to onRequestPermission
        )
        return
    }

    val modes = remember(departures) { availableModes(departures) }
    val groups = remember(departures, stops, modeFilter, now) {
        buildRouteGroups(departures, stops, modeFilter, now)
    }

    when {
        isLoading && departures.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CircularProgressIndicator()
                Text("Finding routes near you…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        error != null && departures.isEmpty() -> CenteredMessage(
            icon = Icons.Default.LocationOff,
            title = "Couldn't load nearby routes",
            body = error ?: "",
            action = "Retry" to viewModel::retry
        )
        departures.isEmpty() -> CenteredMessage(
            icon = Icons.Default.DirectionsBus,
            title = "No departures nearby",
            body = "No upcoming services from stops within 500 m.\nTry moving closer to a stop."
        )
        else -> Column(Modifier.fillMaxSize()) {
            if (modes.size > 1) {
                ModeFilterRow(modes = modes, selected = modeFilter, onSelect = viewModel::setModeFilter)
            }
            RouteCardList(groups, now, onDepartureClick, Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun CenteredMessage(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    body: String,
    action: Pair<String, () -> Unit>? = null
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            action?.let { (label, onClick) -> Button(onClick = onClick) { Text(label) } }
        }
    }
}
