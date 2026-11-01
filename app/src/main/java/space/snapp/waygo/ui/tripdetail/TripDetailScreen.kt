package space.snapp.waygo.ui.tripdetail

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color as AndroidColor
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path as AndroidPath
import android.graphics.drawable.BitmapDrawable
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DirectionsBoat
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Train
import androidx.compose.material.icons.filled.Tram
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.delay
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import space.snapp.waygo.data.api.models.NextTrip
import space.snapp.waygo.data.api.models.TripDetailResponse
import space.snapp.waygo.data.api.models.TripStop
import space.snapp.waygo.data.api.models.TripVehicle
import space.snapp.waygo.ui.alerts.AlertBannerArea
import space.snapp.waygo.ui.alerts.AlertsUi
import space.snapp.waygo.ui.alerts.rememberServiceAlerts
import space.snapp.waygo.ui.components.LiveIndicator
import space.snapp.waygo.ui.components.delayLabel
import space.snapp.waygo.ui.map.ESRI_DARK_GRAY_BASE
import space.snapp.waygo.ui.map.addDarkCanvasLabels
import space.snapp.waygo.ui.map.createVehicleMarkerIcon
import java.util.Calendar
import java.util.Date

data class TripDetailArgs(
    val tripId: String?,
    val routeId: String,
    val directionId: Int,
    val routeShortName: String,
    val headsign: String
)

private const val WALK_METRES_PER_MIN = 80.0

