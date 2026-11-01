package space.snapp.waygo.ui.map

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import space.snapp.waygo.data.api.models.Departure
import space.snapp.waygo.data.api.models.VehicleType
import space.snapp.waygo.ui.components.icon

private val CARD_WIDTH = 272.dp
/** Two missed 30 s refreshes */
private const val STALE_AFTER_MS = 75_000L
private val CardBg = Color(0xF21F242B)
private val CardBorder = Color(0xFF333B45)
private val FocusBorder = Color(0xFF4FB3FF)
private val Muted = Color(0xFF8A96A3)
private val DestText = Color(0xFFC3CAD2)
private val LiveGreen = Color(0xFF3DDC84)
private val LateAmber = Color(0xFFFFB74D)

/**
 * Cards along the bottom of the home map, one per mode with something running nearby.
 * The first tap on a card focuses its stop on the map; a second tap opens all its departures.
 */
@Composable
fun ModeCardsRow(
    cards: List<ModeCard>,
    focusedGroupId: String?,
    /** When the departures were fetched; older than STALE_AFTER_MS and the cards say so */
    updatedAtMs: Long,
    onTap: (ModeCard) -> Unit,
    modifier: Modifier = Modifier
) {
    if (cards.isEmpty()) return
    // Countdowns tick between fetches
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(15_000); now = System.currentTimeMillis() } }

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp)
    ) {
        val staleMin = if (updatedAtMs > 0 && now - updatedAtMs > STALE_AFTER_MS) ((now - updatedAtMs) / 60_000).coerceAtLeast(1) else null
        for (card in cards) {
            ModeCardView(
                card = card,
                focused = card.group.id == focusedGroupId,
                now = now,
                staleMin = staleMin,
                // One card fills the row; several scroll sideways, the next one peeking in
                modifier = if (cards.size == 1) Modifier.fillParentWidth() else Modifier.width(CARD_WIDTH),
                onTap = { onTap(card) }
            )
        }
    }
}

// A Row inside horizontalScroll has unbounded width; a lone card takes the screen width instead
@Composable
private fun Modifier.fillParentWidth(): Modifier {
    val w = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp.dp - 24.dp
    return this.width(w)
}

@Composable
private fun ModeCardView(card: ModeCard, focused: Boolean, now: Long, staleMin: Long?, modifier: Modifier, onTap: () -> Unit) {
    Surface(
        onClick = onTap,
        shape = RoundedCornerShape(16.dp),
        color = CardBg,
        border = BorderStroke(if (focused) 2.dp else 1.dp, if (focused) FocusBorder else CardBorder),
        shadowElevation = 8.dp,
        modifier = modifier
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            // Out of date: dim the times so nobody runs for a bus that may have gone
            Column(Modifier.alpha(if (staleMin != null) 0.5f else 1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(card.mode.icon(), contentDescription = card.mode.label, tint = card.mode.tint, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    card.name,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(distanceLabel(card.distanceM), fontSize = 11.sp, color = Muted)
            }
            Spacer(Modifier.height(6.dp))
            card.sides.forEachIndexed { i, side ->
                // Opposite sides need no distance (it read as the gap between them); another stop does
                if (i > 0) SideDivider(if (card.isAcrossTheRoad) "across the road" else "nearby stop · ${distanceLabel(side.distanceM)} away")
                for (dep in side.departures) DepartureRow(dep, now)
            }
            }
            if (staleMin != null) {
                Spacer(Modifier.height(4.dp))
                Text("Last updated $staleMin min ago · check your connection", fontSize = 11.sp, color = LateAmber)
            }
            if (focused) {
                Spacer(Modifier.height(4.dp))
                Text("Tap again for all departures", fontSize = 11.sp, color = FocusBorder)
            }
        }
    }
}

/** "── across the road ──" between the two sides of a paired card */
@Composable
private fun SideDivider(label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 3.dp)) {
        HorizontalDivider(Modifier.width(10.dp), color = CardBorder)
        Text(
            "  $label  ",
            fontSize = 10.5.sp,
            color = Muted
        )
        HorizontalDivider(Modifier.weight(1f), color = CardBorder)
    }
}

@Composable
private fun DepartureRow(dep: Departure, now: Long) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
        RoutePill(dep)
        Spacer(Modifier.width(5.dp))
        Box(Modifier.size(14.dp), contentAlignment = Alignment.Center) {
            dep.heading?.let {
                Icon(Icons.Default.Navigation, null, tint = DestText, modifier = Modifier.size(13.dp).rotate(it.toFloat()))
            }
        }
        Spacer(Modifier.width(4.dp))
        Text(dep.headsign, fontSize = 12.sp, color = DestText, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(6.dp))
        Text(
            departureTimeLabel(dep, now),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = when {
                dep.isLate -> LateAmber
                dep.isRealtime == true -> LiveGreen
                else -> Color.White
            }
        )
    }
}

@Composable
private fun RoutePill(dep: Departure) {
    val bg = dep.routeColorInt?.let { Color(it) } ?: Color(0xFF555555)
    val fg = dep.routeTextColorInt?.let { Color(it) } ?: Color.White
    Surface(shape = RoundedCornerShape(5.dp), color = bg) {
        Text(
            dep.pillLabel,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = fg,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp).widthIn(min = 18.dp)
        )
    }
}

private val VehicleType.tint: Color
    get() = when (this) {
        VehicleType.Bus -> Color(0xFF2196F3)
        VehicleType.Rail -> Color(0xFF9575CD)
        VehicleType.Ferry -> Color(0xFF26A69A)
        VehicleType.Tram -> Color(0xFF66BB6A)
    }
