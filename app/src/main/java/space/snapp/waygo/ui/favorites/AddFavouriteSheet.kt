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
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import space.snapp.waygo.data.api.models.Stop
import space.snapp.waygo.data.models.FavoriteStop
import space.snapp.waygo.ui.components.icon

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddFavouriteSheet(
    stops: List<Stop>,
    existing: FavoriteStop? = null,
    favoritesVM: FavoritesViewModel? = null,
    userLat: Double? = null,
    userLon: Double? = null,
    onDismiss: () -> Unit,
    onSave: (FavoriteStop) -> Unit
) {
    val scope = rememberCoroutineScope()

    var name by remember { mutableStateOf(existing?.name ?: stops.firstOrNull()?.name ?: "") }
    var editableStops by remember { mutableStateOf(stops.toMutableList() as List<Stop>) }
    var addStopQuery by remember { mutableStateOf("") }
    var addStopResults by remember { mutableStateOf<List<Stop>>(emptyList()) }
    var getOffStopId by remember { mutableStateOf(existing?.getOffStopId) }
    var getOffStopName by remember { mutableStateOf(existing?.getOffStopName) }
    var getOffQuery by remember { mutableStateOf("") }
    var getOffResults by remember { mutableStateOf<List<Stop>>(emptyList()) }

    fun searchStops(query: String, onResult: (List<Stop>) -> Unit) {
        if (favoritesVM == null || query.length < 2) { onResult(emptyList()); return }
        scope.launch {
            onResult(favoritesVM.searchStops(query, userLat, userLon))
        }
    }

    fun addStop(stop: Stop) {
        val parent = stop.parentStation?.takeIf { it.isNotEmpty() }
        val toAdd = if (parent != null) {
            val siblings = addStopResults.filter { it.parentStation == parent }
            if (siblings.size > 1) siblings else listOf(stop)
        } else listOf(stop)
        editableStops = (editableStops + toAdd.filter { s -> editableStops.none { it.id == s.id } })
        addStopQuery = ""
        addStopResults = emptyList()
    }

    fun selectGetOff(stop: Stop) {
        val parent = stop.parentStation?.takeIf { it.isNotEmpty() }
        if (parent != null) {
            val siblings = getOffResults.filter { it.parentStation == parent }
            if (siblings.size > 1) {
                getOffStopId = siblings.joinToString(",") { it.id }
                getOffStopName = stop.parentStationName ?: stop.name
                getOffQuery = ""; getOffResults = emptyList(); return
            }
        }
        getOffStopId = stop.id; getOffStopName = stop.name
        getOffQuery = ""; getOffResults = emptyList()
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

            // Get On At
            Text("Get On At", style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp))

            editableStops.forEach { stop ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(stop.primaryVehicleType.icon(), contentDescription = null,
                        modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text(stop.name, modifier = Modifier.weight(1f))
                    IconButton(onClick = { editableStops = editableStops.filter { it.id != stop.id } }) {
                        Icon(Icons.Default.RemoveCircle, contentDescription = "Remove", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }

            // Add stop search
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
                    modifier = Modifier.clickable { addStop(stop) }
                )
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            // Get Off At
            Text("Get Off At (optional)", style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp))

            if (getOffStopName != null) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Place, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text(getOffStopName!!, modifier = Modifier.weight(1f))
                    IconButton(onClick = { getOffStopId = null; getOffStopName = null }) {
                        Icon(Icons.Default.Close, contentDescription = "Clear")
                    }
                }
            } else {
                OutlinedTextField(
                    value = getOffQuery,
                    onValueChange = { q -> getOffQuery = q; searchStops(q) { getOffResults = it } },
                    placeholder = { Text("Destination stop...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = if (getOffQuery.isNotEmpty()) ({
                        IconButton(onClick = { getOffQuery = ""; getOffResults = emptyList() }) {
                            Icon(Icons.Default.Close, contentDescription = null)
                        }
                    }) else null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                getOffResults.take(5).forEach { stop ->
                    ListItem(
                        headlineContent = { Text(stop.name) },
                        leadingContent = { Icon(stop.primaryVehicleType.icon(), contentDescription = null) },
                        modifier = Modifier.clickable { selectGetOff(stop) }
                    )
                }
            }

            Text(
                "Only services that call at this stop will be shown",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
            )

            // Save button
            Button(
                onClick = {
                    val fav = FavoriteStop(
                        id = existing?.id ?: java.util.UUID.randomUUID().toString(),
                        name = name.trim(),
                        stops = editableStops,
                        getOffStopId = getOffStopId,
                        getOffStopName = getOffStopName
                    )
                    onSave(fav)
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
}
