package space.snapp.waygo.ui.nearme

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import space.snapp.waygo.data.api.models.VehicleType
import space.snapp.waygo.ui.components.icon
import space.snapp.waygo.ui.departures.DeparturesScreen
import space.snapp.waygo.ui.departures.DeparturesViewModel

@Composable
fun NearMeScreen(
    viewModel: NearMeViewModel,
    hasLocationPermission: Boolean,
    userLat: Double?,
    userLon: Double?,
    onRequestPermission: () -> Unit
) {
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val selectedType by viewModel.selectedType.collectAsState()

    LaunchedEffect(hasLocationPermission, userLat, userLon) {
        if (hasLocationPermission && userLat != null && userLon != null) {
            viewModel.fetchNearby(userLat, userLon)
        }
    }

    if (!hasLocationPermission) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                Icon(Icons.Default.LocationOff, contentDescription = null,
                    modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(16.dp))
                Text("Location access needed", style = MaterialTheme.typography.titleMedium)
                Text("WayGo needs your location to find nearby stops.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp))
                Button(onClick = onRequestPermission) { Text("Allow Location") }
            }
        }
        return
    }

    when {
        isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Spacer(Modifier.height(8.dp))
                Text("Finding stops near you...", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.LocationOff, contentDescription = null, modifier = Modifier.size(48.dp))
                Spacer(Modifier.height(8.dp))
                Text(error ?: "Location error")
                Spacer(Modifier.height(8.dp))
                Button(onClick = { if (userLat != null && userLon != null) viewModel.fetchNearby(userLat, userLon) }) {
                    Text("Retry")
                }
            }
        }
        viewModel.availableTypes.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No stops found nearby", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        else -> {
            Column(Modifier.fillMaxSize()) {
                // Type tab bar
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(viewModel.availableTypes) { type ->
                        FilterChip(
                            selected = selectedType == type,
                            onClick = { viewModel.selectType(type) },
                            label = { Text(type.label) },
                            leadingIcon = {
                                Icon(type.icon(), contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                        )
                    }
                }
                HorizontalDivider()

                selectedType?.let { type ->
                    val stops = viewModel.closestStops(type)
                    val depsVM = remember(type) { DeparturesViewModel() }

                    Column(Modifier.fillMaxSize()) {
                        // Stops header
                        if (stops.isNotEmpty()) {
                            Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
                                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                                    Text(
                                        "Nearest ${type.label.lowercase()} stops",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    stops.forEach { stop ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(type.icon(), contentDescription = null,
                                                modifier = Modifier.size(14.dp),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Spacer(Modifier.width(8.dp))
                                            Text(stop.name, style = MaterialTheme.typography.bodyMedium,
                                                modifier = Modifier.weight(1f))
                                            stop.distanceLabel?.let { dist ->
                                                Text(dist, style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                    }
                                }
                            }
                            HorizontalDivider()
                        }
                        DeparturesScreen(viewModel = depsVM, stops = stops)
                    }
                }
            }
        }
    }
}
