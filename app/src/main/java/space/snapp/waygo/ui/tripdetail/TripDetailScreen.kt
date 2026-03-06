package space.snapp.waygo.ui.tripdetail

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color as AndroidColor
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path as AndroidPath
import android.graphics.drawable.BitmapDrawable
import androidx.activity.compose.BackHandler
import kotlinx.coroutines.delay
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import space.snapp.waygo.data.api.models.TripDetailResponse
import space.snapp.waygo.data.api.models.TripStop

data class TripDetailArgs(
    val tripId: String?,
    val routeId: String,
    val directionId: Int,
    val routeShortName: String,
    val headsign: String
)

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

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mapRef = remember { mutableStateOf<MapView?>(null) }

    BackHandler(onBack = onBack)

    LaunchedEffect(args) {
        viewModel.load(args.tripId, args.routeId, args.directionId, userLat, userLon)
    }
    DisposableEffect(Unit) { onDispose { viewModel.stop() } }

    // Resolve route color from loaded detail, fallback to primary
    val routeColorInt: Int = remember(tripDetail) {
        tripDetail?.tripInfo?.routeColor?.let {
            runCatching { AndroidColor.parseColor("#${it.trimStart('#')}") }.getOrNull()
        } ?: AndroidColor.rgb(33, 150, 243)
    }
    val routeComposeColor = Color(routeColorInt)

    // Update map overlays when trip detail or user location changes
    LaunchedEffect(tripDetail, userLat, userLon) {
        val map = mapRef.value ?: return@LaunchedEffect
        val detail = tripDetail ?: return@LaunchedEffect
        rebuildTripMap(context, map, detail, routeColorInt, userLat, userLon)
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

    Column(modifier = Modifier.fillMaxSize()) {
        // Top half: map with header overlaid on top
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            AndroidView(
                factory = { ctx ->
                    MapView(ctx).apply {
                        setTileSource(CARTO_DARK_MATTER)
                        setMultiTouchControls(true)
                        controller.setZoom(13.0)
                        controller.setCenter(GeoPoint(-27.4698, 153.0251))
                    }.also { mapRef.value = it }
                },
                modifier = Modifier.fillMaxSize()
            )

            // Header overlaid on top of the map
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopStart),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.93f)
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                        Surface(
                            color = routeComposeColor,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.padding(end = 10.dp)
                        ) {
                            Text(
                                args.routeShortName,
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                        Text(
                            args.headsign,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            modifier = Modifier.weight(1f),
                            maxLines = 1
                        )
                    }
                    HorizontalDivider()
                }
            }
        }

        // Bottom half
        val stopsListState = rememberLazyListState()
        val stops = tripDetail?.stops ?: emptyList()

        // Jump to nearest stop whenever trip data (re)loads
        LaunchedEffect(tripDetail) {
            val nearestIdx = stops.indexOfFirst { it.isNearest }
            if (nearestIdx > 0) stopsListState.scrollToItem(nearestIdx)
        }

        Column(modifier = Modifier.weight(1f)) {
            // Next trips carousel
            if (nextTrips.isNotEmpty()) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(nextTrips, key = { it.tripId }) { trip ->
                        val isCurrent = trip.tripId == tripDetail?.tripInfo?.tripId
                        TripChip(
                            trip = trip,
                            routeColor = routeComposeColor,
                            isCurrent = isCurrent,
                            onClick = { viewModel.switchTrip(trip.tripId) }
                        )
                    }
                }
                HorizontalDivider()
            }

            // Stops list
            when {
                isLoading && tripDetail == null -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }

                error != null && tripDetail == null -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Failed to load trip",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                else -> {
                    LazyColumn(
                        state = stopsListState,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(stops, key = { "${it.stopId}-${it.scheduledTime}" }) { stop ->
                            TripStopRow(stop = stop, routeColor = routeComposeColor)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TripChip(
    trip: space.snapp.waygo.data.api.models.NextTrip,
    routeColor: Color,
    isCurrent: Boolean,
    onClick: () -> Unit
) {
    val bgColor = if (isCurrent) routeColor else MaterialTheme.colorScheme.surfaceVariant
    val textShadow = Shadow(
        color = Color.Black.copy(alpha = 0.7f),
        offset = Offset(0f, 1f),
        blurRadius = 4f
    )
    Surface(
        color = bgColor,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.clip(RoundedCornerShape(20.dp))
    ) {
        TextButton(
            onClick = onClick,
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val isDelayed = trip.estimatedTime != null && trip.estimatedTime != trip.scheduledTime
                Text(
                    if (isDelayed) trip.estimatedTime!! else trip.scheduledTime,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.Bold,
                        shadow = textShadow
                    ),
                    color = Color.White
                )
                if (isDelayed) {
                    Text(
                        trip.scheduledTime,
                        style = MaterialTheme.typography.labelSmall.copy(shadow = textShadow),
                        color = Color.White.copy(alpha = 0.65f)
                    )
                }
            }
        }
    }
}

