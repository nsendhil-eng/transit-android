package space.snapp.waygo.ui.nearme

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import space.snapp.waygo.data.api.models.Departure
import space.snapp.waygo.data.api.models.VehicleType
import space.snapp.waygo.ui.components.LiveIndicator
import space.snapp.waygo.ui.components.delayLabel
import space.snapp.waygo.ui.components.icon
import java.util.Calendar
import java.util.Date

@Composable
fun NearMeScreen(
    viewModel: NearMeViewModel,
    hasLocationPermission: Boolean,
    userLat: Double?,
    userLon: Double?,
    onRequestPermission: () -> Unit,
    onDepartureClick: ((Departure) -> Unit)? = null
) {
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val stops by viewModel.stops.collectAsState()
    val departures by viewModel.departures.collectAsState()
    val modeFilter by viewModel.modeFilter.collectAsState()

    LaunchedEffect(hasLocationPermission, userLat, userLon) {
        if (hasLocationPermission && userLat != null && userLon != null) {
            viewModel.onLocation(userLat, userLon)
        }
    }
    // Only poll departures while this tab is on screen
    DisposableEffect(Unit) {
        viewModel.resume()
        onDispose { viewModel.pause() }
    }

    // Countdown clock (minute precision, so 15 s is plenty)
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) { delay(15_000); now = System.currentTimeMillis() }
    }

    if (!hasLocationPermission) {
        // Location permission prompt — matches iOS locationPermissionPrompt
        CenteredMessage(
            icon = Icons.Default.LocationOn,
            title = "Location Access Needed",
            body = "Allow location access to find transit stops near you.",
            action = "Allow Location Access" to onRequestPermission
        )
        return
    }

    val modes = remember(departures) { availableModes(departures) }
    val groups = remember(departures, stops, modeFilter, now) {
        buildRouteGroups(departures, stops, modeFilter, now)
    }

    when {
        isLoading && departures.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CircularProgressIndicator()
                Text("Finding routes near you…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        error != null && departures.isEmpty() -> CenteredMessage(
            icon = Icons.Default.LocationOff,
            title = "Couldn't load nearby routes",
            body = error ?: "",
            action = "Retry" to viewModel::retry
        )
        departures.isEmpty() -> CenteredMessage(
            icon = Icons.Default.DirectionsBus,
            title = "No departures nearby",
            body = "No upcoming services from stops within 500 m.\nTry moving closer to a stop."
        )
        else -> Column(Modifier.fillMaxSize()) {
            if (modes.size > 1) {
                ModeFilterRow(modes = modes, selected = modeFilter, onSelect = viewModel::setModeFilter)
            }
            // Cards re-sort as countdowns tick. LazyColumn anchors on the first visible key, so
            // when a card moves above it the list silently scrolls down; pin to the top instead
            // if the user hadn't scrolled away from it.
            val listState = rememberLazyListState()
            val firstKey = groups.firstOrNull()?.key
            LaunchedEffect(firstKey) {
                if (listState.firstVisibleItemIndex <= 1) listState.scrollToItem(0)
            }
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(groups, key = { it.key }) { group ->
                    RouteGroupCard(group = group, now = now, onDepartureClick = onDepartureClick)
                }
            }
        }
    }
}

@Composable
private fun ModeFilterRow(modes: List<VehicleType>, selected: VehicleType?, onSelect: (VehicleType?) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(selected = selected == null, onClick = { onSelect(null) }, label = { Text("All") })
        modes.forEach { mode ->
            FilterChip(
                selected = selected == mode,
                onClick = { onSelect(if (selected == mode) null else mode) },
                label = { Text(mode.label) },
                leadingIcon = { Icon(mode.icon(), contentDescription = null, modifier = Modifier.size(16.dp)) }
            )
        }
    }
}

