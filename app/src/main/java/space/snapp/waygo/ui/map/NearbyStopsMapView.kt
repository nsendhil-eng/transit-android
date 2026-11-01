package space.snapp.waygo.ui.map

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.LocationSearching
import androidx.compose.material.icons.filled.MyLocation
import android.view.MotionEvent
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.unit.Dp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.shape.RoundedCornerShape
import space.snapp.waygo.ui.tripdetail.PRIORITY_KEY
import space.snapp.waygo.ui.tripdetail.StopLabel
import space.snapp.waygo.ui.tripdetail.StopLabelsOverlay
import space.snapp.waygo.ui.tripdetail.mapStopName
import kotlinx.coroutines.delay
import org.osmdroid.config.Configuration
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import space.snapp.waygo.data.api.TransitApiService
import space.snapp.waygo.data.api.models.Departure
import space.snapp.waygo.data.api.models.Stop
import space.snapp.waygo.data.api.models.TripLive
import space.snapp.waygo.data.api.models.VehicleType
import space.snapp.waygo.ui.components.icon
import space.snapp.waygo.ui.departures.DeparturesScreen
import space.snapp.waygo.ui.departures.DeparturesViewModel

private const val BRISBANE_LAT = -27.4698
private const val BRISBANE_LON = 153.0251
private const val STOP_LIVE_REFRESH_MS = 5_000L
private const val CALLOUT_REFRESH_MS = 30_000L
private const val PAN_SETTLE_MS = 1_500L
private const val MAX_CALLOUT_STOPS = 30
/** Refetch nearby stops only after moving this far (GPS updates every 10 s) */
private const val REFETCH_STOPS_AFTER_M = 150.0
/** Drag handle strip at the top of the bottom panel */
private val SheetHandleHeight = 20.dp
/** Room left above the panel when dragged all the way up: status bar, search and alert buttons */
private val SHEET_TOP_GAP = 140.dp
/** Zoom for a focused pair of stops (both sides of a road), so the two dots separate */
private const val PAIR_FOCUS_ZOOM = 17.5
/** Below this zoom only the mode-card stops are labelled */
private const val ALL_STOP_LABELS_ZOOM = 15.5

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NearbyStopsMapView(
    userLat: Double?,
    userLon: Double?,
    userAccuracyM: Float? = null,
    onAddStops: (List<Stop>) -> Unit,
    onDepartureClick: ((Departure) -> Unit)? = null,
    /** Shown at the end of the bottom panel's list (the save prompt) */
    sheetFooter: @Composable () -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val api = remember { TransitApiService.instance }

    var stopGroups by remember { mutableStateOf<List<StopGroup>>(emptyList()) }
    var tripLiveData by remember { mutableStateOf<List<TripLive>>(emptyList()) }
    val selectedGroupState = remember { mutableStateOf<StopGroup?>(null) }
    val mapRef = remember { mutableStateOf<MapView?>(null) }
    val youOverlay = remember { UserLocationOverlay(context) }
    val heading by rememberCompassHeading()
    // The map follows you until you pan it; the re-centre button turns following back on
    var following by remember { mutableStateOf(true) }
    // Next departures for the nearby stops, shown as one card per mode along the bottom
    var nearbyDeps by remember { mutableStateOf<List<Departure>>(emptyList()) }
    // The stop whose card was tapped once: highlighted on the map; a second tap opens it
    var focusedGroupId by remember { mutableStateOf<String?>(null) }
    // When the map was last dragged; refreshes wait until it settles
    val lastPanMs = remember { longArrayOf(0L) }
    val lastStopsFetch = remember { mutableStateOf<GeoPoint?>(null) }
    // When departures last arrived; the cards dim if refreshes keep failing
    var depsUpdatedMs by remember { mutableLongStateOf(0L) }
    // Rebuilt every 15 s too, so a service that has just left drops off without waiting for a fetch
    var cardsTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(15_000); cardsTick++ } }
    val modeCards = remember(stopGroups, nearbyDeps, userLat, userLon, cardsTick) {
        buildModeCards(stopGroups, nearbyDeps, userLat, userLon)
    }
    val nearbyStops = remember(stopGroups, nearbyDeps, userLat, userLon, cardsTick) {
        buildNearbyStops(stopGroups, nearbyDeps, userLat, userLon)
    }

    LaunchedEffect(Unit) {
        Configuration.getInstance().userAgentValue = context.packageName
    }

    // Move the dot (and the map, while following) whenever location changes
    LaunchedEffect(userLat, userLon, userAccuracyM) {
        if (userLat == null || userLon == null) return@LaunchedEffect
        youOverlay.position = GeoPoint(userLat, userLon)
        youOverlay.accuracyM = userAccuracyM
        if (following) mapRef.value?.controller?.animateTo(youOverlay.position)
        mapRef.value?.invalidate()
    }
    LaunchedEffect(heading) {
        youOverlay.heading = heading
        mapRef.value?.invalidate()
    }

    // Fetch nearby stops on the first fix, then only after moving a fair way
    LaunchedEffect(userLat, userLon) {
        val lat = userLat ?: BRISBANE_LAT
        val lon = userLon ?: BRISBANE_LON
        val last = lastStopsFetch.value
        if (last != null && approxMeters(last.latitude, last.longitude, lat, lon) < REFETCH_STOPS_AFTER_M) return@LaunchedEffect
        runCatching { api.stopsNearMe(lat, lon, radius = 500) }
            .onSuccess { stops ->
                lastStopsFetch.value = GeoPoint(lat, lon)
                stopGroups = groupStops(stops)
            }
    }

    // Departures for the nearby stops: every 30 s while the app is in front, never mid-pan.
    // Keyed on the stop codes, so a new GPS fix that finds the same stops doesn't refetch.
    val stopCodes = remember(stopGroups) {
        stopGroups.flatMap { g -> g.stops.mapNotNull { it.stopCode?.takeIf(String::isNotBlank) } }
            .distinct().take(MAX_CALLOUT_STOPS)
    }
    LaunchedEffect(stopCodes) {
        if (stopCodes.isEmpty()) { nearbyDeps = emptyList(); return@LaunchedEffect }
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                while (System.currentTimeMillis() - lastPanMs[0] < PAN_SETTLE_MS) delay(PAN_SETTLE_MS)
                runCatching { api.departures(stopCodes.joinToString(","), perStop = MODE_CARD_ROWS, withHeading = 1) }
                    .onSuccess { nearbyDeps = it; depsUpdatedMs = System.currentTimeMillis() }
                delay(CALLOUT_REFRESH_MS)
            }
        }
    }

    // Poll stop-live every 5 s while a stop group is selected; cancel + clear when dismissed
    LaunchedEffect(selectedGroupState.value) {
        val group = selectedGroupState.value
        if (group == null) {
            tripLiveData = emptyList()
            return@LaunchedEffect
        }
        val stopIds = group.stops.joinToString(",") { it.id }
        while (true) {
            runCatching { api.stopLive(stopIds) }
                .onSuccess { tripLiveData = it.trips }
            delay(STOP_LIVE_REFRESH_MS)
        }
    }

    // Rebuild overlays whenever stops or live trip data changes
    LaunchedEffect(stopGroups, tripLiveData, focusedGroupId, modeCards.map { c -> c.sides.map { it.group.id to it.heading } }) {
        val map = mapRef.value ?: return@LaunchedEffect
        val dp = context.resources.displayMetrics.density
        map.overlays.clear()
        // clear() also drops the street-name labels overlay, so put it back
        map.addDarkCanvasLabels(context)

        // Draw route shapes + vehicle markers for the selected stop's upcoming trips
        for (trip in tripLiveData) {
            if (trip.hasPassed) continue

            val routeColorInt = trip.routeColor?.let {
                runCatching { AndroidColor.parseColor("#${it.trimStart('#')}") }.getOrNull()
            } ?: AndroidColor.GRAY

            // Shape before the stop — dimmed (30% opacity)
            if (trip.shapeBefore.size >= 2) {
                val poly = Polyline().apply {
                    setPoints(trip.shapeBefore.map { GeoPoint(it[0], it[1]) })
                    outlinePaint.color = AndroidColor.argb(
                        77,
                        AndroidColor.red(routeColorInt),
                        AndroidColor.green(routeColorInt),
                        AndroidColor.blue(routeColorInt)
                    )
                    outlinePaint.strokeWidth = 5f * dp
                }
                map.overlays.add(poly)
            }

            // Shape after the stop — full color
            if (trip.shapeAfter.size >= 2) {
                val poly = Polyline().apply {
                    setPoints(trip.shapeAfter.map { GeoPoint(it[0], it[1]) })
                    outlinePaint.color = routeColorInt
                    outlinePaint.strokeWidth = 5f * dp
                }
                map.overlays.add(poly)
            }

            // Vehicle marker (only if real-time position available and not past stop)
            trip.vehicle?.let { v ->
                val textColor = trip.routeTextColor?.let {
                    runCatching { AndroidColor.parseColor("#${it.trimStart('#')}") }.getOrNull()
                } ?: if (androidx.core.graphics.ColorUtils.calculateLuminance(routeColorInt) > 0.5) AndroidColor.BLACK else AndroidColor.WHITE
                val marker = Marker(map).apply {
                    position = GeoPoint(v.lat, v.lon)
                    icon = createVehicleMarkerIcon(
                        context,
                        // Rail short names are internal codes (SPRP, NAIP) — arrow badge only
                        label = if (trip.routeType == 1 || trip.routeType == 2) "" else trip.routeShortName,
                        routeColor = routeColorInt,
                        routeTextColor = textColor,
                        heading = if (v.isLive) v.heading else null
                    )
                    // Faded, arrowless: last known position of a bus that isn't reporting now
                    if (!v.isLive) alpha = 0.45f
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    setInfoWindow(null)
                }
                map.overlays.add(marker)
            }
        }

        // You, under the stops so they stay tappable
        map.overlays.add(youOverlay)

        // Stop names: the focused stop and the mode-card stops always, the rest once zoomed in.
        // The far side of a paired card shares its partner's name, so it gets no label of its own.
        val cardStops = modeCards.map { it.group.id }.toSet()
        val farSides = modeCards.flatMap { c -> c.sides.drop(1).map { it.group.id } }.toSet()
        val focusedCard = modeCards.find { it.group.id == focusedGroupId }
        val focusedHeadings: Map<String, Int?> = focusedCard?.sides?.associate { it.group.id to it.heading }
            ?: listOfNotNull(focusedGroupId).associateWith { null }
        map.overlays.add(StopLabelsOverlay(
            stopGroups.filter { it.id !in farSides }.map { g ->
                StopLabel(
                    stopId = g.id,
                    text = mapStopName(g.name),
                    point = GeoPoint(g.latitude, g.longitude),
                    priority = when (g.id) { focusedGroupId -> PRIORITY_KEY + 2; in cardStops -> PRIORITY_KEY; else -> 0 },
                    bold = g.id == focusedGroupId || g.id in cardStops,
                    dimmed = false,
                    // Pretend the "route" runs north, so labels sit beside the dot (right, else left)
                    alongLat = 0.0001, alongLon = 0.0
                )
            },
            dp,
            gapDp = 16f,
            allLabelsZoom = ALL_STOP_LABELS_ZOOM,
            // Stop markers are 26 dp; padded() adds 3 dp, so a label 16 dp out still clears its own dot
            dotRadiusDp = 12f
        ).apply { avoid = youOverlay.position })

        // Stop markers on top; the focused one bigger with a ring
        for (group in stopGroups) {
            val marker = Marker(map).apply {
                position = GeoPoint(group.latitude, group.longitude)
                icon = createStopIcon(context, group, focused = group.id in focusedHeadings,
                    heading = focusedHeadings[group.id])
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                title = group.name
                setOnMarkerClickListener { _, _ ->
                    selectedGroupState.value = group
                    true
                }
            }
            map.overlays.add(marker)
        }

        map.invalidate()
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapRef.value?.onResume()
                Lifecycle.Event.ON_PAUSE  -> mapRef.value?.onPause()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val density = LocalDensity.current
    var peekPx by remember { mutableIntStateOf(0) }
    val peekHeight = SheetHandleHeight + with(density) { peekPx.toDp() }
    val scaffoldState = rememberBottomSheetScaffoldState()

    BoxWithConstraints(Modifier.fillMaxSize()) {
        // Dragged up, the panel stops below the search / alert buttons
        val sheetMax = maxHeight - SHEET_TOP_GAP
        BottomSheetScaffold(
            scaffoldState = scaffoldState,
            sheetPeekHeight = if (peekPx == 0) 120.dp else peekHeight,
            sheetContainerColor = SheetBg,
            sheetShadowElevation = 12.dp,
            sheetDragHandle = {
                Box(Modifier.fillMaxWidth().height(SheetHandleHeight), contentAlignment = Alignment.Center) {
                    Surface(shape = RoundedCornerShape(2.dp), color = ComposeColor(0xFF444C56), modifier = Modifier.size(36.dp, 4.dp)) {}
                }
            },
            sheetContent = {
                HomeSheetContent(
                    cards = modeCards,
                    stops = nearbyStops,
                    focusedGroupId = focusedGroupId,
                    updatedAtMs = depsUpdatedMs,
                    onCardTap = { card ->
                        if (focusedGroupId == card.group.id) {
                            // Both sides of the road in one sheet
                            selectedGroupState.value = card.asOneGroup()
                        } else {
                            focusedGroupId = card.group.id
                            following = false
                            // Centre between the sides, so both highlighted dots are in view; zoom in on a
                            // pair, whose stops are often only ~40 m apart and would overlap
                            val centre = GeoPoint(card.groups.map { it.latitude }.average(), card.groups.map { it.longitude }.average())
                            val map = mapRef.value
                            if (card.sides.size > 1 && map != null && map.zoomLevelDouble < PAIR_FOCUS_ZOOM)
                                map.controller.animateTo(centre, PAIR_FOCUS_ZOOM, 600L)
                            else map?.controller?.animateTo(centre)
                        }
                    },
                    onStopTap = { selectedGroupState.value = it },
                    onPeekHeight = { peekPx = it },
                    footer = sheetFooter,
                    modifier = Modifier.heightIn(max = sheetMax)
                )
            },
            containerColor = ComposeColor.Transparent
        ) {
            Box(Modifier.fillMaxSize()) {
            AndroidView(
                factory = { ctx ->
                    MapView(ctx).apply {
                        setTileSource(ESRI_DARK_GRAY_BASE)
                        addDarkCanvasLabels(ctx)
                        setMultiTouchControls(true)
                        // Pinch to zoom; osmdroid's own −/+ buttons clash with the dark map
                        zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
                        controller.setZoom(16.5)
                        controller.setCenter(GeoPoint(userLat ?: BRISBANE_LAT, userLon ?: BRISBANE_LON))
                        overlays.add(youOverlay)
                        // Dragging the map stops following you (pinch-zoom keeps it)
                        setOnTouchListener { _, ev ->
                            if (ev.actionMasked == MotionEvent.ACTION_MOVE) {
                                lastPanMs[0] = System.currentTimeMillis()
                                if (ev.pointerCount == 1) following = false
                            }
                            false
                        }
                    }.also { mapRef.value = it }
                },
                modifier = Modifier.fillMaxSize()
            )

                SmallFloatingActionButton(
                    onClick = {
                        following = true
                        youOverlay.position?.let { mapRef.value?.controller?.animateTo(it) }
                    },
                    containerColor = ComposeColor(0xFF22272E),
                    contentColor = ComposeColor(0xFF4FB3FF),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 12.dp, bottom = 12.dp + peekHeight)
                ) {
                    Icon(
                        if (following) Icons.Default.MyLocation else Icons.Default.LocationSearching,
                        contentDescription = "Back to my location"
                    )
                }
            }
        }
    }

    val selectedGroup = selectedGroupState.value
    if (selectedGroup != null) {
        ModalBottomSheet(
            onDismissRequest = { selectedGroupState.value = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
        ) {
            StopGroupSheet(
                group = selectedGroup,
                onAddStops = { stops ->
                    onAddStops(stops)
                    selectedGroupState.value = null
                },
                onDepartureClick = { dep ->
                    selectedGroupState.value = null
                    onDepartureClick?.invoke(dep)
                }
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Stop marker — small solid coloured circle with type initial
// ---------------------------------------------------------------------------

/**
 * Stop dot. Focused: bigger, with a white ring, and when [heading] is known a small arrow on
 * the ring showing which way services leave (tells the two sides of a road apart).
 */
private fun createStopIcon(context: android.content.Context, group: StopGroup, focused: Boolean = false, heading: Int? = null): BitmapDrawable {
    val dp = context.resources.displayMetrics.density
    val size = ((if (focused) (if (heading != null) 46 else 38) else 26) * dp).toInt()
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(bitmap)
    val pinColor = group.primaryType.pinColorInt()
    // Outer radius of the dot (ring included); the bitmap may be bigger to fit the arrow
    val outerR = if (focused) 17f * dp else size / 2f - 2 * dp

    // Shadow
    canvas.drawCircle(size / 2f, size / 2f + dp, outerR,
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.argb(80, 0, 0, 0); style = Paint.Style.FILL
        })

    // Focused: white ring around the dot
    if (focused) {
        canvas.drawCircle(size / 2f, size / 2f, outerR,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = AndroidColor.WHITE; style = Paint.Style.FILL })
    }

    // Coloured fill
    canvas.drawCircle(size / 2f, size / 2f, if (focused) outerR - 3 * dp else outerR,
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = pinColor; style = Paint.Style.FILL
        })

    // White type initial
    val label = when (group.primaryType) {
        VehicleType.Bus   -> "B"
        VehicleType.Rail  -> "T"
        VehicleType.Ferry -> "F"
        VehicleType.Tram  -> "Tm"
    }
    val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.WHITE
        textSize = (if (focused) 11f else 8f) * dp
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }
    canvas.drawText(label, size / 2f, size / 2f - (tp.descent() + tp.ascent()) / 2, tp)

    // Direction arrow just outside the ring
    if (focused && heading != null) {
        val c = size / 2f
        val ringR = outerR
        val path = Path().apply {
            moveTo(0f, -(ringR + 6 * dp))
            lineTo(4.5f * dp, -(ringR + 0.5f * dp))
            lineTo(-4.5f * dp, -(ringR + 0.5f * dp))
            close()
        }
        canvas.save()
        canvas.translate(c, c)
        canvas.rotate(heading.toFloat())
        canvas.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = AndroidColor.WHITE; style = Paint.Style.FILL })
        canvas.restore()
    }

    // Multi-platform badge
    if (group.isMultiPlatform) {
        val r = 6f * dp
        // Top-right of the dot, wherever the dot sits in the bitmap
        val bx = size / 2f + outerR * 0.75f
        val by = size / 2f - outerR * 0.75f
        canvas.drawCircle(bx, by, r,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = AndroidColor.WHITE; style = Paint.Style.FILL
            })
        val bp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = pinColor; textSize = 6f * dp
            textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD
        }
        canvas.drawText("${group.stops.size}", bx, by - (bp.descent() + bp.ascent()) / 2, bp)
    }

    return BitmapDrawable(context.resources, bitmap)
}

