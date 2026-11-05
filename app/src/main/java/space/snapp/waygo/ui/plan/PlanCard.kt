package space.snapp.waygo.ui.plan

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.Canvas
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import space.snapp.waygo.data.api.models.AddressResult
import space.snapp.waygo.data.api.models.ArriveFirstOrder
import space.snapp.waygo.data.api.models.Itinerary
import space.snapp.waygo.data.api.models.ItineraryLeg
import space.snapp.waygo.ui.plan.PlanViewModel.ActiveField

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanCard(
    viewModel: PlanViewModel,
    userLat: Double?,
    userLon: Double?,
    onClose: () -> Unit,
    onItinerarySelect: (Itinerary) -> Unit = {}
) {
    val fromQuery by viewModel.fromQuery.collectAsState()
    val toQuery by viewModel.toQuery.collectAsState()
    val fromSelected by viewModel.fromSelected.collectAsState()
    val toSelected by viewModel.toSelected.collectAsState()
    val suggestions by viewModel.suggestions.collectAsState()
    val activeField by viewModel.activeField.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val itineraries by viewModel.itineraries.collectAsState()
    val isPlanning by viewModel.isPlanning.collectAsState()
    val planError by viewModel.planError.collectAsState()

    val fromFocusRequester = remember { FocusRequester() }
    val toFocusRequester = remember { FocusRequester() }

    val textColor = Color(0xFF1A1A1A)
    val hintColor = Color(0xFF888888)
    val accentBlue = Color(0xFF1A73E8)
    val cardBg = Color.White

    LaunchedEffect(Unit) {
        // Opened from "Where to?" or a saved place, From is already my location
        if (activeField == ActiveField.FROM) fromFocusRequester.requestFocus()
    }

    // Sync user location into VM
    LaunchedEffect(userLat, userLon) {
        viewModel.userLat = userLat
        viewModel.userLon = userLon
    }

    // Auto-shift focus to To field when From is confirmed; drop the keyboard once both are set
    // so it doesn't cover the results
    val focusManager = LocalFocusManager.current
    LaunchedEffect(activeField) {
        when (activeField) {
            ActiveField.TO -> toFocusRequester.requestFocus()
            ActiveField.NONE -> focusManager.clearFocus()
            else -> {}
        }
    }

    Column {
        // ── From / To card ───────────────────────────────────────────────
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = cardBg,
            shadowElevation = 10.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Timeline dots
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(end = 12.dp, top = 6.dp, bottom = 6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(accentBlue, CircleShape)
                    )
                    Box(
                        modifier = Modifier
                            .width(2.dp)
                            .height(22.dp)
                            .background(Color(0xFFCCCCCC))
                    )
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .border(2.dp, accentBlue, CircleShape)
                    )
                }

                // Text fields
                Column(modifier = Modifier.weight(1f)) {
                    PlanField(
                        value = fromQuery,
                        onValueChange = viewModel::onFromQueryChange,
                        placeholder = "From: address or stop",
                        isActive = activeField == ActiveField.FROM,
                        focusRequester = fromFocusRequester,
                        onFocused = { viewModel.activateField(ActiveField.FROM) },
                        textColor = textColor,
                        hintColor = hintColor,
                        trailingContent = {
                            if (fromQuery.isNotEmpty()) {
                                IconButton(
                                    onClick = viewModel::clearFrom,
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Clear from",
                                        modifier = Modifier.size(16.dp),
                                        tint = hintColor
                                    )
                                }
                            }
                        }
                    )

                    HorizontalDivider(color = Color(0xFFEEEEEE))

                    PlanField(
                        value = toQuery,
                        onValueChange = viewModel::onToQueryChange,
                        placeholder = "To: address or stop",
                        isActive = activeField == ActiveField.TO,
                        focusRequester = toFocusRequester,
                        onFocused = { viewModel.activateField(ActiveField.TO) },
                        textColor = textColor,
                        hintColor = hintColor,
                        trailingContent = {
                            if (toQuery.isNotEmpty()) {
                                IconButton(
                                    onClick = viewModel::clearTo,
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Clear to",
                                        modifier = Modifier.size(16.dp),
                                        tint = hintColor
                                    )
                                }
                            }
                        }
                    )
                }

                // Action buttons column
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(onClick = onClose, modifier = Modifier.size(40.dp)) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Exit plan mode",
                            tint = Color(0xFF555555),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(onClick = viewModel::swap, modifier = Modifier.size(40.dp)) {
                        Icon(
                            Icons.Default.SwapVert,
                            contentDescription = "Swap",
                            tint = Color(0xFF555555),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        // ── "My current location" quick pick (From field, empty) ─────────
        if (activeField == ActiveField.FROM && fromQuery.isEmpty() && userLat != null) {
            Spacer(Modifier.height(4.dp))
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = cardBg,
                shadowElevation = 6.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.useMyLocationAsFrom() }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.MyLocation,
                        contentDescription = null,
                        tint = accentBlue,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "My current location",
                        style = MaterialTheme.typography.bodyMedium,
                        color = textColor
                    )
                }
            }
        }

        // ── Address suggestions ──────────────────────────────────────────
        if (suggestions.isNotEmpty() || isSearching) {
            Spacer(Modifier.height(4.dp))
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 8.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isSearching && suggestions.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    }
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                        items(suggestions, key = { it.placeId }) { result ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.selectSuggestion(result) }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Place,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        result.shortName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1
                                    )
                                    if (result.subtitle.isNotEmpty()) {
                                        Text(
                                            result.subtitle,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        }
                    }
                }
            }
        }

        // ── Journey results ───────────────────────────────────────────────
        if (fromSelected != null && toSelected != null && suggestions.isEmpty()) {
            Spacer(Modifier.height(8.dp))
            when {
                isPlanning -> Box(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }

                planError != null -> Box(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(planError!!, color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium)
                }

                itineraries.isNotEmpty() -> {
                    // Default: the trip that gets you there soonest, as the home screen's place chips
                    // pick. "Fastest" (shortest ride) can leave later and still arrive after it.
                    var sort by remember { mutableStateOf(PlanSort.ArriveFirst) }
                    val sorted = when (sort) {
                        PlanSort.ArriveFirst -> itineraries.sortedWith(ArriveFirstOrder)
                        PlanSort.Fastest -> itineraries.sortedBy { it.duration }
                        PlanSort.LeastWalking -> itineraries.sortedBy { it.walkDistance }
                    }
                    // One time axis for every card so bars are directly comparable
                    val window = remember(itineraries) {
                        TimeWindow(itineraries.minOf { it.startTime }, itineraries.maxOf { it.endTime })
                    }
                    // Ticks the "Go in N min" labels forward while results are on screen
                    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
                    LaunchedEffect(Unit) {
                        while (true) {
                            delay(30_000)
                            now = System.currentTimeMillis()
                        }
                    }

                    val pullState = rememberPullToRefreshState()

                    PullToRefreshBox(
                        isRefreshing = isPlanning,
                        onRefresh = { viewModel.refresh() },
                        state = pullState,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .verticalScroll(rememberScrollState())
                                .fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Sort chips + refresh button
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                // Three sorts and refresh don't always fit: scroll rather than wrap
                                modifier = Modifier.horizontalScroll(rememberScrollState())
                            ) {
                                FilterChip(
                                    selected = sort == PlanSort.ArriveFirst,
                                    onClick = { sort = PlanSort.ArriveFirst },
                                    label = { Text("Arrive first", maxLines = 1, softWrap = false) },
                                    leadingIcon = {
                                        Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(16.dp))
                                    }
                                )
                                FilterChip(
                                    selected = sort == PlanSort.Fastest,
                                    onClick = { sort = PlanSort.Fastest },
                                    label = { Text("Fastest", maxLines = 1, softWrap = false) },
                                    leadingIcon = {
                                        Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(16.dp))
                                    }
                                )
                                FilterChip(
                                    selected = sort == PlanSort.LeastWalking,
                                    onClick = { sort = PlanSort.LeastWalking },
                                    label = { Text("Least walking", maxLines = 1, softWrap = false) },
                                    leadingIcon = {
                                        Icon(Icons.Default.DirectionsWalk, contentDescription = null, modifier = Modifier.size(16.dp))
                                    }
                                )
                                IconButton(
                                    onClick = { viewModel.refresh() },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Refresh,
                                        contentDescription = "Refresh results",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            // Padding matches ItineraryCard's inner padding so ticks line up with bars
                            TimelineAxis(window, modifier = Modifier.padding(horizontal = 16.dp))
                            sorted.forEach { itinerary ->
                                ItineraryCard(
                                    itinerary = itinerary,
                                    window = window,
                                    now = now,
                                    onTap = { onItinerarySelect(itinerary) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ItineraryCard(itinerary: Itinerary, window: TimeWindow, now: Long, onTap: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 6.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.clickable(onClick = onTap).padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // "Go in 5 min" + departure window, duration on the right
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    leaveLabel(itinerary.startTime, now),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "${planFormatTime(itinerary.startTime)} → ${planFormatTime(itinerary.endTime)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "${itinerary.durationMins} min",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(Modifier.height(8.dp))
            ItineraryTimelineBar(itinerary, window)
            Spacer(Modifier.height(6.dp))
            // Subtitle: walk distance + transfers
            val walkKm = itinerary.walkDistance / 1000f
            val walkText = if (walkKm < 1f) "${itinerary.walkDistance}m walk" else "${"%.1f".format(walkKm)}km walk"
            val transferText = when (itinerary.transfers) {
                0 -> "No transfers"
                1 -> "1 transfer"
                else -> "${itinerary.transfers} transfers"
            }
            Text(
                "$walkText · $transferText",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Shared time range across all itineraries, used to position legs on a common axis. */
private data class TimeWindow(val start: Long, val end: Long) {
    private val span: Long get() = (end - start).coerceAtLeast(1)

    fun fraction(t: Long): Float = ((t - start).toFloat() / span).coerceIn(0f, 1f)

    /** Round clock times inside the window, spaced so the axis shows roughly 3–5 labels. */
    fun ticks(): List<Long> {
        val stepMin = when {
            span <= 40 * 60_000L -> 10
            span <= 90 * 60_000L -> 15
            span <= 180 * 60_000L -> 30
            else -> 60
        }
        val stepMs = stepMin * 60_000L
        val first = (start + stepMs - 1) / stepMs * stepMs
        return generateSequence(first) { it + stepMs }.takeWhile { it <= end }.toList()
    }
}

@Composable
private fun TimelineAxis(window: TimeWindow, modifier: Modifier = Modifier) {
    val ticks = remember(window) { window.ticks() }
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    Layout(
        content = {
            ticks.forEach {
                Text(planFormatTime(it), style = MaterialTheme.typography.labelSmall, color = labelColor)
            }
        },
        modifier = modifier.fillMaxWidth()
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val placeables = measurables.map { it.measure(Constraints()) }
        val height = placeables.maxOfOrNull { it.height } ?: 0
        layout(width, height) {
            placeables.forEachIndexed { i, p ->
                // Centre each label on its tick, clamped so edge labels stay on screen
                val centre = (window.fraction(ticks[i]) * width).roundToInt()
                p.place((centre - p.width / 2).coerceIn(0, (width - p.width).coerceAtLeast(0)), 0)
            }
        }
    }
}

/** Walk legs as dots, transit legs as route-coloured pills, sized by duration on the shared axis. */
@Composable
private fun ItineraryTimelineBar(itinerary: Itinerary, window: TimeWindow) {
    val walkColor = MaterialTheme.colorScheme.onSurfaceVariant
    val fallbackColor = MaterialTheme.colorScheme.primary
    BoxWithConstraints(modifier = Modifier.fillMaxWidth().height(24.dp)) {
        val tripStart = maxWidth * window.fraction(itinerary.startTime)
        val tripEnd = maxWidth * window.fraction(itinerary.endTime)
        // Faint baseline under the whole trip so waits between legs read as gaps
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(x = tripStart)
                .width(tripEnd - tripStart)
                .height(2.dp)
                .background(walkColor.copy(alpha = 0.2f))
        )
        itinerary.legs.forEach { leg ->
            val x = maxWidth * window.fraction(leg.startTime)
            val legWidth = maxWidth * window.fraction(leg.endTime) - x
            if (leg.isTransit) {
                val bg = leg.parsedRouteColor() ?: fallbackColor
                Surface(
                    color = bg,
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .offset(x = x)
                        // A short ride still gets room for its route number, over the walk beside it
                        .width(legWidth.coerceAtLeast(30.dp))
                        .fillMaxHeight()
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                                leg.label,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = if (bg.luminance() > 0.6f) Color.Black else Color.White,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Clip
                            )
                    }
                }
            } else {
                WalkDots(
                    color = walkColor.copy(alpha = 0.6f),
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .offset(x = x)
                        .width(legWidth)
                        .height(6.dp)
                )
            }
        }
    }
}

@Composable
private fun WalkDots(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val r = size.height / 2
        var cx = r
        while (cx <= size.width - r) {
            drawCircle(color, radius = r, center = Offset(cx, center.y))
            cx += r * 3
        }
    }
}

private fun ItineraryLeg.parsedRouteColor(): Color? =
    routeColor?.let { runCatching { Color(android.graphics.Color.parseColor("#$it")) }.getOrNull() }

private fun leaveLabel(startTime: Long, now: Long): String {
    val mins = ((startTime - now) / 60_000).toInt()
    return when {
        mins <= 0 -> "Go now"
        mins < 60 -> "Go in $mins min"
        else -> "Leave ${planFormatTime(startTime)}"
    }
}

private fun planFormatTime(epochMs: Long): String {
    val cal = java.util.Calendar.getInstance()
    cal.timeInMillis = epochMs
    val h = cal.get(java.util.Calendar.HOUR)
    val m = cal.get(java.util.Calendar.MINUTE)
    val ampm = if (cal.get(java.util.Calendar.AM_PM) == java.util.Calendar.AM) "AM" else "PM"
    return "%d:%02d %s".format(if (h == 0) 12 else h, m, ampm)
}

@Composable
private fun LegRow(leg: ItineraryLeg) {
    Row(verticalAlignment = Alignment.Top) {
        // Mode icon
        Box(
            modifier = Modifier.size(32.dp),
            contentAlignment = Alignment.Center
        ) {
            if (leg.isTransit) {
                val bgColor = leg.routeColor?.let {
                    runCatching { Color(android.graphics.Color.parseColor("#$it")) }.getOrNull()
                } ?: MaterialTheme.colorScheme.primary
                Surface(color = bgColor, shape = RoundedCornerShape(6.dp)) {
                    Text(
                        leg.label,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            } else {
                Icon(Icons.Default.DirectionsWalk, contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            if (leg.isTransit) {
                Text(
                    "${leg.headsign ?: leg.routeLongName ?: leg.mode}",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    maxLines = 1
                )
                Text(
                    "${leg.from.name} → ${leg.to.name}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            } else {
                val distText = if (leg.distance < 1000) "${leg.distance}m" else "${"%.1f".format(leg.distance / 1000f)}km"
                Text(
                    "Walk $distText (${leg.durationMins} min)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (leg.steps.isNotEmpty()) {
                    Text(
                        leg.steps.first().streetName.ifBlank { "path" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
        }
        // Duration
        Text(
            "${leg.durationMins} min",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PlanField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    isActive: Boolean,
    focusRequester: FocusRequester,
    onFocused: () -> Unit,
    textColor: Color,
    hintColor: Color,
    trailingContent: @Composable () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(color = textColor, fontSize = 15.sp),
            cursorBrush = SolidColor(Color(0xFF1A73E8)),
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester)
                .onFocusChanged { if (it.isFocused) onFocused() },
            decorationBox = { innerTextField ->
                if (value.isEmpty()) {
                    Text(placeholder, color = hintColor, fontSize = 15.sp)
                }
                innerTextField()
            }
        )
        trailingContent()
    }
}

/** How the planner's results are ordered */
private enum class PlanSort { ArriveFirst, Fastest, LeastWalking }
