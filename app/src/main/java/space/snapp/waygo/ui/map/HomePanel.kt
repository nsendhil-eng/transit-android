package space.snapp.waygo.ui.map

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
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
import space.snapp.waygo.data.api.models.Departure
import space.snapp.waygo.data.api.models.VehicleType
import space.snapp.waygo.ui.components.icon

/** Beyond this walk the panel says nothing is close */
private const val FAR_WALK_MIN = 10

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
    /** A refresh is on its way, so old rows aren't worth a warning yet */
    refreshing: Boolean = false,
    /** The panel's clock, ticking with [routes] so the two only update together */
    now: Long,
    onRouteTap: (NearbyRoute) -> Unit,
    onRequestLocation: () -> Unit,
    /** Height of everything down to the end of the peek rows: the panel's peek height */
    onPeekHeight: (Int) -> Unit,
    /** Height of the header alone: the panel's lowest position, when dragged down */
    onHeaderHeight: (Int) -> Unit = {},
    footer: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    /** The panel's scroll position: the map view returns it to the top when the panel closes */
    listState: LazyListState = rememberLazyListState(),
    /** "Where to?" and saved places, above the routes whatever the state */
    header: @Composable () -> Unit = {}
) {
    val staleMin = if (updatedAtMs > 0 && now - updatedAtMs > STALE_AFTER_MS) ((now - updatedAtMs) / 60_000).coerceAtLeast(1) else null

    val peek = remember(routes) { peekRoutes(routes) }
    // Nothing within a 10 minute walk (semi-rural streets): say so, and what's nearest
    val nearestRoute = routes.minByOrNull { it.distanceM }
    val farNote = nearestRoute?.takeIf { it.walkMinutes > FAR_WALK_MIN }
        ?.let { "No stops within a $FAR_WALK_MIN min walk · nearest: ${it.stopLabel}, ${it.walkMinutes} min walk" }

    // One scrolling list for the whole panel, header included: a downward swipe scrolls the
    // content back up first and only lowers the panel once it's at the top. (With a fixed block
    // above a separate list, a swipe starting on the block dragged the whole panel down.)
    // Every route, under its stop, even those in the resting rows: a stop is shown complete
    // (excluding them made Regatta, whose only route is the F1, vanish from the list)
    val sections = remember(routes) { stopSections(routes) }
    LazyColumn(modifier, state = listState) {
        // The resting view: header, then the state message or the peek rows. Its height is the
        // panel's peek height, measured while it sits at the top of the list.
        item(key = "peek") {
            Column(Modifier.onSizeChanged { onPeekHeight(it.height) }) {
                Box(Modifier.onSizeChanged { onHeaderHeight(it.height) }) { header() }
                Spacer(Modifier.height(6.dp))
                if (staleMin != null && !refreshing && state == HomePanelState.Ready) {
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
                    HomePanelState.NothingNearby -> Message("No stops or services within 3 km right now. Try Where to? for the best way out.")
                    HomePanelState.Ready -> Column(Modifier.alpha(if (staleMin != null) 0.5f else 1f)) {
                        farNote?.let {
                            Text(it, fontSize = 12.sp, color = LateAmber, modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 4.dp))
                        }
                        for (r in peek) RouteRow(r, selected = r.key == selectedKey, now = now, onTap = { onRouteTap(r) })
                        if (routes.size > peek.size) {
                            Text("All ${routes.size} routes nearby, by stop · drag up", fontSize = 11.sp, color = Muted,
                                modifier = Modifier.padding(start = 16.dp, top = 4.dp))
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
        }

        // Revealed by dragging the panel up: every route, under its stop. Stops nearest first
        // (they never move); within a stop, soonest first
        if (state == HomePanelState.Ready) {
            for (section in sections) {
                item(key = "s-${section.group.id}", contentType = "stop") { StopHeader(section) }
                items(section.routes, key = { it.key }, contentType = { "route" }) { r ->
                    RouteRow(r, selected = r.key == selectedKey, now = now, onTap = { onRouteTap(r) }, underStop = true)
                }
            }
        }
        item(key = "footer") { Box(Modifier.padding(12.dp)) { footer() } }
    }
}

/** "🚌 Wesley Hospital · stop 9 · city-bound · 🚶 4 min", above that stop's routes */
@Composable
private fun StopHeader(section: StopSection) {
    val r = section.first
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 2.dp)) {
        Icon(r.mode.icon(), contentDescription = r.mode.label, tint = r.mode.tint, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(6.dp))
        Text(r.placeLabel + (section.direction?.let { " · $it" } ?: ""), style = MaterialTheme.typography.labelLarge,
            color = DestText, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        Text("  ·  ", fontSize = 12.sp, color = Muted)
        Icon(Icons.AutoMirrored.Filled.DirectionsWalk, null, tint = Muted, modifier = Modifier.size(13.dp))
        Text("${section.routes.minOf { it.walkMinutes }} min", fontSize = 12.sp, color = Muted)
    }
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
private fun RouteRow(
    r: NearbyRoute,
    selected: Boolean,
    now: Long,
    onTap: () -> Unit,
    /** Listed under its stop's header: no stop or walk line, just the platform when there is one */
    underStop: Boolean = false
) {
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
            if (underStop) {
                r.platformLabel?.let {
                    Text(it, fontSize = 11.sp, color = Muted, modifier = Modifier.padding(start = 21.dp, top = 2.dp))
                }
            } else Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 21.dp, top = 2.dp)) {
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
