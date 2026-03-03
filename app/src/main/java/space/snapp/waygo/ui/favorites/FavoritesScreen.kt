package space.snapp.waygo.ui.favorites

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import space.snapp.waygo.data.models.FavoriteStop
import space.snapp.waygo.ui.departures.DeparturesScreen
import space.snapp.waygo.ui.departures.DeparturesViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(
    viewModel: FavoritesViewModel,
    userLat: Double?,
    userLon: Double?
) {
    val favorites by viewModel.favorites.collectAsState()
    var selectedFavorite by remember { mutableStateOf<FavoriteStop?>(null) }
    var editingFavorite by remember { mutableStateOf<FavoriteStop?>(null) }
    val departuresVM = remember { DeparturesViewModel() }

    if (favorites.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.FavoriteBorder, contentDescription = null,
                    modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(16.dp))
                Text("No saved favourites", style = MaterialTheme.typography.titleMedium)
                Text("Search for stops and tap Save to add them here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(favorites, key = { it.id }) { fav ->
                FavoriteCard(
                    favorite = fav,
                    onTap = { selectedFavorite = fav },
                    onEdit = { editingFavorite = fav },
                    onDelete = { viewModel.deleteFavorite(fav) }
                )
                HorizontalDivider()
            }
        }
    }

    // Departures bottom sheet
    selectedFavorite?.let { fav ->
        ModalBottomSheet(onDismissRequest = { selectedFavorite = null }) {
            Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(fav.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    IconButton(onClick = { selectedFavorite = null }) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }
                HorizontalDivider()
                Box(modifier = Modifier.fillMaxWidth().height(500.dp)) {
                    DeparturesScreen(viewModel = departuresVM, stops = fav.stops, getOffStopId = fav.getOffStopId)
                }
            }
        }
    }

    // Edit bottom sheet
    editingFavorite?.let { fav ->
        AddFavouriteSheet(
            stops = fav.stops,
            existing = fav,
            favoritesVM = viewModel,
            userLat = userLat,
            userLon = userLon,
            onDismiss = { editingFavorite = null },
            onSave = { updated ->
                viewModel.updateFavorite(updated)
                editingFavorite = null
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoriteCard(
    favorite: FavoriteStop,
    onTap: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = {
            if (it == SwipeToDismissBoxValue.EndToStart) { onDelete(); true } else false
        }
    )
    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                modifier = Modifier.fillMaxSize()
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(Icons.Default.Delete, contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.error)
            }
        }
    ) {
        ListItem(
            headlineContent = {
                Text(
                    favorite.name,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)
                )
            },
            supportingContent = {
                Column {
                    Text(
                        favorite.stops.joinToString(" · ") { it.name },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2
                    )
                    favorite.getOffStopName?.let { dest ->
                        Text(
                            "→ $dest",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            },
            leadingContent = {
                // Rounded-rect badge with red heart, matching iOS heart.fill
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Favorite,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            },
            trailingContent = {
                IconButton(onClick = onEdit) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Edit",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            },
            modifier = Modifier.clickable(onClick = onTap)
        )
    }
}
