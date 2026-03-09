package space.snapp.waygo.ui.plan

import android.graphics.Color as AndroidColor
import android.graphics.DashPathEffect
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import space.snapp.waygo.data.api.models.Itinerary
import space.snapp.waygo.data.api.models.ItineraryLeg

private val CARTO_DARK = XYTileSource(
    "CartoDB.DarkMatter", 0, 19, 256, ".png",
    arrayOf(
        "https://a.basemaps.cartocdn.com/dark_all/",
        "https://b.basemaps.cartocdn.com/dark_all/",
        "https://c.basemaps.cartocdn.com/dark_all/"
    ),
    "© OpenStreetMap contributors © CARTO"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItineraryDetailScreen(
    itinerary: Itinerary,
    fromName: String,
    toName: String,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mapRef = remember { mutableStateOf<MapView?>(null) }
    val dp = context.resources.displayMetrics.density

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

    LaunchedEffect(itinerary) {
        val map = mapRef.value ?: return@LaunchedEffect
        buildItineraryMap(map, itinerary, dp)
    }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // Map with header overlay
        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            AndroidView(
                factory = { ctx ->
                    MapView(ctx).apply {
                        setTileSource(CARTO_DARK)
                        setMultiTouchControls(true)
                        controller.setZoom(13.0)
                        controller.setCenter(GeoPoint(-27.4698, 153.0251))
                    }.also { mapRef.value = it }
                },
                update = { map ->
                    if (mapRef.value == null) {
                        mapRef.value = map
                        buildItineraryMap(map, itinerary, dp)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            // Header overlay
            Surface(
                modifier = Modifier.fillMaxWidth().align(Alignment.TopStart),
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
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                fromName,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                "→ $toName",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Column(
                            horizontalAlignment = Alignment.End,
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(20.dp)
                            ) {
                                Text(
                                    "${itinerary.durationMins} min",
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            Text(
                                "${formatTime(itinerary.startTime)} → ${formatTime(itinerary.endTime)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }
                    HorizontalDivider()
                }
            }
        }

        // Journey timeline
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(top = 12.dp, bottom = 16.dp)
        ) {
            items(itinerary.legs.size) { index ->
                val leg = itinerary.legs[index]
                val isLast = index == itinerary.legs.lastIndex
                TimelineLegRow(leg = leg, isLast = isLast)
            }
            // Final destination row
            item {
                val lastLeg = itinerary.legs.last()
                TimelineEndRow(time = lastLeg.endTime, label = toName.ifBlank { lastLeg.to.name })
            }
        }
    }
}

@Composable
private fun TimelineLegRow(leg: ItineraryLeg, isLast: Boolean) {
    val legColor = if (leg.isTransit) {
        leg.routeColor?.let {
            runCatching { Color(AndroidColor.parseColor("#$it")) }.getOrNull()
        } ?: MaterialTheme.colorScheme.primary
    } else Color(0xFF888888)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Time column
        Text(
            formatTime(leg.startTime),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, fontSize = 11.sp),
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End,
            modifier = Modifier.width(52.dp).padding(top = 3.dp)
        )

        Spacer(Modifier.width(10.dp))

        // Timeline: dot + line
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(legColor, CircleShape)
            )
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(if (leg.isTransit) 72.dp else 48.dp)
                        .background(legColor.copy(alpha = 0.35f))
                )
            }
        }

        Spacer(Modifier.width(10.dp))

        // Content
        Column(modifier = Modifier.weight(1f).padding(bottom = 8.dp)) {
            if (leg.isTransit) {
                // Board stop + time
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(color = legColor, shape = RoundedCornerShape(6.dp)) {
                        Text(
                            leg.routeShortName ?: leg.mode,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color.White,
                            maxLines = 1,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    Text(
                        leg.headsign ?: leg.routeLongName ?: "",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        "${leg.durationMins} min",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    "Board  ${leg.from.name}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "Alight  ${leg.to.name}  ·  ${formatTime(leg.endTime)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            } else {
                val distText = if (leg.distance < 1000) "${leg.distance}m" else "${"%.1f".format(leg.distance / 1000f)}km"
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.DirectionsWalk,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = legColor
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "Walk $distText",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "${leg.durationMins} min",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (leg.steps.isNotEmpty()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        leg.steps.first().streetName.ifBlank { "path" },
                        style = MaterialTheme.typography.bodySmall,
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
private fun TimelineEndRow(time: Long, label: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            formatTime(time),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, fontSize = 11.sp),
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End,
            modifier = Modifier.width(52.dp)
        )
        Spacer(Modifier.width(10.dp))
        Box(
            modifier = Modifier
                .size(12.dp)
                .background(MaterialTheme.colorScheme.onSurface, CircleShape)
        )
        Spacer(Modifier.width(10.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

private fun formatTime(epochMs: Long): String {
    val cal = java.util.Calendar.getInstance()
    cal.timeInMillis = epochMs
    val h = cal.get(java.util.Calendar.HOUR)
    val m = cal.get(java.util.Calendar.MINUTE)
    val ampm = if (cal.get(java.util.Calendar.AM_PM) == java.util.Calendar.AM) "AM" else "PM"
    val hDisplay = if (h == 0) 12 else h
    return "%d:%02d %s".format(hDisplay, m, ampm)
}

private fun buildItineraryMap(map: MapView, itinerary: Itinerary, dp: Float) {
    map.overlays.clear()
    val allPoints = mutableListOf<GeoPoint>()

    for (leg in itinerary.legs) {
        val encoded = leg.legGeometry ?: continue
        val points = try { decodePolyline(encoded) } catch (e: Exception) { continue }
        if (points.size < 2) continue
        allPoints.addAll(points)

        val legColorInt = if (leg.isTransit) {
            leg.routeColor?.let {
                runCatching { AndroidColor.parseColor("#$it") }.getOrNull()
            } ?: AndroidColor.rgb(33, 150, 243)
        } else AndroidColor.rgb(150, 150, 150)

        val polyline = Polyline().apply {
            setPoints(points)
            outlinePaint.color = legColorInt
            outlinePaint.strokeWidth = if (leg.isTransit) 6f * dp else 3f * dp
            if (!leg.isTransit) {
                outlinePaint.pathEffect = DashPathEffect(floatArrayOf(14f * dp, 8f * dp), 0f)
            }
        }
        map.overlays.add(polyline)
    }

    // Board/alight markers with stop name labels for transit legs
    for (leg in itinerary.legs.filter { it.isTransit }) {
        val legColorInt = leg.routeColor?.let {
            runCatching { AndroidColor.parseColor("#$it") }.getOrNull()
        } ?: AndroidColor.rgb(33, 150, 243)

        listOf(
            GeoPoint(leg.from.lat, leg.from.lon) to leg.from.name,
            GeoPoint(leg.to.lat, leg.to.lon) to leg.to.name
        ).forEach { (pt, name) ->
            val marker = Marker(map).apply {
                position = pt
                icon = createStopLabelIcon(map.context, name, legColorInt, dp)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                setInfoWindow(null)
            }
            map.overlays.add(marker)
        }
    }

    // Fit map to all points
    if (allPoints.size >= 2) {
        try {
            val bbox = BoundingBox.fromGeoPoints(allPoints)
            map.post { runCatching { map.zoomToBoundingBox(bbox, true, (80 * dp).toInt()) } }
        } catch (_: Exception) {}
    }

    map.invalidate()
}

private fun createStopLabelIcon(
    context: android.content.Context,
    stopName: String,
    dotColorInt: Int,
    dp: Float
): android.graphics.drawable.BitmapDrawable {
    val label = if (stopName.length > 22) stopName.take(22) + "…" else stopName
    val dotRadius = 5f * dp
    val stemH = 6f * dp
    val pillPadH = 6f * dp
    val pillPadV = 3f * dp

    val textPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.WHITE
        textSize = 10f * dp
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }
    val textW = textPaint.measureText(label)
    val pillW = textW + pillPadH * 2
    val pillH = textPaint.textSize + pillPadV * 2

    val bmpW = pillW.coerceAtLeast(dotRadius * 2 + 2 * dp)
    val bmpH = pillH + stemH + dotRadius * 2

    val bmp = android.graphics.Bitmap.createBitmap(bmpW.toInt().coerceAtLeast(1), bmpH.toInt().coerceAtLeast(1), android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bmp)

    // Pill background
    val pillLeft = (bmpW - pillW) / 2f
    val bgPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.argb(220, 20, 20, 20)
        style = android.graphics.Paint.Style.FILL
    }
    canvas.drawRoundRect(pillLeft, 0f, pillLeft + pillW, pillH, pillH / 2, pillH / 2, bgPaint)

    // Stop name text
    val textY = pillPadV + textPaint.textSize - textPaint.descent()
    canvas.drawText(label, pillLeft + pillPadH, textY, textPaint)

    // Stem
    val cx = bmpW / 2f
    val stemPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.argb(200, 20, 20, 20)
        strokeWidth = 1.5f * dp
    }
    canvas.drawLine(cx, pillH, cx, pillH + stemH, stemPaint)

    // Dot
    val dotCy = pillH + stemH + dotRadius
    val dotPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = dotColorInt
        style = android.graphics.Paint.Style.FILL
    }
    canvas.drawCircle(cx, dotCy, dotRadius, dotPaint)
    val strokePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.WHITE
        style = android.graphics.Paint.Style.STROKE
        strokeWidth = 1.5f * dp
    }
    canvas.drawCircle(cx, dotCy, dotRadius - 0.75f * dp, strokePaint)

    return android.graphics.drawable.BitmapDrawable(context.resources, bmp)
}