// Floating header (40 dp buttons + 10 dp vertical padding) plus breathing room
private val HeaderHeight = 76.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripDetailScreen(
    args: TripDetailArgs,
    userLat: Double?,
    userLon: Double?,
    viewModel: TripDetailViewModel,
    onBack: () -> Unit
) {
    val tripDetail by viewModel.tripDetail.collectAsState()
    val nextTrips by viewModel.nextTrips.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val notice by viewModel.notice.collectAsState()
    val scaffoldState = rememberBottomSheetScaffoldState()

    LaunchedEffect(notice) {
        notice?.let {
            scaffoldState.snackbarHostState.showSnackbar(it)
            viewModel.clearNotice()
        }
    }

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mapRef = remember { mutableStateOf<MapView?>(null) }

    BackHandler(onBack = onBack)

    // Location updates feed the next refresh rather than reloading the trip
    LaunchedEffect(userLat, userLon) { viewModel.updateLocation(userLat, userLon) }
    LaunchedEffect(args) {
        viewModel.load(args.tripId, args.routeId, args.directionId, userLat, userLon)
    }
    DisposableEffect(Unit) { onDispose { viewModel.stop() } }

    // Clock for countdowns; minute precision is enough, so tick every 10 s
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) { delay(10_000); now = System.currentTimeMillis() }
    }

    val info = tripDetail?.tripInfo
    val routeColor = parseHexColor(info?.routeColor) ?: Color(0xFF2196F3)
    // Prefer the GTFS text colour (e.g. dark text on pink 412), else pick by contrast
    val routeTextColor = parseHexColor(info?.routeTextColor)
        ?: if (routeColor.luminance() > 0.5f) Color.Black else Color.White
    val routeShortName = info?.routeShortName ?: args.routeShortName
    // Alerts for this route (e.g. track work, diversions)
    val alerts = rememberServiceAlerts(listOfNotNull(info?.routeId ?: args.routeId.takeIf { it.isNotBlank() }), emptyList())
    val headsign = info?.headsign ?: args.headsign

    // Only auto-fit the map when the trip changes, so 10 s refreshes don't fight the user's panning
    var fittedTripId by remember { mutableStateOf<String?>(null) }
    var fitBox by remember { mutableStateOf<BoundingBox?>(null) }

    LaunchedEffect(tripDetail, userLat, userLon) {
        val map = mapRef.value ?: return@LaunchedEffect
        val detail = tripDetail ?: return@LaunchedEffect
        rebuildTripMap(context, map, detail, routeColor.toArgbInt(), routeTextColor.toArgbInt(), userLat, userLon)
        val box = focusBoundingBox(detail, userLat, userLon)
        fitBox = box
        if (fittedTripId != detail.tripInfo.tripId && box != null) {
            fittedTripId = detail.tripInfo.tripId
            map.fitTo(box)
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapRef.value?.onResume()
                Lifecycle.Event.ON_PAUSE -> mapRef.value?.onPause()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val peekHeight = maxHeight * 0.55f
        // Fully expanded sheet stops below the floating header so it is never sliced in half
        // (the M3 drag handle adds ~48 dp above this content)
        val sheetMaxHeight = maxHeight - HeaderHeight - 48.dp
        // Tuck the map just under the sheet's rounded top edge
        val mapHeight = maxHeight - peekHeight + 24.dp

        BottomSheetScaffold(
            scaffoldState = scaffoldState,
            sheetPeekHeight = peekHeight,
            sheetContainerColor = MaterialTheme.colorScheme.surface,
            sheetShadowElevation = 12.dp,
            sheetContent = {
                Box(modifier = Modifier.height(sheetMaxHeight)) {
                    when {
                        isLoading && tripDetail == null -> Box(
                            Modifier.fillMaxWidth().padding(top = 48.dp),
                            contentAlignment = Alignment.TopCenter
                        ) { CircularProgressIndicator(color = routeColor) }

                        error != null && tripDetail == null -> Text(
                            "No upcoming trips found for this direction",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth().padding(32.dp)
                        )

                        tripDetail != null -> CompositionLocalProvider(
                            LocalVehicleIcon provides vehicleIcon(info?.routeType)
                        ) {
                            TripSheetContent(
                                detail = tripDetail!!,
                                nextTrips = nextTrips,
                                routeColor = routeColor,
                                routeTextColor = routeTextColor,
                                userLat = userLat,
                                userLon = userLon,
                                now = now,
                                onSelectTrip = viewModel::switchTrip,
                                alerts = alerts
                            )
                        }
                    }
                }
            }
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Map sits above the sheet's peek so auto-fit frames the visible area;
                // clipToBounds stops osmdroid painting polylines outside its view
                AndroidView(
                    factory = { ctx ->
                        MapView(ctx).apply {
                            setTileSource(ESRI_DARK_GRAY_BASE)
                            addDarkCanvasLabels(ctx)
                            setMultiTouchControls(true)
                            controller.setZoom(13.0)
                            controller.setCenter(GeoPoint(-27.4698, 153.0251))
                        }.also { mapRef.value = it }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(mapHeight)
                        .clipToBounds()
                )

                TripHeader(
                    routeShortName = routeShortName,
                    headsign = headsign,
                    routeType = info?.routeType,
                    routeColor = routeColor,
                    routeTextColor = routeTextColor,
                    canSwitchDirection = info != null,
                    onBack = onBack,
                    onSwitchDirection = {
                        fittedTripId = null
                        viewModel.switchDirection()
                    }
                )

                // Recenter on the vehicle / your stop
                SmallFloatingActionButton(
                    onClick = { fitBox?.let { mapRef.value?.fitTo(it) } },
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = routeColor,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 76.dp, end = 12.dp)
                ) {
                    Icon(Icons.Default.MyLocation, contentDescription = "Recenter map")
                }
            }
        }
    }
}

@Composable
private fun TripHeader(
    routeShortName: String,
    headsign: String,
    routeType: Int?,
    routeColor: Color,
    routeTextColor: Color,
    canSwitchDirection: Boolean,
    onBack: () -> Unit,
    onSwitchDirection: () -> Unit
) {
    // Gradient so the header reads over any map tiles without a hard bar
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(Color.Black.copy(alpha = 0.75f), Color.Transparent)
                )
            )
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RoundIconButton(Icons.Default.ArrowBack, "Back", onBack)
        Spacer(Modifier.width(8.dp))
        Surface(color = routeColor, shape = RoundedCornerShape(10.dp)) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isRail(routeType)) {
                    // Rail short names are internal codes (SPRP, NAIP) — icon only, same as Near Me
                    Icon(
                        vehicleIcon(routeType),
                        contentDescription = "Train",
                        tint = routeTextColor,
                        modifier = Modifier.size(28.dp).padding(vertical = 2.dp)
                    )
                } else {
                    Icon(
                        vehicleIcon(routeType),
                        contentDescription = null,
                        tint = routeTextColor,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        routeShortName,
                        color = routeTextColor,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "Towards",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.7f)
            )
            Text(
                headsign,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (canSwitchDirection) {
            RoundIconButton(Icons.Default.SwapHoriz, "Switch direction", onSwitchDirection)
        }
    }
}

