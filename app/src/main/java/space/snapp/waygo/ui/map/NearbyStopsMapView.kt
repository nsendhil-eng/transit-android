package space.snapp.waygo.ui.map

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
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
import kotlinx.coroutines.delay
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.GeoPoint
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

private val CARTO_DARK_MATTER = XYTileSource(
    "CartoDB.DarkMatter",
    0, 19, 256, ".png",
    arrayOf(
        "https://a.basemaps.cartocdn.com/dark_all/",
        "https://b.basemaps.cartocdn.com/dark_all/",
        "https://c.basemaps.cartocdn.com/dark_all/"
    ),
    "© OpenStreetMap contributors © CARTO"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NearbyStopsMapView(
    userLat: Double?,
    userLon: Double?,
    onAddStops: (List<Stop>) -> Unit,
    onDepartureClick: ((Departure) -> Unit)? = null
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val api = remember { TransitApiService.instance }

    var stopGroups by remember { mutableStateOf<List<StopGroup>>(emptyList()) }
    var tripLiveData by remember { mutableStateOf<List<TripLive>>(emptyList()) }
    val selectedGroupState = remember { mutableStateOf<StopGroup?>(null) }
    val mapRef = remember { mutableStateOf<MapView?>(null) }

    LaunchedEffect(Unit) {
        Configuration.getInstance().userAgentValue = context.packageName
    }

    // Fetch nearby stops whenever location changes
    LaunchedEffect(userLat, userLon) {
        val lat = userLat ?: BRISBANE_LAT
        val lon = userLon ?: BRISBANE_LON
        mapRef.value?.controller?.animateTo(GeoPoint(lat, lon))
        runCatching { api.stopsNearMe(lat, lon, radius = 500) }
            .onSuccess { stops -> stopGroups = groupStops(stops) }
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
    LaunchedEffect(stopGroups, tripLiveData) {
        val map = mapRef.value ?: return@LaunchedEffect
        val dp = context.resources.displayMetrics.density
        map.overlays.clear()

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
                val marker = Marker(map).apply {
                    position = GeoPoint(v.lat, v.lon)
                    icon = createVehicleIcon(context, trip)
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    setInfoWindow(null)
                }
                map.overlays.add(marker)
            }
        }

        // Stop markers on top
        for (group in stopGroups) {
            val marker = Marker(map).apply {
                position = GeoPoint(group.latitude, group.longitude)
                icon = createStopIcon(context, group)
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

    AndroidView(
        factory = { ctx ->
            MapView(ctx).apply {
                setTileSource(CARTO_DARK_MATTER)
                setMultiTouchControls(true)
                controller.setZoom(16.5)
                controller.setCenter(GeoPoint(userLat ?: BRISBANE_LAT, userLon ?: BRISBANE_LON))
            }.also { mapRef.value = it }
        },
        modifier = Modifier.fillMaxSize()
    )

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
// Vehicle marker — coloured pill badge with route name + downward arrow anchor
// ---------------------------------------------------------------------------

private fun createVehicleIcon(context: android.content.Context, trip: TripLive): BitmapDrawable {
    val dp = context.resources.displayMetrics.density
    val label = trip.routeShortName.ifBlank { "?" }.take(8)

    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.WHITE
        textSize = 9f * dp
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }
    val textW = textPaint.measureText(label)
    val padH = 7f * dp
    val padV = 4f * dp
    val arrowH = 5f * dp
    val bodyW = maxOf(textW + padH * 2, 20f * dp)
    val bodyH = (-textPaint.ascent() + textPaint.descent()) + padV * 2
    val totalH = bodyH + arrowH

    val bW = bodyW.toInt().coerceAtLeast(1)
    val bH = totalH.toInt().coerceAtLeast(1)
    val bitmap = Bitmap.createBitmap(bW, bH, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(bitmap)

    val bgColor = trip.routeColor?.let {
        runCatching { AndroidColor.parseColor("#${it.trimStart('#')}") }.getOrNull()
    } ?: AndroidColor.rgb(33, 150, 243)

    val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = bgColor; style = Paint.Style.FILL }

    canvas.drawRoundRect(0f, 0f, bodyW, bodyH, 5f * dp, 5f * dp, bgPaint)

    val arrowPath = Path().apply {
        moveTo(bodyW / 2f - arrowH, bodyH)
        lineTo(bodyW / 2f + arrowH, bodyH)
        lineTo(bodyW / 2f, bodyH + arrowH)
        close()
    }
    canvas.drawPath(arrowPath, bgPaint)
    canvas.drawText(label, bodyW / 2f, padV + (-textPaint.ascent()), textPaint)

    return BitmapDrawable(context.resources, bitmap)
}

// ---------------------------------------------------------------------------
// Stop marker — small solid coloured circle with type initial
// ---------------------------------------------------------------------------

private fun createStopIcon(context: android.content.Context, group: StopGroup): BitmapDrawable {
    val dp = context.resources.displayMetrics.density
    val size = (26 * dp).toInt()
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(bitmap)
    val pinColor = group.primaryType.pinColorInt()

    // Shadow
    canvas.drawCircle(size / 2f, size / 2f + dp, size / 2f - 2 * dp,
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.argb(80, 0, 0, 0); style = Paint.Style.FILL
        })

    // Coloured fill
    canvas.drawCircle(size / 2f, size / 2f, size / 2f - 2 * dp,
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
        textSize = 8f * dp
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }
    canvas.drawText(label, size / 2f, size / 2f - (tp.descent() + tp.ascent()) / 2, tp)

    // Multi-platform badge
    if (group.isMultiPlatform) {
        val r = 6f * dp
        canvas.drawCircle(size - r, r, r,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = AndroidColor.WHITE; style = Paint.Style.FILL
            })
        val bp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = pinColor; textSize = 6f * dp
            textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD
        }
        canvas.drawText("${group.stops.size}", size - r, r - (bp.descent() + bp.ascent()) / 2, bp)
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
                    if (group.isMultiPlatform) { if (isNotEmpty()) append(" · "); append("${group.stops.size} platforms") }
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
        if (group.isMultiPlatform) {
            LazyRow(contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(bottom = 8.dp)) {
                items(group.stops) { stop ->
                    SuggestionChip(onClick = {},
                        label = { Text(stop.name, style = MaterialTheme.typography.labelSmall) })
                }
            }
        }
        HorizontalDivider()
        Box(modifier = Modifier.height(320.dp)) {
            DeparturesScreen(
                viewModel = depsVM,
                stops = group.stops,
                onDepartureClick = onDepartureClick
            )
        }
    }
}