@Composable
private fun TripStopRow(stop: TripStop, routeColor: Color) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) { delay(1000); now = System.currentTimeMillis() }
    }
    val liveSeconds = ((stop.departureDate.time - now) / 1000).toInt()
    val timeText = when {
        !stop.isUpcoming -> stop.scheduledTime
        liveSeconds < 60 -> "Now"
        liveSeconds < 3600 -> "${liveSeconds / 60} min"
        else -> "${liveSeconds / 3600}h ${(liveSeconds % 3600) / 60}m"
    }
    val timeColor = when {
        !stop.isUpcoming -> MaterialTheme.colorScheme.onSurfaceVariant
        liveSeconds < 120 -> MaterialTheme.colorScheme.error
        liveSeconds < 300 -> Color(0xFFE65100)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    val dotColor = when {
        stop.isNearest -> routeColor
        stop.isUpcoming -> Color.White
        else -> Color(0xFF666666)
    }
    val strokeColor = when {
        stop.isNearest -> Color.White
        stop.isUpcoming -> routeColor
        else -> Color.Transparent
    }
    val dotSize = if (stop.isNearest) 12.dp else if (stop.isUpcoming) 10.dp else 8.dp
    val nameStyle = if (stop.isNearest)
        MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
    else
        MaterialTheme.typography.bodyMedium

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Timeline indicator
        Box(
            modifier = Modifier.width(36.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(dotSize)
                    .background(dotColor, CircleShape)
                    .then(
                        if (stop.isUpcoming || stop.isNearest)
                            Modifier.clip(CircleShape)
                        else Modifier
                    )
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                stop.stopName,
                style = nameStyle,
                maxLines = 1
            )
            Text(
                timeText,
                style = MaterialTheme.typography.bodySmall,
                color = timeColor
            )
        }

        if (stop.estimatedTime != null) {
            Text(
                stop.estimatedTime,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                color = Color(0xFFE65100),
                modifier = Modifier.padding(end = 16.dp)
            )
        } else {
            Spacer(Modifier.width(16.dp))
        }
    }
    HorizontalDivider(modifier = Modifier.padding(start = 36.dp, end = 16.dp))
}

