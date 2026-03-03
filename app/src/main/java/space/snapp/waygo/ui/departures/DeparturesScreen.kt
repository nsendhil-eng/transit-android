package space.snapp.waygo.ui.departures

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import space.snapp.waygo.data.api.models.Stop

@Composable
fun DeparturesScreen(
    viewModel: DeparturesViewModel,
    stops: List<Stop>,
    getOffStopId: String? = null
) {
    val departures by viewModel.departures.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val routeFilter by viewModel.routeFilter.collectAsState()

    LaunchedEffect(stops, getOffStopId) {
        viewModel.startAutoRefresh(stops, getOffStopId)
    }
    DisposableEffect(Unit) { onDispose { viewModel.stopAutoRefresh() } }

    Column(modifier = Modifier.fillMaxSize()) {
        // Route filter — capsule buttons matching iOS RouteFilterBar
        val routes = viewModel.availableRoutes
        if (routes.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // "All" capsule
                RouteFilterCapsule(
                    label = "All",
                    active = routeFilter == null,
                    onClick = { viewModel.setRouteFilter(null) }
                )
                routes.forEach { route ->
                    RouteFilterCapsule(
                        label = route,
                        active = routeFilter == route,
                        onClick = { viewModel.setRouteFilter(if (routeFilter == route) null else route) }
                    )
                }
            }
            HorizontalDivider()
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
            departures.isEmpty() && !isLoading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No departures found", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            else -> {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    val filtered = viewModel.filteredDepartures
                    items(filtered, key = { it.id }) { dep ->
                        space.snapp.waygo.ui.components.DepartureRow(dep)
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun RouteFilterCapsule(label: String, active: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Box(
        modifier = Modifier
            .clip(shape)
            .background(
                if (active) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = if (active) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
