package space.snapp.waygo.ui.map

import space.snapp.waygo.data.pollWhileVisible
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
import space.snapp.waygo.data.api.models.LiveTripVehicle
import org.osmdroid.views.overlay.FolderOverlay
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.util.TileSystem
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import space.snapp.waygo.data.api.models.VehicleType
import space.snapp.waygo.ui.components.icon
import space.snapp.waygo.ui.departures.DeparturesScreen
import space.snapp.waygo.ui.departures.DeparturesViewModel

private const val BRISBANE_LAT = -27.4698
private const val BRISBANE_LON = 153.0251
private const val STOP_LIVE_REFRESH_MS = 5_000L
private const val CALLOUT_REFRESH_MS = 30_000L
private const val PAN_SETTLE_MS = 1_500L
private const val MAX_CALLOUT_STOPS = 45
/** Trains and ferries are looked for this far away (about a 15 minute walk); buses within 500 m */
private const val FAR_RADIUS_M = 1200
/** Refetch nearby stops only after moving this far (GPS updates every 10 s) */
private const val REFETCH_STOPS_AFTER_M = 150.0
/** Starting zoom, and where the re-centre button returns to */
private const val DEFAULT_ZOOM = 16.5
/** Drag handle strip at the top of the bottom panel */
private val SheetHandleHeight = 20.dp
/** Room left above the panel when dragged all the way up: status bar, search and alert buttons */
private val SHEET_TOP_GAP = 140.dp
/** Below this zoom only the panel's top stops are labelled */
private const val ALL_STOP_LABELS_ZOOM = 15.5
/** From this zoom every stop is drawn; below it, stops closer than STOP_GAP_DP on screen thin out */
private const val ALL_STOPS_ZOOM = 17.5
private const val STOP_GAP_DP = 28f
/** Live vehicles on the selected route: refresh interval, and fixes older than this are hidden */
private const val VEHICLE_REFRESH_MS = 10_000L
private const val VEHICLE_MAX_AGE_S = 600

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NearbyStopsMapView(
    userLat: Double?,
    userLon: Double?,
    userAccuracyM: Float? = null,
    hasLocationPermission: Boolean = true,
    onRequestLocation: () -> Unit = {},
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
    // Next departures nearby: each route + direction at its stop nearest you
    var nearbyDeps by remember { mutableStateOf<List<Departure>>(emptyList()) }
    // The panel row tapped once: its stop, route and live vehicles on the map; a second tap opens the stop
    var selectedKey by remember { mutableStateOf<String?>(null) }
    // When the map was last dragged; refreshes wait until it settles
    val lastPanMs = remember { longArrayOf(0L) }
    val lastStopsFetch = remember { mutableStateOf<GeoPoint?>(null) }
    // When departures last arrived; the panel dims if refreshes keep failing
    var depsUpdatedMs by remember { mutableLongStateOf(0L) }
    // Rebuilt every 15 s too, so a service that has just left drops off without waiting for a fetch
    var rowsTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(15_000); rowsTick++ } }
    val routes = remember(stopGroups, nearbyDeps, rowsTick) { buildNearbyRoutes(stopGroups, nearbyDeps) }
    val selectedRoute = routes.find { it.key == selectedKey }
    val focusedGroupId = selectedRoute?.group?.id
    // The panel's peek rows: the nearest route of each mode
    val peekGroupIds = remember(routes) { routes.groupBy { it.mode }.values.map { it.first().group.id }.toSet() }
    val panelState = when {
        !hasLocationPermission -> HomePanelState.NoLocation
        userLat == null || userLon == null -> HomePanelState.Loading
        !inSeq(userLat, userLon) -> HomePanelState.OutsideSeq
        routes.isNotEmpty() -> HomePanelState.Ready
        depsUpdatedMs == 0L -> HomePanelState.Loading
        else -> HomePanelState.NothingNearby
    }
    // Zoomed out, stops that would sit on top of each other thin out (a dozen dots round a city
    // station); the panel's top stops and the selected one always stay. Zoom tracked to a quarter level.
    var mapZoom by remember { mutableDoubleStateOf(DEFAULT_ZOOM) }
    val shownStops = remember(stopGroups, peekGroupIds, focusedGroupId, mapZoom, userLat, userLon) {
        if (mapZoom >= ALL_STOPS_ZOOM) stopGroups
        else {
            val keep = peekGroupIds + listOfNotNull(focusedGroupId)
            val lat = userLat ?: BRISBANE_LAT
            val metersPerPx = 40_075_016.686 * cos(Math.toRadians(lat)) / (TileSystem.getTileSize() * 2.0.pow(mapZoom))
            declutterStops(stopGroups, keep, userLat, userLon, STOP_GAP_DP * context.resources.displayMetrics.density * metersPerPx)
        }
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

    // Fetch nearby stops on the first fix, then only after moving a fair way. No fix, no stops:
    // showing the CBD's departures as if you were there would mislead.
    LaunchedEffect(userLat, userLon) {
        val lat = userLat ?: return@LaunchedEffect
        val lon = userLon ?: return@LaunchedEffect
        if (!inSeq(lat, lon)) return@LaunchedEffect
        val last = lastStopsFetch.value
        if (last != null && approxMeters(last.latitude, last.longitude, lat, lon) < REFETCH_STOPS_AFTER_M) return@LaunchedEffect
        runCatching { api.stopsNearMe(lat, lon, radius = 500, farRadius = FAR_RADIUS_M) }
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
        if (stopCodes.isEmpty()) {
            nearbyDeps = emptyList()
            if (lastStopsFetch.value != null) depsUpdatedMs = System.currentTimeMillis()
            return@LaunchedEffect
        }
        // Distances from where the stops were fetched, so the API's nearest-stop choice (and the
        // panel's order) only changes when the stops do
        val from = lastStopsFetch.value
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                while (System.currentTimeMillis() - lastPanMs[0] < PAN_SETTLE_MS) delay(PAN_SETTLE_MS)
                runCatching {
                    api.departures(stopCodes.joinToString(","), withHeading = 1,
                        perRoute = ROUTE_ROW_TIMES, lat = from?.latitude, lon = from?.longitude)
                }
                    .onSuccess { nearbyDeps = it; depsUpdatedMs = System.currentTimeMillis() }
                delay(CALLOUT_REFRESH_MS)
            }
        }
    }

    // The selected row's whole route and the live vehicles on its next services. Nothing is
    // drawn until a row is tapped: lines redrawn whenever the next bus changed were dizzying.
    // Shapes depend only on the timetable, so each is fetched once and kept for the session.
    val routePaths = remember { RoutePathsOverlay(context.resources.displayMetrics.density) }
    val shapeCache = remember { mutableStateMapOf<String, List<GeoPoint>>() }
    val drawn = selectedRoute?.departures.orEmpty()
    val missingShapes = drawn.take(1).mapNotNull { it.shapeId }.filter { it !in shapeCache }.toSet()
    LaunchedEffect(missingShapes) {
        if (missingShapes.isEmpty()) return@LaunchedEffect
        runCatching { api.shapes(missingShapes.joinToString(",")) }
            .onSuccess { r ->
                for (id in missingShapes) shapeCache[id] = r.shapes[id].orEmpty().map { GeoPoint(it[0], it[1]) }
            }
    }
    LaunchedEffect(selectedRoute?.first?.shapeId, shapeCache.size) {
        routePaths.paths = listOfNotNull(selectedRoute?.first?.let { dep ->
            shapeCache[dep.shapeId]?.takeIf { it.size >= 2 }?.let { RoutePath(it, dep.routeColorInt ?: dep.mode.pinColorInt(), dimmed = false) }
        })
        mapRef.value?.invalidate()
    }

    // The live vehicles on those services, refreshed while the app is on screen. Tapping one
    // opens its trip.
    val vehicleLayer = remember { FolderOverlay() }
    var liveVehicles by remember { mutableStateOf<List<LiveTripVehicle>>(emptyList()) }
    val drawnTripIds = drawn.map { it.tripId }.distinct()
    LaunchedEffect(drawnTripIds) {
        if (drawnTripIds.isEmpty()) { liveVehicles = emptyList(); return@LaunchedEffect }
        pollWhileVisible(VEHICLE_REFRESH_MS) {
            runCatching { api.tripVehicles(drawnTripIds.joinToString(",")) }
                .onSuccess { liveVehicles = it.vehicles }
        }
    }
    LaunchedEffect(liveVehicles, selectedKey) {
        val map = mapRef.value ?: return@LaunchedEffect
        vehicleLayer.items.clear()
        for (v in liveVehicles) {
            // A fix this old is a vehicle parked before its trip, not on its way
            if ((v.ageSeconds ?: 0) > VEHICLE_MAX_AGE_S) continue
            val dep = drawn.find { it.tripId == v.tripId } ?: continue
            val color = dep.routeColorInt ?: dep.mode.pinColorInt()
            val marker = Marker(map).apply {
                position = GeoPoint(v.lat, v.lon)
                icon = createVehicleMarkerIcon(
                    context,
                    // Rail short names are internal codes: arrow badge only
                    label = if (dep.mode == VehicleType.Rail) "" else dep.routeNumber,
                    routeColor = color,
                    routeTextColor = dep.routeTextColorInt
                        ?: if (androidx.core.graphics.ColorUtils.calculateLuminance(color) > 0.5) AndroidColor.BLACK else AndroidColor.WHITE,
                    heading = if (v.headingKnown && !v.isStale) v.heading.toFloat() else null
                )
                if (v.isStale) alpha = 0.45f
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                setInfoWindow(null)
                setOnMarkerClickListener { _, _ -> onDepartureClick?.invoke(dep); true }
            }
            vehicleLayer.add(marker)
        }
        map.invalidate()
    }

    // Poll stop-live every 5 s while a stop group is selected; cancel + clear when dismissed
    LaunchedEffect(selectedGroupState.value) {
        val group = selectedGroupState.value
        if (group == null) {
            tripLiveData = emptyList()
            return@LaunchedEffect
        }
        val stopIds = group.stops.joinToString(",") { it.id }
        pollWhileVisible(STOP_LIVE_REFRESH_MS) {
            runCatching { api.stopLive(stopIds) }
                .onSuccess { tripLiveData = it.trips }
        }
    }

    // Rebuild overlays whenever stops or live trip data changes
    LaunchedEffect(shownStops, tripLiveData, focusedGroupId, selectedRoute?.first?.heading, peekGroupIds) {
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

        // The selected route and its vehicles, under you and the stops
        map.overlays.add(routePaths)
        map.overlays.add(vehicleLayer)

        // You, under the stops so they stay tappable
        map.overlays.add(youOverlay)

        // Stop names: the selected stop and the panel's top stops always, the rest once zoomed in
        val cardStops = peekGroupIds
        val focusedHeadings: Map<String, Int?> = listOfNotNull(focusedGroupId).associateWith { selectedRoute?.first?.heading }
        map.overlays.add(StopLabelsOverlay(
            shownStops.map { g ->
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
        for (group in shownStops) {
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
                HomePanel(
                    state = panelState,
                    routes = routes,
                    selectedKey = selectedKey,
                    updatedAtMs = depsUpdatedMs,
                    onRouteTap = { r ->
                        if (selectedKey == r.key) {
                            selectedGroupState.value = r.group
                        } else {
                            selectedKey = r.key
                            following = false
                            mapRef.value?.controller?.animateTo(GeoPoint(r.group.latitude, r.group.longitude))
                        }
                    },
                    onRequestLocation = onRequestLocation,
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
                        controller.setZoom(DEFAULT_ZOOM)
                        controller.setCenter(GeoPoint(userLat ?: BRISBANE_LAT, userLon ?: BRISBANE_LON))
                        overlays.add(youOverlay)
                        // Dragging the map stops following you (pinch-zoom keeps it)
                        addMapListener(object : MapListener {
                            override fun onScroll(event: ScrollEvent?) = false
                            override fun onZoom(event: ZoomEvent?): Boolean {
                                val z = (zoomLevelDouble * 4).roundToInt() / 4.0
                                if (z != mapZoom) mapZoom = z
                                return false
                            }
                        })
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
                        // Back to you at the normal zoom, whatever was selected
                        selectedKey = null
                        youOverlay.position?.let { mapRef.value?.controller?.animateTo(it, DEFAULT_ZOOM, 600L) }
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
            onDismissRequest = {
                selectedGroupState.value = null
            },
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
