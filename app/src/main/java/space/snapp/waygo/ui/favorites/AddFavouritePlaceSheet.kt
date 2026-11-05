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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import space.snapp.waygo.data.api.models.AddressResult
import space.snapp.waygo.data.models.FavoritePlace

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddFavouritePlaceSheet(
    favoritesVM: FavoritesViewModel,
    existing: FavoritePlace? = null,
    /** A name to start with when adding ("Home" from the home screen's "Set home") */
    initialName: String = "",
    onDismiss: () -> Unit,
    onSave: (FavoritePlace) -> Unit
) {
    val scope = rememberCoroutineScope()

    var name by remember {
        mutableStateOf(existing?.name ?: initialName)
    }
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<AddressResult>>(emptyList()) }
    var selectedAddress by remember {
        mutableStateOf<AddressResult?>(
            existing?.let {
                AddressResult(placeId = -1L, displayName = it.displayName, lat = it.lat, lon = it.lon)
            }
        )
    }
    var isSearching by remember { mutableStateOf(false) }
    var searchJob by remember { mutableStateOf<Job?>(null) }

    fun search(q: String) {
        searchJob?.cancel()
        if (q.trim().length < 3) { results = emptyList(); return }
        searchJob = scope.launch {
            delay(350)
            isSearching = true
            results = favoritesVM.searchAddresses(q)
            isSearching = false
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
                if (existing != null) "Edit Favourite Place" else "Add Favourite Place",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                placeholder = { Text("e.g. Home, Work, Gym") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(16.dp))

            Text(
                "Address",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            if (selectedAddress != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Place,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            selectedAddress!!.shortName,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        if (selectedAddress!!.subtitle.isNotEmpty()) {
                            Text(
                                selectedAddress!!.subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    IconButton(onClick = {
                        selectedAddress = null
                        query = ""
                        results = emptyList()
                    }) {
                        Icon(Icons.Default.Close, contentDescription = "Clear address")
                    }
                }
            } else {
                OutlinedTextField(
                    value = query,
                    onValueChange = { q -> query = q; search(q) },
                    placeholder = { Text("Search address...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        when {
                            isSearching -> CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                            query.isNotEmpty() -> IconButton(onClick = {
                                query = ""
                                results = emptyList()
                            }) {
                                Icon(Icons.Default.Close, contentDescription = null)
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                results.take(6).forEach { addr ->
                    ListItem(
                        headlineContent = { Text(addr.shortName) },
                        supportingContent = if (addr.subtitle.isNotEmpty()) ({
                            Text(addr.subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }) else null,
                        leadingContent = { Icon(Icons.Default.Place, contentDescription = null) },
                        modifier = Modifier.clickable {
                            selectedAddress = addr
                            query = addr.shortName
                            results = emptyList()
                            if (name.isBlank()) name = addr.shortName
                        }
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            Button(
                onClick = {
                    val addr = selectedAddress ?: return@Button
                    onSave(
                        FavoritePlace(
                            id = existing?.id ?: java.util.UUID.randomUUID().toString(),
                            name = name.trim(),
                            displayName = addr.displayName,
                            lat = addr.lat,
                            lon = addr.lon
                        )
                    )
                },
                enabled = name.isNotBlank() && selectedAddress != null,
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
