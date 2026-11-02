package space.snapp.waygo.ui.map

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import space.snapp.waygo.data.api.models.Departure
import space.snapp.waygo.data.api.models.VehicleType
import space.snapp.waygo.ui.components.icon

private val CARD_WIDTH = 236.dp
/** Two missed 30 s refreshes */
private const val STALE_AFTER_MS = 75_000L
/** Departures per side in the compact cards */
private const val COMPACT_PER_SIDE = 2

val SheetBg = Color(0xFF181C21)
private val CardBg = Color(0xFF21262D)
private val CardBorder = Color(0xFF333B45)
private val FocusBorder = Color(0xFF4FB3FF)
private val Muted = Color(0xFF8A96A3)
private val DestText = Color(0xFFC3CAD2)
private val LiveGreen = Color(0xFF3DDC84)
private val LateAmber = Color(0xFFFFB74D)


/**
 * The home map's bottom panel. Peeking: compact cards, one per mode (first tap focuses the stop
 * on the map, a second opens its departures). Dragged up: every nearby stop with its next
 * departures, then [footer] (the save prompt).
 */
@Composable
fun HomeSheetContent(
    cards: List<ModeCard>,
    stops: List<NearbyStop>,
    focusedGroupId: String?,
    /** When the departures were fetched; older than STALE_AFTER_MS and the panel says so */
    updatedAtMs: Long,
    onCardTap: (ModeCard) -> Unit,
    onStopTap: (StopGroup) -> Unit,
    /** Height of everything down to the end of the cards: the panel's peek height */
    onPeekHeight: (Int) -> Unit,
    footer: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    // Countdowns tick between fetches
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(15_000); now = System.currentTimeMillis() } }
    val staleMin = if (updatedAtMs > 0 && now - updatedAtMs > STALE_AFTER_MS) ((now - updatedAtMs) / 60_000).coerceAtLeast(1) else null

    Column(modifier) {
        Column(Modifier.onSizeChanged { onPeekHeight(it.height) }) {
            if (staleMin != null) {
                Text(
                    "Last updated $staleMin min ago · check your connection",
                    fontSize = 11.sp, color = LateAmber,
                    modifier = Modifier.padding(start = 16.dp, bottom = 6.dp)
                )
            }
            if (cards.isEmpty()) {
                Text(
                    "Finding departures near you…",
                    fontSize = 13.sp, color = Muted,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )
            } else {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp)
                        .alpha(if (staleMin != null) 0.5f else 1f)
                ) {
                    for (card in cards) {
                        CompactCard(
                            card = card,
                            focused = card.group.id == focusedGroupId,
                            now = now,
                            modifier = if (cards.size == 1) Modifier.fillMaxWidth() else Modifier.width(CARD_WIDTH),
                            onTap = { onCardTap(card) }
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        // Revealed by dragging the panel up
        LazyColumn(Modifier.weight(1f, fill = false)) {
            item {
                Text(
                    "All stops nearby",
                    style = MaterialTheme.typography.labelLarge,
                    color = Muted,
                    modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 4.dp)
                )
            }
            items(stops, key = { it.group.id }) { stop ->
                StopListItem(stop, now, onTap = { onStopTap(stop.group) })
                HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = CardBorder)
            }
            item {
                Box(Modifier.padding(12.dp)) { footer() }
            }
        }
    }
}

@Composable
private fun CompactCard(card: ModeCard, focused: Boolean, now: Long, modifier: Modifier, onTap: () -> Unit) {
    Surface(
        onClick = onTap,
        shape = RoundedCornerShape(14.dp),
        color = CardBg,
        border = BorderStroke(if (focused) 2.dp else 1.dp, if (focused) FocusBorder else CardBorder),
        modifier = modifier
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(card.mode.icon(), contentDescription = card.mode.label, tint = card.mode.tint, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(5.dp))
                Text(
                    card.name,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                // Focused: say what the next tap does, in place of the distance (no height change)
                if (focused) Text("Tap for all", fontSize = 11.sp, color = FocusBorder)
                else Text(distanceLabel(card.distanceM), fontSize = 11.sp, color = Muted)
            }
            Spacer(Modifier.height(4.dp))
            if (card.mode == VehicleType.Rail) {
                // Trains are labelled by destination, too long to pair up: one per line
                for (dep in card.departures.take(COMPACT_PER_SIDE)) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                        // Pill and arrow take what the time leaves, so the name only shortens when it must
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            RoutePill(dep, Modifier.weight(1f, fill = false))
                            Spacer(Modifier.width(4.dp))
                            Arrow(dep.heading, 13.dp)
                        }
                        Spacer(Modifier.width(6.dp))
                        Text(departureTimeLabel(dep, now), fontSize = 12.sp, fontWeight = FontWeight.Bold,
                            color = dep.timeColor, maxLines = 1)
                    }
                }
            } else {
                // One line per side of the road
                for (side in card.sides) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                        side.departures.take(COMPACT_PER_SIDE).forEachIndexed { i, dep ->
                            if (i > 0) Spacer(Modifier.width(10.dp))
                            RoutePill(dep)
                            Spacer(Modifier.width(3.dp))
                            Arrow(dep.heading, 13.dp)
                            Spacer(Modifier.width(3.dp))
                            Text(departureTimeLabel(dep, now), fontSize = 12.sp, fontWeight = FontWeight.Bold,
                                color = dep.timeColor, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StopListItem(stop: NearbyStop, now: Long, onTap: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onTap)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(stop.group.primaryType.icon(), null, tint = stop.group.primaryType.tint, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(6.dp))
            Text(stop.group.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text(distanceLabel(stop.distanceM), fontSize = 11.sp, color = Muted)
        }
        Spacer(Modifier.height(4.dp))
        if (stop.departures.isEmpty()) Text("No more departures today", fontSize = 12.sp, color = Muted)
        for (dep in stop.departures) DepartureRow(dep, now)
    }
}

@Composable
private fun DepartureRow(dep: Departure, now: Long) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
        // A train's pill already says where it goes
        if (dep.mode == VehicleType.Rail) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                RoutePill(dep, Modifier.weight(1f, fill = false))
                Spacer(Modifier.width(5.dp))
                Arrow(dep.heading, 13.dp)
            }
        } else {
            RoutePill(dep)
            Spacer(Modifier.width(5.dp))
            Arrow(dep.heading, 13.dp)
            Spacer(Modifier.width(4.dp))
            Text(dep.headsign, fontSize = 12.sp, color = DestText, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.width(6.dp))
        Text(departureTimeLabel(dep, now), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = dep.timeColor)
    }
}

/** Which way the service leaves the stop, as a compass arrow; blank space when unknown so rows line up */
@Composable
private fun Arrow(heading: Int?, size: androidx.compose.ui.unit.Dp) {
    Box(Modifier.size(size + 1.dp), contentAlignment = Alignment.Center) {
        heading?.let {
            Icon(Icons.Default.ArrowUpward, null, tint = Color.White, modifier = Modifier.size(size).rotate(it.toFloat()))
        }
    }
}

@Composable
private fun RoutePill(dep: Departure, modifier: Modifier = Modifier) {
    val bg = dep.routeColorInt?.let { Color(it) } ?: Color(0xFF555555)
    val fg = dep.routeTextColorInt?.let { Color(it) } ?: Color.White
    Surface(shape = RoundedCornerShape(5.dp), color = bg, modifier = modifier) {
        Text(
            dep.pillLabel,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = fg,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp).widthIn(min = 18.dp)
        )
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
