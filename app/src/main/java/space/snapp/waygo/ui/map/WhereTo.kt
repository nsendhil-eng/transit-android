package space.snapp.waygo.ui.map

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import space.snapp.waygo.data.api.TransitApiService
import space.snapp.waygo.data.api.models.Eta
import space.snapp.waygo.data.models.FavoritePlace
import space.snapp.waygo.data.pollWhileVisible
import kotlin.math.roundToInt

/** Saved-place chips refresh this often while the app is on screen */
private const val ETA_REFRESH_MS = 120_000L
/** …and when you've moved about this far (degrees, ~300 m) */
private const val ETA_MOVE_STEP = 0.003
/** Chips shown in the panel; more places live on the Favourites tab */
private const val MAX_CHIPS = 3

private val BarBg = Color(0xFF262C34)
private val ChipBg = Color(0xFF21262D)
private val ChipBorder = Color(0xFF333B45)
private val Muted = Color(0xFF8A96A3)
private val Accent = Color(0xFF4FB3FF)

/**
 * The top of the home panel: "Where to?" (plans from where you are) and your saved places,
 * each with its travel time from here ("Home · 34 min · leave in 3 · 412").
 */
@Composable
fun WhereToHeader(
    places: List<FavoritePlace>,
    userLat: Double?,
    userLon: Double?,
    onWhereTo: () -> Unit,
    onSearchStops: () -> Unit,
    onPlace: (FavoritePlace) -> Unit,
    onAddPlace: (suggestedName: String) -> Unit
) {
    Column(Modifier.padding(horizontal = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(onClick = onWhereTo, shape = RoundedCornerShape(14.dp), color = BarBg, modifier = Modifier.weight(1f).height(46.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 14.dp)) {
                    Icon(Icons.Default.Search, null, tint = Muted, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("Where to?", fontSize = 16.sp, color = Color(0xFFC3CAD2))
                }
            }
            Spacer(Modifier.width(8.dp))
            // Stops and routes by name: the old search, a tap away
            Surface(onClick = onSearchStops, shape = RoundedCornerShape(14.dp), color = BarBg, modifier = Modifier.height(46.dp)) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 12.dp)) {
                    Text("Stops & routes", fontSize = 12.sp, color = Muted)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val shown = places.take(MAX_CHIPS)
            for (p in shown) PlaceChip(p, userLat, userLon, onClick = { onPlace(p) })
            // Nothing saved: one tap to set Home (the zero-tap way home next time)
            if (shown.none { it.name.equals("Home", true) }) GhostChip("Set home", Icons.Default.Home) { onAddPlace("Home") }
            else if (shown.size < MAX_CHIPS) GhostChip("Add place", Icons.Default.Add) { onAddPlace("") }
        }
    }
}

@Composable
private fun PlaceChip(place: FavoritePlace, userLat: Double?, userLon: Double?, onClick: () -> Unit) {
    val api = remember { TransitApiService.instance }
    var eta by remember(place.id) { mutableStateOf<Eta?>(null) }
    var loading by remember(place.id) { mutableStateOf(false) }
    // Asked and answered with no trip (outside SEQ, nothing running): don't spin forever
    var noTrip by remember(place.id) { mutableStateOf(false) }
    // Refetch on open, every couple of minutes, and after moving ~300 m; never in the background
    val cellLat = userLat?.let { (it / ETA_MOVE_STEP).roundToInt() }
    val cellLon = userLon?.let { (it / ETA_MOVE_STEP).roundToInt() }
    LaunchedEffect(place.id, place.lat, place.lon, cellLat, cellLon) {
        val lat = userLat ?: return@LaunchedEffect
        val lon = userLon ?: return@LaunchedEffect
        pollWhileVisible(ETA_REFRESH_MS) {
            loading = true
            runCatching { api.eta(lat, lon, place.lat, place.lon) }.onSuccess { eta = it.eta; noTrip = it.eta == null }
            loading = false
        }
    }
    Surface(onClick = onClick, shape = RoundedCornerShape(12.dp), color = ChipBg, border = BorderStroke(1.dp, ChipBorder)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)) {
            Icon(placeIcon(place.name), null, tint = Accent, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Column {
                Text(place.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text(
                    eta?.let { etaChipText(it) } ?: when {
                        noTrip -> "No trip found"
                        loading || userLat != null -> "…"
                        else -> "Tap to plan"
                    },
                    fontSize = 11.sp, color = Muted, maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun GhostChip(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(12.dp), color = Color.Transparent, border = BorderStroke(1.dp, ChipBorder)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp)) {
            Icon(icon, null, tint = Muted, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(text, fontSize = 13.sp, color = Muted)
        }
    }
}

private fun placeIcon(name: String) = when {
    name.equals("Home", true) -> Icons.Default.Home
    name.equals("Work", true) -> Icons.Default.Work
    else -> Icons.Default.Place
}

/** A walk this short means you're already there */
private const val HERE_MAX_MIN = 2

/** Walking beyond this is part of the answer, not a detail ("25 min walk + 444") */
private const val LONG_WALK_MIN = 10

/**
 * "34 min · leave now · 412", "34 min · leave in 3 · 412", "59 min · leave in 3 · 25 min walk + 444",
 * "12 min walk", "You're here"
 */
fun etaChipText(eta: Eta): String {
    if (eta.walkOnly) return if (eta.minutes <= HERE_MAX_MIN) "You're here" else "${eta.minutes} min walk"
    val leave = if (eta.leaveInMinutes <= 0) "leave now" else "leave in ${eta.leaveInMinutes}"
    val route = eta.firstLeg?.let { leg -> leg.route?.takeIf { leg.mode != "RAIL" } ?: modeWord(leg.mode) }
    val catch = if (eta.walkMinutes > LONG_WALK_MIN && route != null) "${eta.walkMinutes} min walk + $route" else route
    return listOfNotNull("${eta.minutes} min", leave, catch).joinToString(" · ")
}

private fun modeWord(mode: String) = when (mode) { "RAIL" -> "train"; "FERRY" -> "ferry"; "TRAM" -> "tram"; else -> "bus" }
