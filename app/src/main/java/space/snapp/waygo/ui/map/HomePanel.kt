package space.snapp.waygo.ui.map

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import space.snapp.waygo.data.api.models.Departure
import space.snapp.waygo.data.api.models.VehicleType
import space.snapp.waygo.ui.components.icon

/** Two missed 30 s refreshes */
private const val STALE_AFTER_MS = 75_000L

val SheetBg = Color(0xFF181C21)
private val RowSelected = Color(0xFF22303D)
private val Divider = Color(0xFF2A3038)
private val Accent = Color(0xFF4FB3FF)
private val Muted = Color(0xFF8A96A3)
private val DestText = Color(0xFFC3CAD2)
private val LiveGreen = Color(0xFF3DDC84)
private val LateAmber = Color(0xFFFFB74D)

/** What the panel can say before it has routes to show */
enum class HomePanelState { Ready, Loading, NoLocation, OutsideSeq, NothingNearby }

/**
 * The home map's bottom panel. Peeking: the nearest route of each mode (so a train ten
 * minutes' walk away shows beside the buses at the door). Dragged up: every route nearby,
 * grouped by mode, nearest first, then [footer]. Tapping a row shows its stop, route and live
 * vehicles on the map; tapping it again opens the stop.
 */
@Composable
fun HomePanel(
    state: HomePanelState,
    routes: List<NearbyRoute>,
    selectedKey: String?,
    /** When the departures were fetched; older than STALE_AFTER_MS and the panel says so */
    updatedAtMs: Long,
    onRouteTap: (NearbyRoute) -> Unit,
    onRequestLocation: () -> Unit,
    /** Height of everything down to the end of the peek rows: the panel's peek height */
    onPeekHeight: (Int) -> Unit,
    footer: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(15_000); now = System.currentTimeMillis() } }
    val staleMin = if (updatedAtMs > 0 && now - updatedAtMs > STALE_AFTER_MS) ((now - updatedAtMs) / 60_000).coerceAtLeast(1) else null

    // Modes in order of their nearest route; each mode's routes nearest first (already sorted)
    val byMode = remember(routes) { routes.groupBy { it.mode }.entries.sortedBy { (_, r) -> r.first().distanceM } }
    val peek = byMode.map { it.value.first() }

    Column(modifier) {
        Column(Modifier.onSizeChanged { onPeekHeight(it.height) }) {
            if (staleMin != null && state == HomePanelState.Ready) {
                Text("Last updated $staleMin min ago · check your connection", fontSize = 11.sp, color = LateAmber,
                    modifier = Modifier.padding(start = 16.dp, bottom = 4.dp))
            }
            when (state) {
                HomePanelState.NoLocation -> Message(
                    "Allow location to see what's leaving near you",
                    action = "Allow location", onAction = onRequestLocation, icon = true
                )
                HomePanelState.OutsideSeq -> Message("WayGo covers South East Queensland. Nothing nearby to show here.")
                HomePanelState.Loading -> Message("Finding what's leaving near you…")
                HomePanelState.NothingNearby -> Message("Nothing leaving within a 15 minute walk right now.")
                HomePanelState.Ready -> Column(Modifier.alpha(if (staleMin != null) 0.5f else 1f)) {
                    for (r in peek) RouteRow(r, selected = r.key == selectedKey, now = now, onTap = { onRouteTap(r) })
                    if (routes.size > peek.size) {
                        Text("${routes.size - peek.size} more routes nearby · drag up", fontSize = 11.sp, color = Muted,
                            modifier = Modifier.padding(start = 16.dp, top = 4.dp))
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }

        // Revealed by dragging the panel up: the rest of each mode (the peek rows stay above)
        LazyColumn(Modifier.weight(1f, fill = false)) {
            if (state == HomePanelState.Ready) {
                for ((mode, all) in byMode) {
                    val list = all.drop(1)
                    if (list.isEmpty()) continue
                    item(key = "h-$mode") {
                        Text(modeHeading(mode, list.size), style = MaterialTheme.typography.labelLarge, color = Muted,
                            modifier = Modifier.padding(start = 16.dp, top = 10.dp, bottom = 2.dp))
                    }
                    items(list, key = { it.key }) { r ->
                        RouteRow(r, selected = r.key == selectedKey, now = now, onTap = { onRouteTap(r) })
                    }
                }
            }
            item { Box(Modifier.padding(12.dp)) { footer() } }
        }
    }
}

private fun modeHeading(mode: VehicleType, n: Int): String {
    val noun = when (mode) {
        VehicleType.Rail -> "trains"; VehicleType.Bus -> "buses"; VehicleType.Ferry -> "ferries"; VehicleType.Tram -> "trams"
    }
    return "More $noun · $n"
}

@Composable
private fun Message(text: String, action: String? = null, onAction: () -> Unit = {}, icon: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        if (icon) { Icon(Icons.Default.LocationOff, null, tint = Muted, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)) }
        Text(text, fontSize = 13.sp, color = Muted, modifier = Modifier.weight(1f))
        if (action != null) TextButton(onClick = onAction) { Text(action, color = Accent) }
    }
}

