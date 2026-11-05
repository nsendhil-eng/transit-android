package space.snapp.waygo.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.animation.AnimatedVisibility
import space.snapp.waygo.ui.alerts.AlertsPage
import space.snapp.waygo.ui.alerts.HomeAlertControl
import space.snapp.waygo.ui.alerts.NetworkAlertsViewModel
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import space.snapp.waygo.data.api.models.Departure
import space.snapp.waygo.data.api.models.Itinerary
import space.snapp.waygo.data.api.models.Stop
import space.snapp.waygo.data.models.FavoriteStop
import space.snapp.waygo.ui.components.StopRow
import space.snapp.waygo.ui.components.icon
import space.snapp.waygo.ui.departures.DeparturesScreen
import space.snapp.waygo.ui.departures.DeparturesViewModel
import space.snapp.waygo.ui.favorites.AddFavouriteSheet
import space.snapp.waygo.ui.favorites.FavoritesViewModel
import space.snapp.waygo.ui.favorites.SaveStopsPrompt
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import kotlinx.coroutines.delay
import androidx.compose.ui.platform.LocalDensity
import space.snapp.waygo.ui.map.NearbyStopsMapView
import space.snapp.waygo.ui.plan.ItineraryDetailScreen
import space.snapp.waygo.ui.plan.PlanCard
import space.snapp.waygo.ui.plan.PlanViewModel
import space.snapp.waygo.ui.tripdetail.TripDetailArgs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    departuresVM: DeparturesViewModel,
    favoritesVM: FavoritesViewModel,
    planViewModel: PlanViewModel,
    userLat: Double?,
    userLon: Double?,
    userAccuracyM: Float? = null,
    hasLocationPermission: Boolean = true,
    onRequestLocation: () -> Unit = {},
    networkAlertsVM: NetworkAlertsViewModel,
    onTripSelected: (TripDetailArgs) -> Unit = {}
) {
    val query by viewModel.query.collectAsState()
    val results by viewModel.results.collectAsState()
    val selectedStops by viewModel.selectedStops.collectAsState()
    val stopDepartures by viewModel.stopDepartures.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val hasSearched by viewModel.hasSearched.collectAsState()

    val fromSelected by planViewModel.fromSelected.collectAsState()
    val toSelected by planViewModel.toSelected.collectAsState()

    var showSaveSheet by remember { mutableStateOf(false) }
    var stopsForSaving by remember { mutableStateOf<List<Stop>>(emptyList()) }
    var saveName by remember { mutableStateOf<String?>(null) }
    val favorites by favoritesVM.favorites.collectAsState()
    // The save prompt opens as a card until closed or something is saved, then stays as a pill
    var savePromptClosed by rememberSaveable { mutableStateOf(false) }
    val savePromptExpanded = favorites.isEmpty() && !savePromptClosed

    // Search is a round button until tapped; the bar stays while there's a query or chosen stops
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    val searchBarShown = searchOpen || query.isNotEmpty() || selectedStops.isNotEmpty()
    val searchFocus = remember { FocusRequester() }
    LaunchedEffect(searchOpen) { if (searchOpen && query.isEmpty()) runCatching { searchFocus.requestFocus() } }
    // Left alone for a minute (no typing, nothing chosen): fold back into the button
    LaunchedEffect(searchOpen, query, selectedStops.isEmpty()) {
        if (searchOpen && selectedStops.isEmpty()) {
            delay(SEARCH_IDLE_MS)
            viewModel.onQueryChange("")
            searchOpen = false
        }
    }
    // Done with the chosen stops: fold back too
    var hadStops by remember { mutableStateOf(false) }
    LaunchedEffect(selectedStops.isEmpty()) {
        if (selectedStops.isEmpty() && hadStops) searchOpen = false
        hadStops = selectedStops.isNotEmpty()
    }

    val density = LocalDensity.current
    var inPlanMode by remember { mutableStateOf(false) }
    var selectedItinerary by remember { mutableStateOf<Itinerary?>(null) }
    var showAllAlerts by remember { mutableStateOf(false) }
    val networkAlerts by networkAlertsVM.alerts.collectAsState()
    val alertBannerCollapsed by networkAlertsVM.bannerCollapsed.collectAsState()
    LaunchedEffect(userLat, userLon) { networkAlertsVM.onLocation(userLat, userLon) }
    // The alert banner shows briefly, then shrinks to the ⚠ button beside search
    LaunchedEffect(networkAlerts.isNotEmpty(), alertBannerCollapsed) {
        if (networkAlerts.isNotEmpty() && !alertBannerCollapsed) {
            delay(ALERT_BANNER_MS)
            networkAlertsVM.collapseBanner()
        }
    }

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
            userAccuracyM = userAccuracyM,
            hasLocationPermission = hasLocationPermission,
            onRequestLocation = onRequestLocation,
            // The save prompt sits at the end of the map's bottom panel
            sheetFooter = {
                SaveStopsPrompt(
                    expanded = savePromptExpanded,
                    onAdd = { name ->
                        stopsForSaving = emptyList()
                        saveName = name
                        showSaveSheet = true
                    },
                    onCollapse = { savePromptClosed = true }
                )
            },
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
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {

            // Back clears the search, then the selected stops, then folds the bar back into
            // the round button, before leaving the app (same precedence as the content panel
            // below: results win over selected stops)
            BackHandler(enabled = !inPlanMode && searchBarShown) {
                when {
                    query.isNotEmpty() -> viewModel.onQueryChange("")
                    selectedStops.isNotEmpty() -> viewModel.clearAll()
                    else -> searchOpen = false
                }
            }

            // Floating search bar OR plan mode card
            if (inPlanMode) {
                // Back closes plan mode instead of leaving the app (itinerary detail has its own handler)
                BackHandler(enabled = selectedItinerary == null) {
                    inPlanMode = false
                    planViewModel.reset()
                }
                PlanCard(
                    viewModel = planViewModel,
                    userLat = userLat,
                    userLon = userLon,
                    onClose = { inPlanMode = false; planViewModel.reset() },
                    onItinerarySelect = { selectedItinerary = it }
                )
            } else if (searchBarShown) {
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
                            // Folds the bar back into the round button
                            IconButton(onClick = {
                                viewModel.onQueryChange("")
                                if (selectedStops.isEmpty()) searchOpen = false
                            }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close search", tint = searchBarIcon)
                            }
                        },
                        trailingIcon = {
                            if (query.isNotEmpty()) {
                                IconButton(onClick = { viewModel.onQueryChange("") }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear", tint = searchBarIcon)
                                }
                            } else {
                                IconButton(onClick = { inPlanMode = true }) {
                                    Icon(
                                        Icons.Default.Directions,
                                        contentDescription = "Plan a journey",
                                        tint = searchBarIcon
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().focusRequester(searchFocus),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = Color.Transparent,
                            focusedBorderColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedContainerColor = Color.Transparent,
                            unfocusedTextColor = searchBarText,
                            focusedTextColor = searchBarText,
                        ),
                    )
                }
            }

            // Selected stops chips (hidden in plan mode)
            if (!inPlanMode && selectedStops.isNotEmpty()) {
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
                                saveName = null
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

            // Content panel — sits above the map (hidden in plan mode, PlanCard manages its own content)
            val trimmed = query.trim()
            if (!inPlanMode) when {
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
                                                // A route was chosen: the search is done
                                                viewModel.onQueryChange("")
                                                searchOpen = false
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

        // While the map is the content: the round search button, with the alert beside it
        // (a banner for a few seconds, then the ⚠ button)
        if (!inPlanMode && !searchBarShown) {
            Row(
                verticalAlignment = Alignment.Top,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(start = 12.dp, end = 6.dp, top = 8.dp)
            ) {
                Surface(
                    onClick = { searchOpen = true },
                    shape = CircleShape,
                    color = Color(0xFF22272E),
                    shadowElevation = 10.dp,
                    modifier = Modifier.padding(top = 6.dp).size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Search, contentDescription = "Search stops and routes", tint = Color.White)
                    }
                }
                Spacer(Modifier.width(8.dp))
                HomeAlertControl(
                    alerts = networkAlerts,
                    collapsed = alertBannerCollapsed,
                    onCollapse = networkAlertsVM::collapseBanner,
                    onOpenAll = { networkAlertsVM.collapseBanner(); showAllAlerts = true },
                    modifier = Modifier.weight(1f, fill = false)
                )
            }
        }

        // All alerts — full-screen overlay
        AnimatedVisibility(
            visible = showAllAlerts,
            enter = slideInVertically(initialOffsetY = { it / 6 }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it / 6 }) + fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(Modifier.background(MaterialTheme.colorScheme.background).statusBarsPadding()) {
                AlertsPage(alerts = networkAlerts, onBack = { showAllAlerts = false })
            }
        }

        // Itinerary detail — full-screen overlay inside the same Box so it covers the map
        AnimatedVisibility(
            visible = selectedItinerary != null,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            selectedItinerary?.let { itinerary ->
                Box(Modifier.background(MaterialTheme.colorScheme.background).statusBarsPadding()) {
                ItineraryDetailScreen(
                    itinerary = itinerary,
                    fromName = fromSelected?.shortName ?: "",
                    toName = toSelected?.shortName ?: "",
                    onBack = { selectedItinerary = null }
                )
                }
            }
        }
    }

    if (showSaveSheet) {
        AddFavouriteSheet(
            stops = stopsForSaving,
            favoritesVM = favoritesVM,
            userLat = userLat,
            userLon = userLon,
            initialName = saveName,
            onDismiss = { showSaveSheet = false },
            onSave = { fav ->
                favoritesVM.saveFavorite(fav)
                showSaveSheet = false
            }
        )
    }
}

/** Search bar folds back into its button after this long untouched */
private const val SEARCH_IDLE_MS = 60_000L
/** The alert banner shrinks to its ⚠ button after this long */
private const val ALERT_BANNER_MS = 6_000L

private fun parseColor(hex: String): androidx.compose.ui.graphics.Color =
    runCatching {
        val color = android.graphics.Color.parseColor("#${hex.trimStart('#')}")
        androidx.compose.ui.graphics.Color(color)
    }.getOrDefault(androidx.compose.ui.graphics.Color.Blue)
