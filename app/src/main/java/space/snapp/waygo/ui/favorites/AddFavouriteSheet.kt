package space.snapp.waygo.ui.favorites

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import space.snapp.waygo.data.api.models.Stop
import space.snapp.waygo.data.models.FavoriteDestination
import space.snapp.waygo.data.models.FavoritePlace
import space.snapp.waygo.data.models.FavoriteStop
import space.snapp.waygo.ui.components.icon

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddFavouriteSheet(
    stops: List<Stop>,
    existing: FavoriteStop? = null,
    favoritesVM: FavoritesViewModel? = null,
    savedPlaces: List<FavoritePlace> = emptyList(),
    userLat: Double? = null,
    userLon: Double? = null,
    // Pre-filled name, e.g. "Home" from the home-screen prompt
    initialName: String? = null,
    onDismiss: () -> Unit,
    onSave: (FavoriteStop) -> Unit
) {
    val scope = rememberCoroutineScope()

    var name by remember { mutableStateOf(existing?.name ?: initialName ?: stops.firstOrNull()?.name ?: "") }
    var editableStops by remember { mutableStateOf(stops.toMutableList() as List<Stop>) }
    var addStopQuery by remember { mutableStateOf("") }
    var addStopResults by remember { mutableStateOf<List<Stop>>(emptyList()) }

    // Destinations — migrate legacy single getOff to new list format on first open
    var destinations by remember {
        val initial: List<FavoriteDestination> = when {
            existing?.destinations?.isNotEmpty() == true -> existing.destinations.orEmpty()
            existing?.getOffStopId != null -> listOf(
                FavoriteDestination(
                    stopIds = existing.getOffStopId.split(",").map { it.trim() }.filter { it.isNotEmpty() },
                    displayName = existing.getOffStopName ?: "Destination"
                )
            )
            else -> emptyList()
        }
        mutableStateOf(initial)
    }
    var destQuery by remember { mutableStateOf("") }
    var destResults by remember { mutableStateOf<List<Stop>>(emptyList()) }
    var showPlacePicker by remember { mutableStateOf(false) }
    var isResolvingPlace by remember { mutableStateOf(false) }

    fun searchStops(query: String, onResult: (List<Stop>) -> Unit) {
        if (favoritesVM == null || query.length < 2) { onResult(emptyList()); return }
        scope.launch { onResult(favoritesVM.searchStops(query, userLat, userLon)) }
    }

    fun addOriginStop(stop: Stop) {
        val parent = stop.parentStation?.takeIf { it.isNotEmpty() }
        val toAdd = if (parent != null) {
            val siblings = addStopResults.filter { it.parentStation == parent }
            if (siblings.size > 1) siblings else listOf(stop)
        } else listOf(stop)
        editableStops = editableStops + toAdd.filter { s -> editableStops.none { it.id == s.id } }
        addStopQuery = ""
        addStopResults = emptyList()
    }

    fun addDestinationStop(stop: Stop) {
        val parent = stop.parentStation?.takeIf { it.isNotEmpty() }
        val toAdd = if (parent != null) {
            val siblings = destResults.filter { it.parentStation == parent }
            if (siblings.size > 1) siblings else listOf(stop)
        } else listOf(stop)
        val stopIds = toAdd.map { it.id }
        val displayName = if (parent != null) stop.parentStationName ?: stop.name else stop.name
        destinations = destinations + FavoriteDestination(stopIds = stopIds, displayName = displayName)
        destQuery = ""
        destResults = emptyList()
    }

    fun resolveAndAddPlace(place: FavoritePlace) {
        showPlacePicker = false
        isResolvingPlace = true
        scope.launch {
            val nearbyStops = favoritesVM?.fetchNearbyStops(place.lat, place.lon, 200) ?: emptyList()
            isResolvingPlace = false
            if (nearbyStops.isNotEmpty()) {
                destinations = destinations + FavoriteDestination(
                    stopIds = nearbyStops.map { it.id },
                    displayName = "near ${place.name}",
                    fromPlaceName = place.name
                )
            }
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .navigationBarsPadding()
        ) {
            Text(
                if (existing != null) "Edit Favourite" else "Save Favourite",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Name
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                placeholder = { Text("e.g. Home to Work") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(16.dp))

            // --- Origin stops ---
            Text(
                "Get On At",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            editableStops.forEach { stop ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        stop.primaryVehicleType.icon(),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stop.name, modifier = Modifier.weight(1f))
                    IconButton(onClick = { editableStops = editableStops.filter { it.id != stop.id } }) {
                        Icon(Icons.Default.RemoveCircle, contentDescription = "Remove", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }

            OutlinedTextField(
                value = addStopQuery,
                onValueChange = { q -> addStopQuery = q; searchStops(q) { addStopResults = it } },
                placeholder = { Text("Add a stop...") },
                leadingIcon = { Icon(Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                trailingIcon = if (addStopQuery.isNotEmpty()) ({
                    IconButton(onClick = { addStopQuery = ""; addStopResults = emptyList() }) {
                        Icon(Icons.Default.Close, contentDescription = null)
                    }
                }) else null,
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )
            addStopResults.take(5).forEach { stop ->
                ListItem(
                    headlineContent = { Text(stop.name) },
                    leadingContent = { Icon(stop.primaryVehicleType.icon(), contentDescription = null) },
                    modifier = Modifier.clickable { addOriginStop(stop) }
                )
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            // --- Destinations ---
            Text(
                "Destinations (optional)",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            Text(
                "Only services calling at any destination will be shown.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            // Added destinations list
            destinations.forEach { dest ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Place,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(dest.displayName, style = MaterialTheme.typography.bodyMedium)
                        dest.fromPlaceName?.let {
                            Text(
                                "Stops near $it",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    IconButton(onClick = { destinations = destinations.filter { it !== dest } }) {
                        Icon(Icons.Default.RemoveCircle, contentDescription = "Remove", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }

            // Place resolution loading indicator
            if (isResolvingPlace) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Finding nearby stops...", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // Add destination stop search
            OutlinedTextField(
                value = destQuery,
                onValueChange = { q -> destQuery = q; searchStops(q) { destResults = it } },
                placeholder = { Text("Add destination stop...") },
                leadingIcon = { Icon(Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                trailingIcon = if (destQuery.isNotEmpty()) ({
                    IconButton(onClick = { destQuery = ""; destResults = emptyList() }) {
                        Icon(Icons.Default.Close, contentDescription = null)
                    }
                }) else null,
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )
            destResults.take(5).forEach { stop ->
                ListItem(
                    headlineContent = { Text(stop.name) },
                    leadingContent = { Icon(stop.primaryVehicleType.icon(), contentDescription = null) },
                    modifier = Modifier.clickable { addDestinationStop(stop) }
                )
            }

            // Add from saved places button
            if (savedPlaces.isNotEmpty()) {
                TextButton(
                    onClick = { showPlacePicker = true },
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                ) {
                    Icon(Icons.Default.Place, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Add from saved places")
                }
            }

            Spacer(Modifier.height(16.dp))

            // Save / Cancel
            Button(
                onClick = {
                    onSave(
                        FavoriteStop(
                            id = existing?.id ?: java.util.UUID.randomUUID().toString(),
                            name = name.trim(),
                            stops = editableStops,
                            destinations = destinations
                        )
                    )
                },
                enabled = name.isNotBlank() && editableStops.isNotEmpty(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save")
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("Cancel")
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    // Place picker dialog
    if (showPlacePicker) {
        AlertDialog(
            onDismissRequest = { showPlacePicker = false },
            title = { Text("Add from saved places") },
            text = {
                Column {
                    savedPlaces.forEach { place ->
                        ListItem(
                            headlineContent = { Text(place.name) },
                            supportingContent = {
                                Text(
                                    place.displayName.split(",").take(2).joinToString(",").trim(),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            leadingContent = { Icon(Icons.Default.Place, contentDescription = null) },
                            modifier = Modifier.clickable { resolveAndAddPlace(place) }
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showPlacePicker = false }) { Text("Cancel") }
            }
        )
    }
}
