package space.snapp.waygo.ui.nearme

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import space.snapp.waygo.data.api.models.Departure
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
    onRequestPermission: () -> Unit,
    onDepartureClick: ((Departure) -> Unit)? = null
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
        // Location permission prompt — matches iOS locationPermissionPrompt
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(
                    Icons.Default.LocationOn,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Text("Location Access Needed", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Allow location access to find transit stops near you.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Button(onClick = onRequestPermission) { Text("Allow Location Access") }
            }
        }
        return
    }

    when {
        isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CircularProgressIndicator()
                Text("Finding stops near you…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.LocationOff, contentDescription = null, modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Location error", style = MaterialTheme.typography.titleMedium)
                Text(error ?: "", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                Button(onClick = { if (userLat != null && userLon != null) viewModel.fetchNearby(userLat, userLon) }) {
                    Text("Retry")
                }
            }
        }
        viewModel.availableTypes.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.DirectionsBus, contentDescription = null, modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("No stops found", style = MaterialTheme.typography.titleMedium)
                Text(
                    "No public transport stops found nearby.\nTry moving closer to a stop.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
        else -> {
            Column(Modifier.fillMaxSize()) {
                // Type tab bar — capsule buttons with icon + label matching iOS
                Row(
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    viewModel.availableTypes.forEach { type ->
                        TypeTabCapsule(
                            type = type,
                            selected = selectedType == type,
                            onClick = { viewModel.selectType(type) }
                        )
                    }
                }
                HorizontalDivider()

                selectedType?.let { type ->
                    val stops = viewModel.closestStops(type)
                    val depsVM = remember(type) { DeparturesViewModel() }

                    Column(Modifier.fillMaxSize()) {
                        // Stops header — matches iOS stopsHeader
                        if (stops.isNotEmpty()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    "Nearest ${type.label.lowercase()} stops".uppercase(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                stops.forEach { stop ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            type.icon(), contentDescription = null,
                                            modifier = Modifier.size(14.dp).padding(end = 0.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            stop.name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            modifier = Modifier.weight(1f)
                                        )
                                        stop.distanceLabel?.let { dist ->
                                            Text(
                                                dist,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                            HorizontalDivider()
                        }
                        DeparturesScreen(
                            viewModel = depsVM,
                            stops = stops,
                            onDepartureClick = onDepartureClick
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TypeTabCapsule(type: VehicleType, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Row(
        modifier = Modifier
            .clip(shape)
            .background(
                if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            type.icon(), contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = if (selected) MaterialTheme.colorScheme.onPrimary
                   else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            type.label,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            color = if (selected) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