/** A route card; when the route serves both directions nearby, swipe between them. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RouteGroupCard(group: RouteGroup, now: Long, onDepartureClick: ((Departure) -> Unit)?) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        modifier = Modifier.fillMaxWidth()
    ) {
        if (group.directions.size == 1) {
            DirectionCardContent(group.directions.first(), now, onDepartureClick)
        } else {
            val pagerState = rememberPagerState { group.directions.size }
            Column {
                HorizontalPager(state = pagerState) { page ->
                    DirectionCardContent(group.directions[page], now, onDepartureClick)
                }
                // Page dots + the other direction's destination as a swipe hint
                val other = group.directions[(pagerState.currentPage + 1) % group.directions.size]
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(group.directions.size) { i ->
                        Box(
                            Modifier
                                .padding(end = 4.dp)
                                .size(6.dp)
                                .background(
                                    if (i == pagerState.currentPage) MaterialTheme.colorScheme.onSurface
                                    else MaterialTheme.colorScheme.outline,
                                    CircleShape
                                )
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Swipe for ${other.headsign}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun DirectionCardContent(card: DirectionCard, now: Long, onDepartureClick: ((Departure) -> Unit)?) {
    val next = card.departures.first()
    val routeColor = parseHex(card.routeColor) ?: MaterialTheme.colorScheme.primary
    val routeTextColor = parseHex(card.routeTextColor)
        ?: if (routeColor.luminance() > 0.5f) Color.Black else Color.White
    val walkSecs = card.walkSeconds

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = onDepartureClick != null) { onDepartureClick?.invoke(next) }
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RouteBadge(card, routeColor, routeTextColor)
        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                displayHeadsign(card),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (walkSecs != null) {
                    Icon(
                        Icons.Default.DirectionsWalk, contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "${(walkSecs / 60).coerceAtLeast(1)} min · ",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    card.platformCode?.let { "Platform $it" } ?: shortStopName(card.stopName),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(Modifier.width(8.dp))
        NextDepartures(card.departures, walkSecs, now)
    }
}

@Composable
private fun RouteBadge(card: DirectionCard, routeColor: Color, routeTextColor: Color) {
    Surface(color = routeColor, shape = RoundedCornerShape(12.dp)) {
        Box(
            modifier = Modifier.defaultMinSize(minWidth = 56.dp, minHeight = 44.dp).padding(horizontal = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            if (card.vehicleType == VehicleType.Bus) {
                Text(
                    card.routeNumber,
                    color = routeTextColor,
                    fontSize = if (card.routeNumber.length > 3) 15.sp else 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1
                )
            } else {
                // Rail short names are internal codes (NAIP, SPRP) — show the mode instead
                Icon(card.vehicleType.icon(), contentDescription = card.vehicleType.label, tint = routeTextColor)
            }
        }
    }
}

/** Big countdown for the next service, its live status, and the following two. */
@Composable
private fun NextDepartures(departures: List<Departure>, walkSecs: Int?, now: Long) {
    val next = departures.first()
    val secs = ((next.departureDate.time - now) / 1000).toInt()
    // Dim a departure you can't reach on foot in time
    val missable = walkSecs != null && secs in 0 until walkSecs
    val fg = MaterialTheme.colorScheme.onSurface.copy(alpha = if (missable) 0.45f else 1f)
    val isLive = next.isRealtime ?: (next.expectedDepartureUtc != null)

    Column(horizontalAlignment = Alignment.End) {
        Row(verticalAlignment = Alignment.Top) {
            val (big, unit) = countdownParts(secs, next.departureDate)
            Text(big, color = fg, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
            if (unit.isNotEmpty()) {
                Text(
                    unit,
                    color = fg,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(start = 2.dp, top = 6.dp)
                )
            }
            if (isLive) {
                Spacer(Modifier.width(3.dp))
                LiveIndicator(color = fg, modifier = Modifier.size(11.dp))
            }
        }
        val (label, color) = delayLabel(isLive, next.delaySeconds ?: delayFrom(next))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = color ?: MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (departures.size > 1) {
            val later = departures.drop(1).map { d -> ((d.departureDate.time - now) / 1000).toInt() to d.departureDate }
            val allMinutes = later.all { it.first < 3600 }
            Text(
                // "then 13 · 23 min" when all are soon; otherwise each item carries its own unit
                // so "then 57 min · 9:52 PM" isn't misread as two clock times
                "then " + later.joinToString(" · ") { (s, date) ->
                    when {
                        s >= 3600 -> formatClock(date)
                        allMinutes -> "${(s / 60).coerceAtLeast(0)}"
                        else -> "${(s / 60).coerceAtLeast(0)} min"
                    }
                } + if (allMinutes) " min" else "",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CenteredMessage(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    body: String,
    action: Pair<String, () -> Unit>? = null
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            action?.let { (label, onClick) -> Button(onClick = onClick) { Text(label) } }
        }
    }
}

// ── Helpers ────────────────────────────────────────────────────────────────

/** "3" + "min", "Now", or a clock time "8:17" + "PM" for departures over an hour away. */
private fun countdownParts(secs: Int, date: Date): Pair<String, String> = when {
    secs < 60 -> "Now" to ""
    secs < 3600 -> "${secs / 60}" to "min"
    else -> formatClock(date).let { it.substringBefore(" ") to it.substringAfter(" ", "") }
}

/** Older API responses lack delaySeconds; derive it from scheduled vs expected. */
private fun delayFrom(d: Departure): Int? {
    val expected = d.expectedDepartureUtc ?: return null
    return runCatching {
        val fmt = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US)
            .apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
        ((fmt.parse(expected)!!.time - fmt.parse(d.scheduledDepartureUtc)!!.time) / 1000).toInt()
    }.getOrNull()
}

/** Train destinations read better without the trailing "station". */
private fun displayHeadsign(card: DirectionCard): String =
    if (card.vehicleType == VehicleType.Rail) card.headsign.removeSuffix(" station") else card.headsign

/** "Coronation Dr at Wesley Hospital, stop 9" → "Wesley Hospital, stop 9" for compact subtitles. */
private fun shortStopName(name: String): String = name.substringAfter(" at ", name)

private fun formatClock(date: Date): String {
    val cal = Calendar.getInstance().apply { time = date }
    val h = cal.get(Calendar.HOUR).let { if (it == 0) 12 else it }
    val ampm = if (cal.get(Calendar.AM_PM) == Calendar.AM) "AM" else "PM"
    return "%d:%02d %s".format(h, cal.get(Calendar.MINUTE), ampm)
}

private fun parseHex(hex: String?): Color? =
    hex?.takeIf { it.isNotBlank() }?.let {
        runCatching { Color(android.graphics.Color.parseColor("#${it.trimStart('#')}")) }.getOrNull()
    }
