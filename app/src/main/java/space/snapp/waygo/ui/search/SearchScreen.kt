package space.snapp.waygo.ui.search

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import space.snapp.waygo.data.api.models.Departure
import space.snapp.waygo.data.api.models.Stop
import space.snapp.waygo.data.models.FavoriteStop
import space.snapp.waygo.ui.components.StopRow
import space.snapp.waygo.ui.components.icon
import space.snapp.waygo.ui.departures.DeparturesScreen
import space.snapp.waygo.ui.departures.DeparturesViewModel
import space.snapp.waygo.ui.favorites.AddFavouriteSheet
import space.snapp.waygo.ui.favorites.FavoritesViewModel
import space.snapp.waygo.ui.map.NearbyStopsMapView
import space.snapp.waygo.ui.tripdetail.TripDetailArgs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    departuresVM: DeparturesViewModel,
    favoritesVM: FavoritesViewModel,
    userLat: Double?,
    userLon: Double?,
    onTripSelected: (TripDetailArgs) -> Unit = {}
) {
    val query by viewModel.query.collectAsState()
    val results by viewModel.results.collectAsState()
    val selectedStops by viewModel.selectedStops.collectAsState()
    val stopDepartures by viewModel.stopDepartures.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val hasSearched by viewModel.hasSearched.collectAsState()

    var showSaveSheet by remember { mutableStateOf(false) }
    var stopsForSaving by remember { mutableStateOf<List<Stop>>(emptyList()) }

    // White — high contrast against dark map
    val searchBarBg = Color.White
    val searchBarText = Color(0xFF1A1A1A)
    val searchBarHint = Color(0xFF888888)
    val searchBarIcon = Color(0xFF555555)

    Box(modifier = Modifier.fillMaxSize()) {

        // ── Base layer: map always visible ──────────────────────────────
        NearbyStopsMapView(
            userLat = userLat,
            userLon = userLon,
            onAddStops = { stops -> stops.forEach { viewModel.addStop(it) } },
            onDepartureClick = { dep ->
                onTripSelected(
                    TripDetailArgs(
                        tripId = dep.tripId,
                        routeId = dep.routeId,
                        directionId = dep.directionId,
                        routeShortName = dep.routeNumber,
                        headsign = dep.headsign
                    )
                )
            }
        )

        // ── Overlay UI ──────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {

            // Floating search bar
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = searchBarBg,
                shadowElevation = 10.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = viewModel::onQueryChange,
                    placeholder = {
                        Text("Stop name, route, suburb...", color = searchBarHint)
                    },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null, tint = searchBarIcon)
                    },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { viewModel.onQueryChange("") }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear", tint = searchBarIcon)
                            }
                        }
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = Color.Transparent,
                        focusedBorderColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedContainerColor = Color.Transparent,
                        unfocusedTextColor = searchBarText,
                        focusedTextColor = searchBarText,
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Selected stops chips
            if (selectedStops.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = searchBarBg.copy(alpha = 0.95f),
                    shadowElevation = 6.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        selectedStops.forEach { stop ->
                            InputChip(
                                selected = true,
                                onClick = {},
                                label = { Text(stop.name, maxLines = 1, color = searchBarText) },
                                leadingIcon = {
                                    Icon(
                                        stop.primaryVehicleType.icon(),
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                trailingIcon = {
                                    IconButton(
                                        onClick = { viewModel.removeStop(stop) },
                                        modifier = Modifier.size(18.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "Remove",
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                },
                                colors = InputChipDefaults.inputChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                )
                            )
                        }
                        AssistChip(
                            onClick = {
                                stopsForSaving = viewModel.expandedStops(selectedStops, results.stops)
                                showSaveSheet = true
                            },
                            label = { Text("Save") },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.FavoriteBorder,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        )
                        AssistChip(
                            onClick = { viewModel.clearAll() },
                            label = { Text("Clear") },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        )
                    }
                }
            }

            // Content panel — sits above the map
            val trimmed = query.trim()
            when {
                trimmed.length >= 2 -> {
                    Spacer(Modifier.height(6.dp))
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface,
                        shadowElevation = 10.dp
                    ) {
                        if (isSearching && results.stops.isEmpty() && results.routes.isEmpty()) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        } else if (hasSearched && results.stops.isEmpty() && results.routes.isEmpty() && results.suburbs.isEmpty()) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    "No results for \"$trimmed\"",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            LazyColumn(modifier = Modifier.fillMaxSize()) {
                                if (results.stops.isNotEmpty()) {
                                    item {
                                        Text(
                                            "Stops",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                        )
                                    }
                                    items(results.stops, key = { it.id }) { stop ->
                                        StopRow(
                                            stop = stop,
                                            departures = stopDepartures[stop.id] ?: emptyList(),
                                            isSelected = viewModel.isSelected(stop),
                                            onClick = {
                                                if (viewModel.isSelected(stop)) viewModel.removeStop(stop)
                                                else viewModel.addStop(stop)
                                            }
                                        )
                                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                                    }
                                }
                                if (results.routes.isNotEmpty()) {
                                    item {
                                        HorizontalDivider()
                                        Text(
                                            "Routes",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                        )
                                    }
                                    items(results.routes, key = { it.id }) { route ->
                                        ListItem(
                                            headlineContent = {
                                                Text("→ ${route.tripHeadsign ?: route.routeLongName}")
                                            },
                                            supportingContent = { Text(route.routeLongName) },
                                            leadingContent = {
                                                Surface(
                                                    color = route.routeColor?.let { parseColor(it) }
                                                        ?: MaterialTheme.colorScheme.primary,
                                                    shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp)
                                                ) {
                                                    Text(
                                                        route.routeShortName,
                                                        style = MaterialTheme.typography.titleSmall.copy(
                                                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                                                        ),
                                                        color = route.routeTextColor?.let { parseColor(it) }
                                                            ?: MaterialTheme.colorScheme.onPrimary,
                                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                                    )
                                                }
                                            },
                                            modifier = Modifier.clickable {
                                                onTripSelected(
                                                    TripDetailArgs(
                                                        tripId = null,
                                                        routeId = route.routeId,
                                                        directionId = route.directionId ?: 0,
                                                        routeShortName = route.routeShortName,
                                                        headsign = route.tripHeadsign ?: route.routeLongName
                                                    )
                                                )
                                            }
                                        )
                                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                                    }
                                }
                            }
                        }
                    }
                }

                selectedStops.isNotEmpty() -> {
                    Spacer(Modifier.height(6.dp))
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                        color = MaterialTheme.colorScheme.surface,
                        shadowElevation = 10.dp
                    ) {
                        DeparturesScreen(
                            viewModel = departuresVM,
                            stops = selectedStops,
                            onDepartureClick = { dep ->
                                onTripSelected(
                                    TripDetailArgs(
                                        tripId = dep.tripId,
                                        routeId = dep.routeId,
                                        directionId = dep.directionId,
                                        routeShortName = dep.routeNumber,
                                        headsign = dep.headsign
                                    )
                                )
                            }
                        )
                    }
                }

                else -> { /* map is the content — nothing needed */ }
            }
        }
    }

    if (showSaveSheet) {
        AddFavouriteSheet(
            stops = stopsForSaving,
            favoritesVM = favoritesVM,
            userLat = userLat,
            userLon = userLon,
            onDismiss = { showSaveSheet = false },
            onSave = { fav ->
                favoritesVM.saveFavorite(fav)
                showSaveSheet = false
            }
        )
    }
}

private fun parseColor(hex: String): androidx.compose.ui.graphics.Color =
    runCatching {
        val color = android.graphics.Color.parseColor("#${hex.trimStart('#')}")
        androidx.compose.ui.graphics.Color(color)
    }.getOrDefault(androidx.compose.ui.graphics.Color.Blue)