@Composable
private fun RoundIconButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
        modifier = Modifier.size(40.dp)
    ) {
        IconButton(onClick = onClick) {
            Icon(icon, contentDescription = description)
        }
    }
}

/** Display rows for the stop timeline, including where the live vehicle sits. */
private sealed interface TimelineItem {
    data class EarlierToggle(val count: Int, val expanded: Boolean) : TimelineItem
    data class StopItem(
        val stop: TripStop,
        val isFirst: Boolean,
        val isLast: Boolean,
        val vehicleHere: Boolean,
        val passed: Boolean
    ) : TimelineItem
    data object VehicleBetween : TimelineItem
}

@Composable
private fun TripSheetContent(
    detail: TripDetailResponse,
    nextTrips: List<NextTrip>,
    routeColor: Color,
    routeTextColor: Color,
    userLat: Double?,
    userLon: Double?,
    now: Long,
    onSelectTrip: (String) -> Unit,
    alerts: AlertsUi
) {
    var showEarlier by remember(detail.tripInfo.tripId) { mutableStateOf(false) }
    val stops = detail.stops
    val nearest = stops.firstOrNull { it.isNearest }
    // Only a fresh fix places the bus on the timeline; a stale one is mentioned, not trusted
    val vehicle = detail.vehicle?.takeIf { it.isLive }
    val staleVehicle = detail.vehicle?.takeUnless { it.isLive }

    val items = remember(detail, showEarlier) { buildTimeline(stops, vehicle, showEarlier) }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        // Always present (placeholders until next-trips loads): inserting it later above the
        // stops makes LazyColumn keep the stops anchored and push the tiles out of view
        item(key = "tiles") {
            if (nextTrips.isEmpty()) {
                DepartureTilePlaceholders()
            } else {
                DepartureTiles(
                    trips = nextTrips,
                    currentTripId = detail.tripInfo.tripId,
                    routeColor = routeColor,
                    routeTextColor = routeTextColor,
                    now = now,
                    onSelect = onSelectTrip
                )
            }
        }
        item(key = "status") {
            // Once this bus has gone past your stop, point at the next one instead
            val nextAtYourStop = nextTrips.firstOrNull { it.tripId != detail.tripInfo.tripId }
            LiveStatusLine(
                stops = stops, nearest = nearest, vehicle = vehicle, staleVehicle = staleVehicle,
                routeColor = routeColor, now = now,
                nearestPassed = nearest?.isPassed(vehicle) == true,
                nextTrip = nextAtYourStop,
                onShowNext = { nextAtYourStop?.let { onSelectTrip(it.tripId) } }
            )
        }
        if (alerts.visible.isNotEmpty()) {
            item(key = "alerts") {
                AlertBannerArea(alerts, Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
            }
        }
        items(items, key = { item ->
            when (item) {
                is TimelineItem.EarlierToggle -> "earlier"
                is TimelineItem.StopItem -> "stop-${item.stop.stopId}-${item.stop.scheduledTime}"
                TimelineItem.VehicleBetween -> "vehicle"
            }
        }) { item ->
            when (item) {
                is TimelineItem.EarlierToggle -> EarlierStopsToggle(item.count, item.expanded, routeColor) {
                    showEarlier = !showEarlier
                }
                is TimelineItem.StopItem -> StopTimelineRow(
                    stop = item.stop,
                    isFirst = item.isFirst,
                    isLast = item.isLast,
                    vehicleHere = item.vehicleHere,
                    passed = item.passed,
                    routeColor = routeColor,
                    routeTextColor = routeTextColor,
                    walkMins = if (item.stop.isNearest) walkMinutes(userLat, userLon, item.stop) else null,
                    now = now
                )
                TimelineItem.VehicleBetween -> VehicleBetweenRow(vehicle, routeColor, routeTextColor)
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

private fun buildTimeline(stops: List<TripStop>, vehicle: TripVehicle?, showEarlier: Boolean): List<TimelineItem> {
    val vehicleSeq = vehicle?.currentStopSequence
    val passedCount = stops.indexOfFirst { !it.isPassed(vehicle) }.let { if (it < 0) stops.size else it }
    val result = mutableListOf<TimelineItem>()
    if (passedCount > 0 && passedCount < stops.size) {
        result += TimelineItem.EarlierToggle(passedCount, showEarlier)
    }
    stops.forEachIndexed { i, stop ->
        if (i < passedCount && !showEarlier && passedCount < stops.size) return@forEachIndexed
        val atThisStop = vehicleSeq != null && stop.stopSequence == vehicleSeq
        // Vehicle heading to this stop: draw it on the line just above
        if (atThisStop && vehicle?.isStoppedAt == false && i > 0) result += TimelineItem.VehicleBetween
        result += TimelineItem.StopItem(
            stop = stop,
            isFirst = i == 0,
            isLast = i == stops.lastIndex,
            vehicleHere = atThisStop && vehicle?.isStoppedAt == true,
            passed = stop.isPassed(vehicle)
        )
    }
    return result
}

/**
 * A stop counts as passed once its time is gone or the tracked vehicle is beyond it.
 * The time alone lags: GTFS-RT drops updates for stops the bus has left, so a just-passed
 * stop still reads as "upcoming" for up to a minute.
 */
private fun TripStop.isPassed(vehicle: TripVehicle?): Boolean {
    if (!isUpcoming) return true
    val seq = stopSequence ?: return false
    val vehicleSeq = vehicle?.currentStopSequence ?: return false
    return seq < vehicleSeq
}

@Composable
private fun DepartureTiles(
    trips: List<NextTrip>,
    currentTripId: String,
    routeColor: Color,
    routeTextColor: Color,
    now: Long,
    onSelect: (String) -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(trips, key = { it.tripId }) { trip ->
            val selected = trip.tripId == currentTripId
            val bg = if (selected) routeColor else MaterialTheme.colorScheme.surfaceVariant
            val fg = if (selected) routeTextColor else MaterialTheme.colorScheme.onSurface
            val departure = trip.departureDate ?: todayAt(trip.estimatedTime ?: trip.scheduledTime)
            val mins = departure?.let { ((it.time - now) / 60_000).toInt() }
            val isLive = trip.isRealtime ?: (trip.estimatedTime != null)

            Surface(
                color = bg,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .size(TileWidth, TileHeight)
                    .clickable { onSelect(trip.tripId) }
            ) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    // Big number + small unit line; clock times split "8:17" / "PM" so they fit the tile
                    val clock = formatClock(departure)
                    val (big, unit) = when {
                        mins == null || mins >= 60 -> clock.substringBefore(" ") to clock.substringAfter(" ", "")
                        mins <= 0 -> "Now" to " "
                        else -> "$mins" to "min"
                    }
                    Row(verticalAlignment = Alignment.Top) {
                        Text(
                            big,
                            color = fg,
                            fontSize = if (big.length > 3) 26.sp else 34.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1
                        )
                        if (isLive) {
                            Spacer(Modifier.width(2.dp))
                            LiveIndicator(color = fg, modifier = Modifier.size(12.dp))
                        }
                    }
                    Text(unit, color = fg.copy(alpha = 0.8f), style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.height(2.dp))
                    val (label, _) = delayLabel(isLive, trip.delaySeconds)
                    Text(
                        label,
                        color = fg.copy(alpha = 0.85f),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

private val TileWidth = 104.dp
private val TileHeight = 112.dp

@Composable
private fun DepartureTilePlaceholders() {
    Row(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        repeat(3) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.size(TileWidth, TileHeight)
            ) {}
        }
    }
}

/** One-line summary: where the vehicle is relative to your nearest stop, or why we can't say. */
@Composable
private fun LiveStatusLine(
    stops: List<TripStop>,
    nearest: TripStop?,
    vehicle: TripVehicle?,
    staleVehicle: TripVehicle?,
    routeColor: Color,
    now: Long,
    nearestPassed: Boolean = false,
    nextTrip: NextTrip? = null,
    onShowNext: () -> Unit = {}
) {
    val first = stops.firstOrNull()
    // Trip hasn't left its first stop yet (and no live bus says otherwise)
    val notStarted = first != null && first.departureDate.time - now > 60_000 &&
        (vehicle == null || (vehicle.currentStopSequence ?: 0) <= (first.stopSequence ?: 1))
    val vehicleSeq = vehicle?.currentStopSequence
    val nearestSeq = nearest?.stopSequence

    val nextMins = nextTrip?.let { t ->
        (t.departureDate ?: todayAt(t.estimatedTime ?: t.scheduledTime))?.let { ((it.time - now) / 60_000).toInt() }
    }
    val (icon, text) = when {
        nearestPassed && nearest != null -> Icons.Default.History to buildString {
            append("Passed your stop")
            if (nextMins != null) append(if (nextMins >= 1) " · next in $nextMins min ›" else " · next one due now ›")
        }
        notStarted -> Icons.Default.Schedule to "Trip starts ${formatClock(first!!.departureDate)} at ${first.stopName}"
        vehicle != null && vehicleSeq != null && nearestSeq != null -> {
            val away = nearestSeq - vehicleSeq + if (vehicle.isStoppedAt) 0 else 1
            Icons.Default.MyLocation to when {
                away <= 0 -> "Arriving at your nearest stop"
                away == 1 -> "1 stop from your nearest stop"
                else -> "$away stops from your nearest stop"
            }
        }
        vehicle != null -> null to "Vehicle is live on the map"
        staleVehicle != null -> Icons.Default.History to
            "Bus last seen ${staleVehicle.ageSeconds?.let { formatAge(it) } ?: "a while ago"}"
        stops.any { it.hasLiveTime } -> null to "Live times · vehicle not yet tracking"
        else -> null to "Scheduled times · no live tracking"
    }
    // Once the bus has passed you, its age/position no longer matter
    val age = vehicle?.ageSeconds?.takeIf { !nearestPassed }?.let { formatAge(it) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (nearestPassed && nextTrip != null) Modifier.clickable(onClick = onShowNext) else Modifier)
            .padding(horizontal = 20.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        when {
            icon != null -> Icon(icon, contentDescription = null, tint = routeColor, modifier = Modifier.size(18.dp))
            vehicle != null || stops.any { it.hasLiveTime } -> LiveIndicator(color = routeColor, modifier = Modifier.size(14.dp))
        }
        if (icon != null || vehicle != null || stops.any { it.hasLiveTime }) Spacer(Modifier.width(8.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (age != null) {
            Text(
                "updated $age",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun EarlierStopsToggle(count: Int, expanded: Boolean, routeColor: Color, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .height(40.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.width(56.dp).fillMaxHeight()) {
            DottedLine(color = MaterialTheme.colorScheme.outline, modifier = Modifier.align(Alignment.Center).fillMaxHeight().width(4.dp))
        }
        Text(
            if (expanded) "Hide earlier stops" else "$count earlier stop${if (count == 1) "" else "s"}",
            style = MaterialTheme.typography.labelLarge,
            color = routeColor
        )
        Icon(
            if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
            contentDescription = null,
            tint = routeColor,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun StopTimelineRow(
    stop: TripStop,
    isFirst: Boolean,
    isLast: Boolean,
    vehicleHere: Boolean,
    passed: Boolean,
    routeColor: Color,
    routeTextColor: Color,
    walkMins: Int?,
    now: Long
) {
    val passedColor = MaterialTheme.colorScheme.outline
    val lineColor = if (passed) passedColor else routeColor
    val surface = MaterialTheme.colorScheme.surface
    val dimText = MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .height(IntrinsicSize.Min)
            .then(if (stop.isNearest) Modifier.background(routeColor.copy(alpha = 0.10f)) else Modifier)
    ) {
        // Timeline gutter: line segments above/below plus the stop dot (or the vehicle)
        Box(modifier = Modifier.width(56.dp).fillMaxHeight(), contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val x = size.width / 2
                val w = 6.dp.toPx()
                if (!isFirst) drawLine(lineColor, Offset(x, 0f), Offset(x, size.height / 2), w)
                if (!isLast) drawLine(lineColor, Offset(x, size.height / 2), Offset(x, size.height), w)
                if (!vehicleHere) {
                    val c = Offset(x, size.height / 2)
                    when {
                        stop.isNearest -> {
                            drawCircle(routeColor, radius = 11.dp.toPx(), center = c)
                            drawCircle(surface, radius = 5.dp.toPx(), center = c)
                        }
                        passed -> drawCircle(passedColor, radius = 5.dp.toPx(), center = c)
                        else -> {
                            drawCircle(surface, radius = 7.dp.toPx(), center = c)
                            drawCircle(routeColor, radius = 7.dp.toPx(), center = c, style = Stroke(3.dp.toPx()))
                        }
                    }
                }
            }
            if (vehicleHere) VehicleBadge(routeColor, routeTextColor)
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = if (stop.isNearest) 14.dp else 10.dp)
        ) {
            Text(
                stop.stopName,
                style = if (stop.isNearest)
                    MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                else MaterialTheme.typography.bodyLarge,
                color = if (passed) dimText else MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (stop.isNearest && passed) {
                Spacer(Modifier.height(4.dp))
                Text(
                    // No time: GTFS-RT drops passed stops, so we'd only have the schedule
                    "This bus has passed your stop",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else if (stop.isNearest) {
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (walkMins != null) {
                        Surface(color = routeColor.copy(alpha = 0.2f), shape = RoundedCornerShape(8.dp)) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.DirectionsWalk, null, Modifier.size(14.dp), tint = routeColor)
                                Spacer(Modifier.width(2.dp))
                                Text("$walkMins min", style = MaterialTheme.typography.labelMedium, color = routeColor)
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                    }
                    val secs = ((stop.departureDate.time - now) / 1000).toInt()
                    Text(
                        when {
                            secs < 60 -> "Departing now"
                            secs < 3600 -> "Departs in ${secs / 60} min"
                            else -> "Departs in ${secs / 3600}h ${(secs % 3600) / 60}m"
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = routeColor
                    )
                }
            }
        }

        // Time column: live/scheduled clock time with delay status beneath
        Column(
            modifier = Modifier.padding(start = 8.dp, end = 16.dp).align(Alignment.CenterVertically),
            horizontalAlignment = Alignment.End
        ) {
            if (passed) {
                Text(formatClock24(stop.scheduledTime), style = MaterialTheme.typography.bodyMedium, color = dimText)
            } else {
                Text(
                    formatClock(stop.departureDate),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                )
                val (label, color) = delayLabel(stop.hasLiveTime, stop.delaySeconds)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Strike through the timetable time when the live time differs
                    if (stop.hasLiveTime && (stop.delaySeconds ?: 0).let { it >= 60 || it <= -60 }) {
                        Text(
                            formatClock24(stop.scheduledTime),
                            style = MaterialTheme.typography.labelSmall.copy(textDecoration = TextDecoration.LineThrough),
                            color = dimText
                        )
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(label, style = MaterialTheme.typography.labelSmall, color = color ?: dimText)
                }
            }
        }
    }
}

/** The vehicle drawn on the line between the last stop it passed and the one it's heading to. */
@Composable
private fun VehicleBetweenRow(vehicle: TripVehicle?, routeColor: Color, routeTextColor: Color) {
    Row(
        modifier = Modifier.fillMaxWidth().height(44.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.width(56.dp).fillMaxHeight(), contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val x = size.width / 2
                drawLine(routeColor, Offset(x, 0f), Offset(x, size.height), 6.dp.toPx())
            }
            VehicleBadge(routeColor, routeTextColor)
        }
        Text(
            "On the way" + (vehicle?.ageSeconds?.let { " · ${formatAge(it)}" } ?: ""),
            style = MaterialTheme.typography.labelMedium,
            color = routeColor
        )
    }
}

// Icon for the live vehicle on the timeline (bus / train / ferry / tram), set per trip
private val LocalVehicleIcon = staticCompositionLocalOf { Icons.Default.DirectionsBus }

@Composable
private fun VehicleBadge(routeColor: Color, routeTextColor: Color) {
    Surface(
        color = routeColor,
        shape = CircleShape,
        shadowElevation = 4.dp,
        modifier = Modifier.size(30.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(LocalVehicleIcon.current, contentDescription = "Vehicle", tint = routeTextColor, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun DottedLine(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val r = size.width / 2
        var y = r
        while (y < size.height) {
            drawCircle(color, radius = r, center = Offset(size.width / 2, y))
            y += r * 3.5f
        }
    }
}

// ── Formatting helpers ─────────────────────────────────────────────────────

private fun formatAge(seconds: Int): String = when {
    seconds < 60 -> "${seconds}s ago"
    else -> "${seconds / 60}m ago"
}

private fun formatClock(date: Date?): String {
    if (date == null) return "—"
    val cal = Calendar.getInstance().apply { time = date }
    val h = cal.get(Calendar.HOUR).let { if (it == 0) 12 else it }
    val ampm = if (cal.get(Calendar.AM_PM) == Calendar.AM) "AM" else "PM"
    return "%d:%02d %s".format(h, cal.get(Calendar.MINUTE), ampm)
}

/** API sends timetable times as 24 h "HH:mm"; show them in the same 12 h style as live times. */
private fun formatClock24(hhmm: String): String = todayAt(hhmm)?.let { formatClock(it) } ?: hhmm

private fun todayAt(hhmm: String): Date? {
    val parts = hhmm.split(":").mapNotNull { it.toIntOrNull() }
    if (parts.size < 2) return null
    return Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, parts[0] % 24)
        set(Calendar.MINUTE, parts[1])
        set(Calendar.SECOND, 0)
    }.time
}

private fun walkMinutes(userLat: Double?, userLon: Double?, stop: TripStop): Int? {
    if (userLat == null || userLon == null) return null
    val metres = haversineMeters(userLat, userLon, stop.lat, stop.lon)
    // Beyond ~2 km a walk time isn't meaningful (user is likely elsewhere)
    if (metres > 2000) return null
    return (metres / WALK_METRES_PER_MIN).toInt().coerceAtLeast(1)
}

private fun isRail(routeType: Int?): Boolean = routeType == 1 || routeType == 2

private fun vehicleIcon(routeType: Int?): ImageVector = when (routeType) {
    0 -> Icons.Default.Tram
    1, 2 -> Icons.Default.Train
    4 -> Icons.Default.DirectionsBoat
    else -> Icons.Default.DirectionsBus
}

private fun parseHexColor(hex: String?): Color? =
    hex?.takeIf { it.isNotBlank() }?.let {
        runCatching { Color(AndroidColor.parseColor("#${it.trimStart('#')}")) }.getOrNull()
    }

private fun Color.toArgbInt(): Int =
    AndroidColor.argb((alpha * 255).toInt(), (red * 255).toInt(), (green * 255).toInt(), (blue * 255).toInt())

// ── Map ────────────────────────────────────────────────────────────────────

/** Frame what matters: the vehicle, your stop, you, and the next few stops after yours. */
private fun focusBoundingBox(detail: TripDetailResponse, userLat: Double?, userLon: Double?): BoundingBox? {
    val points = mutableListOf<GeoPoint>()
    detail.vehicle?.takeIf { it.isLive }?.let { points += GeoPoint(it.lat, it.lon) }
    val nearestIdx = detail.stops.indexOfFirst { it.isNearest }
    if (nearestIdx >= 0) {
        val nearest = detail.stops[nearestIdx]
        points += GeoPoint(nearest.lat, nearest.lon)
        if (userLat != null && userLon != null &&
            haversineMeters(userLat, userLon, nearest.lat, nearest.lon) <= 1000.0
        ) points += GeoPoint(userLat, userLon)
        detail.stops.drop(nearestIdx + 1).take(3).forEach { points += GeoPoint(it.lat, it.lon) }
    }
    if (points.size < 2) {
        points += detail.stops.filter { it.isUpcoming }.ifEmpty { detail.stops }.map { GeoPoint(it.lat, it.lon) }
    }
    val box = runCatching { BoundingBox.fromGeoPoints(points) }.getOrNull() ?: return null
    // Extra headroom at the top so nothing lands under the floating header / recenter button
    val latSpan = (box.latNorth - box.latSouth).coerceAtLeast(0.002)
    val lonSpan = (box.lonEast - box.lonWest).coerceAtLeast(0.002)
    return BoundingBox(
        box.latNorth + latSpan * 0.35,
        box.lonEast + lonSpan * 0.08,
        box.latSouth - latSpan * 0.08,
        box.lonWest - lonSpan * 0.08
    )
}

private fun MapView.fitTo(box: BoundingBox) {
    val padding = (64 * context.resources.displayMetrics.density).toInt()
    post { runCatching { zoomToBoundingBox(box, true, padding) } }
}

private fun rebuildTripMap(
    context: android.content.Context,
    map: MapView,
    detail: TripDetailResponse,
    routeColorInt: Int,
    routeTextColorInt: Int,
    userLat: Double?,
    userLon: Double?
) {
    val dp = context.resources.displayMetrics.density
    map.overlays.clear()
    // clear() also drops the street-name labels overlay, so put it back
    map.addDarkCanvasLabels(context)

    // Full route shape polyline
    if (detail.shape.size >= 2) {
        val poly = Polyline().apply {
            setPoints(detail.shape.map { GeoPoint(it[0], it[1]) })
            outlinePaint.color = routeColorInt
            outlinePaint.strokeWidth = 6f * dp
            outlinePaint.strokeCap = Paint.Cap.ROUND
        }
        map.overlays.add(poly)
    }

    // Walking route to nearest stop (if user location is known and ≤ 1km away).
    // Walk time is shown in the sheet, so the map only draws the dashed path.
    val nearestStop = detail.stops.firstOrNull { it.isNearest }
    if (nearestStop != null && userLat != null && userLon != null &&
        haversineMeters(userLat, userLon, nearestStop.lat, nearestStop.lon) <= 1000.0
    ) {
        val walkPoly = Polyline().apply {
            setPoints(listOf(GeoPoint(userLat, userLon), GeoPoint(nearestStop.lat, nearestStop.lon)))
            outlinePaint.color = AndroidColor.rgb(100, 180, 255)
            outlinePaint.strokeWidth = 3f * dp
            outlinePaint.pathEffect = DashPathEffect(floatArrayOf(10f * dp, 8f * dp), 0f)
        }
        map.overlays.add(walkPoly)
        map.overlays.add(Marker(map).apply {
            position = GeoPoint(userLat, userLon)
            icon = createUserDotIcon(context)
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            setInfoWindow(null)
        })
    }

    // Stop markers
    for (stop in detail.stops) {
        map.overlays.add(Marker(map).apply {
            position = GeoPoint(stop.lat, stop.lon)
            icon = createTripStopIcon(context, stop, routeColorInt)
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            title = stop.stopName
            setInfoWindow(null)
        })
    }

    // Vehicle marker on top: route pill with direction arrow and data age
    detail.vehicle?.let { v ->
        map.overlays.add(Marker(map).apply {
            position = GeoPoint(v.lat, v.lon)
            icon = createVehicleMarkerIcon(
                context,
                // No text on rail markers: their short names are internal codes
                label = if (isRail(detail.tripInfo.routeType)) "" else detail.tripInfo.routeShortName,
                routeColor = routeColorInt,
                routeTextColor = routeTextColorInt,
                // A stale fix gets no arrow: we don't know where it's heading now
                heading = if (v.isLive) v.heading else null,
                age = v.ageSeconds?.let { formatAge(it).removeSuffix(" ago") }
            )
            // Faded: last known position, not live
            if (!v.isLive) alpha = 0.45f
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            setInfoWindow(null)
        })
    }

    map.invalidate()
}

private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r = 6371000.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
            Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
            Math.sin(dLon / 2) * Math.sin(dLon / 2)
    return r * 2 * Math.asin(Math.sqrt(a))
}

private fun createUserDotIcon(context: android.content.Context): BitmapDrawable {
    val dp = context.resources.displayMetrics.density
    val size = (20 * dp).toInt().coerceAtLeast(8)
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    // Halo
    paint.color = AndroidColor.argb(70, 33, 150, 243)
    paint.style = Paint.Style.FILL
    canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
    // Inner dot
    paint.color = AndroidColor.rgb(33, 150, 243)
    canvas.drawCircle(size / 2f, size / 2f, size / 3.5f, paint)
    // White border
    paint.color = AndroidColor.WHITE
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = 1.5f * dp
    canvas.drawCircle(size / 2f, size / 2f, size / 3.5f, paint)
    return BitmapDrawable(context.resources, bitmap)
}

private fun createTripStopIcon(
    context: android.content.Context,
    stop: TripStop,
    routeColorInt: Int
): BitmapDrawable {
    val dp = context.resources.displayMetrics.density
    val sizeDp = if (stop.isNearest) 18 else if (stop.isUpcoming) 11 else 8
    val size = (sizeDp * dp).toInt().coerceAtLeast(4)
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(bitmap)

    val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = when {
            stop.isNearest -> routeColorInt
            stop.isUpcoming -> AndroidColor.WHITE
            else -> AndroidColor.rgb(100, 100, 100)
        }
    }
    canvas.drawCircle(size / 2f, size / 2f, size / 2f - dp, fillPaint)

    if (stop.isNearest || stop.isUpcoming) {
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2f * dp
            color = if (stop.isNearest) AndroidColor.WHITE else routeColorInt
        }
        canvas.drawCircle(size / 2f, size / 2f, size / 2f - dp, strokePaint)
    }

    return BitmapDrawable(context.resources, bitmap)
}
