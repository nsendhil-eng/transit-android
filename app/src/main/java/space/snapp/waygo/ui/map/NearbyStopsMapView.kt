package space.snapp.waygo.ui.map

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
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
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import space.snapp.waygo.data.api.TransitApiService
import space.snapp.waygo.data.api.models.Stop
import space.snapp.waygo.data.api.models.VehicleType
import space.snapp.waygo.ui.components.icon
import space.snapp.waygo.ui.departures.DeparturesScreen
import space.snapp.waygo.ui.departures.DeparturesViewModel

// Brisbane city centre fallback
private const val BRISBANE_LAT = -27.4698
private const val BRISBANE_LON = 153.0251

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NearbyStopsMapView(
    userLat: Double?,
    userLon: Double?,
    onAddStops: (List<Stop>) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val api = remember { TransitApiService.instance }

    var stopGroups by remember { mutableStateOf<List<StopGroup>>(emptyList()) }
    val selectedGroupState = remember { mutableStateOf<StopGroup?>(null) }
    val mapRef = remember { mutableStateOf<MapView?>(null) }

    // Configure osmdroid user-agent (required by the OSM tile usage policy)
    LaunchedEffect(Unit) {
        Configuration.getInstance().userAgentValue = context.packageName
    }

    // Fetch nearby stops and centre the map whenever location changes
    LaunchedEffect(userLat, userLon) {
        val lat = userLat ?: BRISBANE_LAT
        val lon = userLon ?: BRISBANE_LON
        mapRef.value?.controller?.animateTo(GeoPoint(lat, lon))
        runCatching { api.stopsNearMe(lat, lon, radius = 500) }
            .onSuccess { stops -> stopGroups = groupStops(stops) }
    }

    // Rebuild map markers whenever stop groups change
    LaunchedEffect(stopGroups) {
        val map = mapRef.value ?: return@LaunchedEffect
        map.overlays.clear()
        for (group in stopGroups) {
            val marker = Marker(map).apply {
                position = GeoPoint(group.latitude, group.longitude)
                icon = createMarkerIcon(context, group)
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

    // Forward Android lifecycle events to osmdroid
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
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)
                controller.setZoom(16.5)
                controller.setCenter(GeoPoint(userLat ?: BRISBANE_LAT, userLon ?: BRISBANE_LON))
            }.also { mapRef.value = it }
        },
        modifier = Modifier.fillMaxSize()
    )

    // Bottom sheet — shown when the user taps a stop marker
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
                }
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Marker icon helpers
// ---------------------------------------------------------------------------

private fun createMarkerIcon(context: android.content.Context, group: StopGroup): BitmapDrawable {
    val dp = context.resources.displayMetrics.density
    val size = (44 * dp).toInt()
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(bitmap)
    val pinColor = group.primaryType.pinColorInt()

    // Drop shadow
    canvas.drawCircle(size / 2f, size / 2f + dp, size / 2f - 2 * dp,
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.argb(40, 0, 0, 0)
            style = Paint.Style.FILL
        })

    // White fill
    canvas.drawCircle(size / 2f, size / 2f, size / 2f - 2 * dp,
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.WHITE
            style = Paint.Style.FILL
        })

    // Coloured border
    canvas.drawCircle(size / 2f, size / 2f, size / 2f - 3f * dp,
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = pinColor
            style = Paint.Style.STROKE
            strokeWidth = 3f * dp
        })

    // Vehicle type initial letter
    val label = when (group.primaryType) {
        VehicleType.Bus   -> "B"
        VehicleType.Rail  -> "T"
        VehicleType.Ferry -> "F"
        VehicleType.Tram  -> "Tm"
    }
    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = pinColor
        textSize = 13f * dp
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }
    canvas.drawText(
        label, size / 2f,
        size / 2f - (textPaint.descent() + textPaint.ascent()) / 2,
        textPaint
    )

    // Platform-count badge
    if (group.isMultiPlatform) {
        val r = 9f * dp
        canvas.drawCircle(size - r, r, r,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = pinColor
                style = Paint.Style.FILL
            })
        val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.WHITE
            textSize = 9f * dp
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }
        canvas.drawText(
            "${group.stops.size}", size - r,
            r - (badgePaint.descent() + badgePaint.ascent()) / 2,
            badgePaint
        )
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
    onAddStops: (List<Stop>) -> Unit
) {
    val depsVM = remember(group.id) { DeparturesViewModel() }

    Column(modifier = Modifier.fillMaxWidth()) {
        // Header row: icon + name/distance + Add button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = group.primaryType.icon(),
                contentDescription = null,
                tint = group.primaryType.pinColorCompose(),
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(group.name, style = MaterialTheme.typography.titleMedium)
                val subtitle = buildString {
                    group.distanceLabel?.let { append(it) }
                    if (group.isMultiPlatform) {
                        if (isNotEmpty()) append(" · ")
                        append("${group.stops.size} platforms")
                    }
                }
                if (subtitle.isNotEmpty()) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            FilledTonalButton(
                onClick = { onAddStops(group.stops) },
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Add")
            }
        }

        // Platform chips for multi-platform stations
        if (group.isMultiPlatform) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                items(group.stops) { stop ->
                    SuggestionChip(
                        onClick = {},
                        label = { Text(stop.name, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }
        }

        HorizontalDivider()

        // Departures list (bounded height so it works inside the sheet)
        Box(modifier = Modifier.height(320.dp)) {
            DeparturesScreen(viewModel = depsVM, stops = group.stops)
        }
    }
}
