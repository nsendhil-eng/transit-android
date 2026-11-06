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
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.shape.RoundedCornerShape
import space.snapp.waygo.ui.tripdetail.PRIORITY_KEY
import space.snapp.waygo.ui.tripdetail.StopLabel
import space.snapp.waygo.ui.tripdetail.StopLabelsOverlay
import space.snapp.waygo.ui.tripdetail.mapStopName
import kotlinx.coroutines.delay
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
/** Starting zoom, and where the re-centre button returns to */
const val DEFAULT_ZOOM = 16.5
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
    sheetFooter: @Composable () -> Unit = {},
    /** Shown at the top of the bottom panel ("Where to?" and saved places) */
    sheetHeader: @Composable () -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val api = remember { TransitApiService.instance }
    // The data lives here, so coming back from another tab doesn't start over
    val home: HomeViewModel = viewModel()
    val stopGroups = home.stopGroups
    val nearbyDeps = home.nearbyDeps
    var selectedKey by home::selectedKey
    var following by home::following
    var mapZoom by home::mapZoom

    var tripLiveData by remember { mutableStateOf<List<TripLive>>(emptyList()) }
    val selectedGroupState = remember { mutableStateOf<StopGroup?>(null) }
    val mapRef = remember { mutableStateOf<MapView?>(null) }
    val youOverlay = remember { UserLocationOverlay(context) }
    // The map's layers, bottom to top, each redrawn only when its own data changes
    val density = LocalDensity.current
    val tripLayer = remember { FolderOverlay() }
    val routePaths = remember { RoutePathsOverlay(density.density) }
    val vehicleLayer = remember { FolderOverlay() }
    val stopLabels = remember {
        StopLabelsOverlay(
            emptyList(),
            density.density,
            gapDp = 16f,
            allLabelsZoom = ALL_STOP_LABELS_ZOOM,
            // Stop markers are 26 dp; a label 16 dp out still clears its own dot
            dotRadiusDp = 12f
        )
    }
    val stopLayer = remember { FolderOverlay() }
    val compass = rememberCompassHeading()
    // When the map was last dragged; refreshes wait until it settles
    val lastPanMs = remember { longArrayOf(0L) }
    LaunchedEffect(Unit) { while (true) { home.nowMs = System.currentTimeMillis(); delay(15_000) } }
    val routes = remember(stopGroups, nearbyDeps, home.nowMs) { buildNearbyRoutes(stopGroups, nearbyDeps) }
    // Time to first rows: "Fully drawn" in logcat on launch, WayGoPerf on every return to this tab
    val composedAtMs = remember { android.os.SystemClock.uptimeMillis() }
    val hasRoutes = routes.isNotEmpty()
    LaunchedEffect(hasRoutes) {
        if (!hasRoutes) return@LaunchedEffect
        val uptime = android.os.SystemClock.uptimeMillis()
        android.util.Log.i("WayGoPerf", "rows after ${uptime - composedAtMs} ms, ${uptime - android.os.Process.getStartUptimeMillis()} ms since process start")
        (context as? android.app.Activity)?.reportFullyDrawn()
    }
    val selectedRoute = routes.find { it.key == selectedKey }
    val focusedGroupId = selectedRoute?.group?.id
    // The panel's peek rows: the nearest route of each mode
    val peekGroupIds = remember(routes) { peekRoutes(routes).map { it.group.id }.toSet() }
    val panelState = when {
        !hasLocationPermission -> HomePanelState.NoLocation
        userLat == null || userLon == null -> HomePanelState.Loading
        !inSeq(userLat, userLon) -> HomePanelState.OutsideSeq
        routes.isNotEmpty() -> HomePanelState.Ready
        home.depsUpdatedMs == 0L -> HomePanelState.Loading
        else -> HomePanelState.NothingNearby
    }
    // Zoomed out, stops that would sit on top of each other thin out (a dozen dots round a city
    // station); the panel's top stops and the selected one always stay. Zoom tracked to a quarter level.
    val shownStops = remember(stopGroups, peekGroupIds, focusedGroupId, mapZoom, userLat, userLon) {
        if (mapZoom >= ALL_STOPS_ZOOM) stopGroups
        else {
            val keep = peekGroupIds + listOfNotNull(focusedGroupId)
            val lat = userLat ?: BRISBANE_LAT
            val metersPerPx = 40_075_016.686 * cos(Math.toRadians(lat)) / (TileSystem.getTileSize() * 2.0.pow(mapZoom))
            declutterStops(stopGroups, keep, userLat, userLon, STOP_GAP_DP * context.resources.displayMetrics.density * metersPerPx)
        }
    }

    // Move the dot (and the map, while following) whenever location changes
    LaunchedEffect(userLat, userLon, userAccuracyM) {
        if (userLat == null || userLon == null) return@LaunchedEffect
        youOverlay.position = GeoPoint(userLat, userLon)
        youOverlay.accuracyM = userAccuracyM
        stopLabels.avoid = youOverlay.position
        if (following) mapRef.value?.controller?.animateTo(youOverlay.position)
        mapRef.value?.invalidate()
    }
    // Collected here rather than read above: reading it above re-ran this whole screen on each 3° turn
    LaunchedEffect(Unit) {
        snapshotFlow { compass.value }.collect { heading ->
            youOverlay.heading = heading
            mapRef.value?.invalidate()
        }
    }

    LaunchedEffect(userLat, userLon) {
        home.onLocation(userLat ?: return@LaunchedEffect, userLon ?: return@LaunchedEffect)
    }

    // Departures for the nearby stops: every 30 s while the app is in front, never mid-pan.
    // Keyed on the stop codes, so a new GPS fix that finds the same stops doesn't refetch.
    val stopCodes = remember(stopGroups) {
        stopGroups.flatMap { g -> g.stops.mapNotNull { it.stopCode?.takeIf(String::isNotBlank) } }
            .distinct().take(MAX_CALLOUT_STOPS)
    }
    LaunchedEffect(stopCodes) {
        if (stopCodes.isEmpty()) return@LaunchedEffect
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            home.refreshing = true
            while (true) {
                while (System.currentTimeMillis() - lastPanMs[0] < PAN_SETTLE_MS) delay(PAN_SETTLE_MS)
                home.refreshDepartures(stopCodes)
                delay(CALLOUT_REFRESH_MS)
            }
        }
    }

    // The selected row's whole route and the live vehicles on its next services. Nothing is
    // drawn until a row is tapped: lines redrawn whenever the next bus changed were dizzying.
    val shapeCache = home.shapeCache
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

    // The opened stop's upcoming trips: route shapes and vehicle markers
    LaunchedEffect(tripLiveData) {
        val map = mapRef.value ?: return@LaunchedEffect
        val dp = density.density
        tripLayer.items.clear()

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
                tripLayer.add(poly)
            }

            // Shape after the stop — full color
            if (trip.shapeAfter.size >= 2) {
                val poly = Polyline().apply {
                    setPoints(trip.shapeAfter.map { GeoPoint(it[0], it[1]) })
                    outlinePaint.color = routeColorInt
                    outlinePaint.strokeWidth = 5f * dp
                }
                tripLayer.add(poly)
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
                tripLayer.add(marker)
            }
        }
        map.invalidate()
    }

    // Stops and their names
    LaunchedEffect(shownStops, focusedGroupId, selectedRoute?.first?.heading, peekGroupIds) {
        val map = mapRef.value ?: return@LaunchedEffect
        // Stop names: the selected stop and the panel's top stops always, the rest once zoomed in
        val cardStops = peekGroupIds
        val focusedHeadings: Map<String, Int?> = listOfNotNull(focusedGroupId).associateWith { selectedRoute?.first?.heading }
        stopLabels.labels = shownStops.map { g ->
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
        }

        // Stop markers on top; the focused one bigger with a ring
        stopLayer.items.clear()
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
            stopLayer.add(marker)
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
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapRef.value?.let { map ->
                home.leftMapAt = GeoPoint(map.mapCenter.latitude, map.mapCenter.longitude)
                home.leftMapZoom = map.zoomLevelDouble
            }
        }
    }

    var peekPx by home::peekPx
    var headerPx by home::headerPx
    // Three positions: resting (header + nearest routes), dragged up (every route), and dragged
    // down to just the header so the map gets the screen. Material's sheet has two, so a drag
    // below resting "hides" it, which is caught here and turned into the lower peek.
    var lowered by home::lowered
    val peekHeight = SheetHandleHeight + with(density) { (if (lowered && headerPx > 0) headerPx else peekPx).toDp() }
    // Centre the map in the part you can see, above the panel, so you aren't under it
    val peekHeightPx = with(density) { peekHeight.roundToPx() }
    LaunchedEffect(peekHeightPx) {
        mapRef.value?.apply { setMapCenterOffset(0, -peekHeightPx / 2); invalidate() }
    }
    val scaffoldState = rememberBottomSheetScaffoldState(
        bottomSheetState = rememberStandardBottomSheetState(skipHiddenState = false)
    )
    val panelList = rememberLazyListState()
    LaunchedEffect(scaffoldState.bottomSheetState.currentValue) {
        // Closed back to resting (or lowered): show its top, "Where to?", not the middle of the list
        if (scaffoldState.bottomSheetState.currentValue != SheetValue.Expanded) panelList.scrollToItem(0)
        when (scaffoldState.bottomSheetState.currentValue) {
            SheetValue.Hidden -> { lowered = true; scaffoldState.bottomSheetState.partialExpand() }
            // Opened all the way: closing it comes back to the usual resting size
            SheetValue.Expanded -> lowered = false
            else -> {}
        }
    }

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
                    updatedAtMs = home.depsUpdatedMs,
                    refreshing = home.refreshing,
                    now = home.nowMs,
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
                    onHeaderHeight = { headerPx = it },
                    footer = sheetFooter,
                    header = sheetHeader,
                    listState = panelList,
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
                        controller.setZoom(home.leftMapZoom)
                        // Where you left it; else you; else where you were last time
                        controller.setCenter(
                            home.leftMapAt.takeUnless { following }
                                ?: youOverlay.position
                                ?: userLat?.let { lat -> userLon?.let { lon -> GeoPoint(lat, lon) } }
                                ?: home.lastKnownLocation
                                ?: GeoPoint(BRISBANE_LAT, BRISBANE_LON)
                        )
                        overlays.add(tripLayer)
                        overlays.add(routePaths)
                        overlays.add(vehicleLayer)
                        // You, under the stops so they stay tappable
                        overlays.add(youOverlay)
                        overlays.add(stopLabels)
                        overlays.add(stopLayer)
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
    val key = StopIconKey(group.primaryType, focused, heading, if (group.isMultiPlatform) group.stops.size else 1)
    val bitmap = stopIcons.get(key) ?: drawStopIcon(context, key).also { stopIcons.put(key, it) }
    return BitmapDrawable(context.resources, bitmap)
}

/** What a stop dot looks like, so stops that look alike share one bitmap */
data class StopIconKey(val type: VehicleType, val focused: Boolean, val heading: Int?, val platforms: Int)

private val stopIcons = android.util.LruCache<StopIconKey, Bitmap>(32)

private fun drawStopIcon(context: android.content.Context, key: StopIconKey): Bitmap {
    val focused = key.focused
    val heading = key.heading
    val dp = context.resources.displayMetrics.density
    val size = ((if (focused) (if (heading != null) 46 else 38) else 26) * dp).toInt()
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(bitmap)
    val pinColor = key.type.pinColorInt()
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
    val label = when (key.type) {
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
    if (key.platforms > 1) {
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
        canvas.drawText("${key.platforms}", bx, by - (bp.descent() + bp.ascent()) / 2, bp)
    }

    return bitmap
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