private fun rebuildTripMap(
    context: android.content.Context,
    map: MapView,
    detail: TripDetailResponse,
    routeColorInt: Int,
    userLat: Double?,
    userLon: Double?
) {
    val dp = context.resources.displayMetrics.density
    map.overlays.clear()

    // Full route shape polyline
    if (detail.shape.size >= 2) {
        val poly = Polyline().apply {
            setPoints(detail.shape.map { GeoPoint(it[0], it[1]) })
            outlinePaint.color = routeColorInt
            outlinePaint.strokeWidth = 5f * dp
        }
        map.overlays.add(poly)
    }

    // Walking route to nearest stop (if user location is known and ≤ 1km away)
    val nearestStop = detail.stops.firstOrNull { it.isNearest }
    if (nearestStop != null && userLat != null && userLon != null) {
        val walkMeters = haversineMeters(userLat, userLon, nearestStop.lat, nearestStop.lon)
        if (walkMeters <= 1000.0) {
            val walkPoly = Polyline().apply {
                setPoints(listOf(GeoPoint(userLat, userLon), GeoPoint(nearestStop.lat, nearestStop.lon)))
                outlinePaint.color = AndroidColor.rgb(100, 180, 255)
                outlinePaint.strokeWidth = 3f * dp
                outlinePaint.pathEffect = DashPathEffect(floatArrayOf(18f * dp, 10f * dp), 0f)
            }
            map.overlays.add(walkPoly)

            // Walk time label at midpoint
            val midLat = (userLat + nearestStop.lat) / 2
            val midLon = (userLon + nearestStop.lon) / 2
            val walkMins = (walkMeters / 80).toInt().coerceAtLeast(1)
            val walkLabel = Marker(map).apply {
                position = GeoPoint(midLat, midLon)
                icon = createWalkLabel(context, "$walkMins min walk")
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                setInfoWindow(null)
            }
            map.overlays.add(walkLabel)

            // User location dot
            val userMarker = Marker(map).apply {
                position = GeoPoint(userLat, userLon)
                icon = createUserDotIcon(context)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                setInfoWindow(null)
            }
            map.overlays.add(userMarker)
        }
    }

    // Stop markers
    for (stop in detail.stops) {
        val marker = Marker(map).apply {
            position = GeoPoint(stop.lat, stop.lon)
            icon = createTripStopIcon(context, stop, routeColorInt)
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            title = stop.stopName
            setInfoWindow(null)
        }
        map.overlays.add(marker)
    }

    // Vehicle position marker
    detail.vehicle?.let { v ->
        val vehicleMarker = Marker(map).apply {
            position = GeoPoint(v.lat, v.lon)
            icon = createTripVehicleIcon(context, routeColorInt, v.bearing)
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            setInfoWindow(null)
        }
        map.overlays.add(vehicleMarker)
    }

    // Auto-fit to upcoming stops
    val upcomingPoints = detail.stops
        .filter { it.isUpcoming }
        .map { GeoPoint(it.lat, it.lon) }

    if (upcomingPoints.isNotEmpty()) {
        try {
            val bbox = BoundingBox.fromGeoPoints(upcomingPoints)
            map.post {
                runCatching { map.zoomToBoundingBox(bbox, true, (80 * dp).toInt()) }
            }
        } catch (_: Exception) {}
    } else if (detail.stops.isNotEmpty()) {
        val all = detail.stops.map { GeoPoint(it.lat, it.lon) }
        try {
            val bbox = BoundingBox.fromGeoPoints(all)
            map.post {
                runCatching { map.zoomToBoundingBox(bbox, true, (80 * dp).toInt()) }
            }
        } catch (_: Exception) {}
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

private fun createWalkLabel(context: android.content.Context, text: String): BitmapDrawable {
    val dp = context.resources.displayMetrics.density
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 11f * dp
        color = AndroidColor.WHITE
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }
    val textWidth = paint.measureText(text)
    val padH = 6f * dp
    val padV = 4f * dp
    val w = (textWidth + padH * 2).toInt().coerceAtLeast(1)
    val h = (paint.textSize + padV * 2).toInt().coerceAtLeast(1)
    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(bitmap)
    val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.argb(200, 30, 100, 200)
        style = Paint.Style.FILL
    }
    canvas.drawRoundRect(0f, 0f, w.toFloat(), h.toFloat(), 6f * dp, 6f * dp, bgPaint)
    canvas.drawText(text, padH, h - padV - paint.descent(), paint)
    return BitmapDrawable(context.resources, bitmap)
}

private fun createTripVehicleIcon(
    context: android.content.Context,
    routeColorInt: Int,
    bearing: Float
): BitmapDrawable {
    val dp = context.resources.displayMetrics.density
    val size = (34 * dp).toInt().coerceAtLeast(8)
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(bitmap)
    val cx = size / 2f
    val cy = size / 2f
    val radius = size / 2f - 2f * dp

    // Filled circle
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = routeColorInt
        style = Paint.Style.FILL
    }
    canvas.drawCircle(cx, cy, radius, paint)

    // White border
    paint.color = AndroidColor.WHITE
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = 2f * dp
    canvas.drawCircle(cx, cy, radius, paint)

    // Bearing arrow
    canvas.save()
    canvas.rotate(bearing, cx, cy)
    val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.WHITE
        style = Paint.Style.FILL
    }
    val arrowPath = AndroidPath().apply {
        val half = radius * 0.32f
        moveTo(cx, cy - radius * 0.72f)        // tip (north before rotation)
        lineTo(cx - half, cy - radius * 0.05f)  // left
        lineTo(cx + half, cy - radius * 0.05f)  // right
        close()
    }
    canvas.drawPath(arrowPath, arrowPaint)
    canvas.restore()

    return BitmapDrawable(context.resources, bitmap)
}

private fun createTripStopIcon(
    context: android.content.Context,
    stop: TripStop,
    routeColorInt: Int
): BitmapDrawable {
    val dp = context.resources.displayMetrics.density
    val sizeDp = if (stop.isNearest) 16 else if (stop.isUpcoming) 10 else 8
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
            strokeWidth = 1.5f * dp
            color = if (stop.isNearest) AndroidColor.WHITE else routeColorInt
        }
        canvas.drawCircle(size / 2f, size / 2f, size / 2f - dp, strokePaint)
    }

    return BitmapDrawable(context.resources, bitmap)
}