private fun VehicleType.pinColorInt(): Int = when (this) {
    VehicleType.Bus   -> AndroidColor.rgb(33, 150, 243)
    VehicleType.Rail  -> AndroidColor.rgb(103, 58, 183)
    VehicleType.Ferry -> AndroidColor.rgb(0, 150, 136)
    VehicleType.Tram  -> AndroidColor.rgb(76, 175, 80)
}

private fun VehicleType.pinColorCompose() = when (this) {
    VehicleType.Bus   -> androidx.compose.ui.graphics.Color(0xFF2196F3)
    VehicleType.Rail  -> androidx.compose.ui.graphics.Color(0xFF673AB7)
    VehicleType.Ferry -> androidx.compose.ui.graphics.Color(0xFF009688)
    VehicleType.Tram  -> androidx.compose.ui.graphics.Color(0xFF4CAF50)
}

// ---------------------------------------------------------------------------
// Stop-group bottom sheet
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StopGroupSheet(
    group: StopGroup,
    onAddStops: (List<Stop>) -> Unit,
    onDepartureClick: ((Departure) -> Unit)? = null
) {
    val depsVM = remember(group.id) { DeparturesViewModel() }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(group.primaryType.icon(), contentDescription = null,
                tint = group.primaryType.pinColorCompose(), modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(group.name, style = MaterialTheme.typography.titleMedium)
                val subtitle = buildString {
                    group.distanceLabel?.let { append(it) }
                    if (group.isMultiPlatform) {
                        if (isNotEmpty()) append(" · ")
                        // Stations can mix train platforms with bus bays (e.g. replacement buses)
                        val allSameMode = group.stops.map { it.primaryVehicleType }.distinct().size == 1
                        // Train/ferry platforms; bus stops (both sides of a road) are just stops
                        val platforms = allSameMode && group.primaryType != VehicleType.Bus
                        append("${group.stops.size} ${if (platforms) "platforms" else "stops"}")
                    }
                }
                if (subtitle.isNotEmpty()) {
                    Text(subtitle, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            FilledTonalButton(onClick = { onAddStops(group.stops) },
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Add")
            }
        }
        // Route cards show each service's platform, so no per-platform chips here
        HorizontalDivider()
        Box(modifier = Modifier.height(440.dp)) {
            DeparturesScreen(
                viewModel = depsVM,
                stops = group.stops,
                onDepartureClick = onDepartureClick
            )
        }
    }
}