/**
 * [412] ↗ City                                   3 min · 11 min
 *       Wesley Hospital · stop 9 · 🚶 2 min
 */
@Composable
private fun RouteRow(r: NearbyRoute, selected: Boolean, now: Long, onTap: () -> Unit) {
    Surface(
        color = if (selected) RowSelected else Color.Transparent,
        border = if (selected) BorderStroke(1.dp, Accent.copy(alpha = 0.6f)) else null,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 1.dp).clickable(onClick = onTap)
    ) {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 7.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(r.mode.icon(), contentDescription = r.mode.label, tint = r.mode.tint, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(6.dp))
                RoutePill(r.first, Modifier.weight(1f, fill = false))
                Spacer(Modifier.width(4.dp))
                Arrow(r.first.heading, 13.dp)
                // Trains: the pill already says where it goes
                if (r.mode != VehicleType.Rail) {
                    Spacer(Modifier.width(4.dp))
                    Text(r.first.headsign, fontSize = 13.sp, color = DestText, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f))
                } else Spacer(Modifier.weight(0.01f))
                Spacer(Modifier.width(8.dp))
                r.departures.forEachIndexed { i, dep ->
                    if (i > 0) Text(" · ", fontSize = 12.sp, color = Muted)
                    Text(departureTimeLabel(dep, now), fontSize = if (i == 0) 13.sp else 12.sp,
                        fontWeight = if (i == 0) FontWeight.Bold else FontWeight.Normal,
                        color = if (i == 0) dep.timeColor else Muted, maxLines = 1)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 21.dp, top = 2.dp)) {
                Text(r.stopLabel, fontSize = 11.sp, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false))
                Text("  ·  ", fontSize = 11.sp, color = Muted)
                Icon(Icons.AutoMirrored.Filled.DirectionsWalk, null, tint = Muted, modifier = Modifier.size(12.dp))
                Text("${r.walkMinutes} min", fontSize = 11.sp, color = Muted)
            }
        }
    }
}

/** Which way the service leaves the stop; blank space when unknown so rows line up */
@Composable
private fun Arrow(heading: Int?, size: Dp) {
    Box(Modifier.size(size + 1.dp), contentAlignment = Alignment.Center) {
        heading?.let { Icon(Icons.Default.ArrowUpward, null, tint = Color.White, modifier = Modifier.size(size).rotate(it.toFloat())) }
    }
}

@Composable
private fun RoutePill(dep: Departure, modifier: Modifier = Modifier) {
    val bg = dep.routeColorInt?.let { Color(it) } ?: Color(0xFF555555)
    val fg = dep.routeTextColorInt?.let { Color(it) } ?: Color.White
    Surface(shape = RoundedCornerShape(5.dp), color = bg, modifier = modifier) {
        Text(dep.pillLabel, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = fg, maxLines = 1,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp).widthIn(min = 18.dp))
    }
}

/** Live times green, late amber, timetable white */
private val Departure.timeColor: Color
    get() = when {
        isLate -> LateAmber
        isRealtime == true -> LiveGreen
        else -> Color.White
    }

private val VehicleType.tint: Color
    get() = when (this) {
        VehicleType.Bus -> Color(0xFF2196F3)
        VehicleType.Rail -> Color(0xFF9575CD)
        VehicleType.Ferry -> Color(0xFF26A69A)
        VehicleType.Tram -> Color(0xFF66BB6A)
    }
