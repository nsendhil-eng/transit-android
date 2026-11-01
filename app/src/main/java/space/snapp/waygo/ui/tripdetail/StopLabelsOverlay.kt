package space.snapp.waygo.ui.tripdetail

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Point
import android.graphics.RectF
import android.graphics.Typeface
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay
import kotlin.math.abs
import kotlin.math.hypot

/** One stop's label on the trip map. Higher [priority] wins when labels would overlap. */
data class StopLabel(
    val stopId: String,
    val text: String,
    val point: GeoPoint,
    val priority: Int,
    val bold: Boolean,
    val dimmed: Boolean,
    /** Route direction at this stop (screen-independent, as a lat/lon delta) — labels go beside it */
    val alongLat: Double,
    val alongLon: Double
)

/** Below this zoom only the key stops (yours, the bus's next, the terminus) are labelled. */
private const val ALL_LABELS_ZOOM = 15.0
const val PRIORITY_KEY = 2

/**
 * Draws stop names next to the route's dots. Recomputed every frame from the current
 * projection, so labels appear/disappear with zoom and never overlap: higher-priority labels
 * are placed first, and a label that can't find a free spot on either side of its dot is
 * skipped. Each label sits perpendicular to the route line so it never covers it.
 */
class StopLabelsOverlay(
    private val labels: List<StopLabel>,
    density: Float,
    /** Height of UI floating over the top of the map (the header); no labels under it */
    private val topInsetPx: Float = 0f,
    /** Dot centre to label edge, dp: bigger for the home map's larger stop markers */
    gapDp: Float = 9f,
    /** Below this zoom only PRIORITY_KEY labels are drawn */
    private val allLabelsZoom: Double = ALL_LABELS_ZOOM,
    /** Radius of the stop markers, dp; when set, labels also keep off every marker */
    dotRadiusDp: Float = 0f
) : Overlay() {
    private val dp = density
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 11.5f * dp
        color = Color.WHITE
    }
    private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 11.5f * dp
        color = Color.argb(235, 15, 18, 22)
        style = Paint.Style.STROKE
        strokeWidth = 3.5f * dp
        strokeJoin = Paint.Join.ROUND
    }
    private val regular = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
    private val bold = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    private val gap = gapDp * dp
    private val dotR = dotRadiusDp * dp
    /** Another point labels keep off (your location dot) */
    var avoid: GeoPoint? = null
    private val p = Point()
    private val q = Point()

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow || labels.isEmpty()) return
        val proj = mapView.projection
        val showAll = mapView.zoomLevelDouble >= allLabelsZoom
        val placed = ArrayList<RectF>()
        val viewW = mapView.width.toFloat()
        val viewH = mapView.height.toFloat()
        if (dotR > 0f) for (label in labels) {
            proj.toPixels(label.point, p)
            placed += RectF(p.x - dotR, p.y - dotR, p.x + dotR, p.y + dotR)
        }
        avoid?.let {
            proj.toPixels(it, p)
            placed += RectF(p.x - 12 * dp, p.y - 12 * dp, p.x + 12 * dp, p.y + 12 * dp)
        }

        for (label in labels.sortedByDescending { it.priority }) {
            if (!showAll && label.priority < PRIORITY_KEY) continue
            proj.toPixels(label.point, p)
            if (p.x < -50 || p.y < -50 || p.x > viewW + 50 || p.y > viewH + 50) continue

            // Route direction on screen at this stop, then its perpendicular (screen y points down)
            proj.toPixels(GeoPoint(label.point.latitude + label.alongLat, label.point.longitude + label.alongLon), q)
            var dx = (q.x - p.x).toFloat()
            var dy = (q.y - p.y).toFloat()
            val len = hypot(dx, dy).takeIf { it > 0.5f } ?: 1f
            dx /= len; dy /= len
            val px = -dy
            val py = dx

            val face = if (label.bold) bold else regular
            fill.typeface = face
            outline.typeface = face
            val w = fill.measureText(label.text)
            val h = fill.textSize

            // Try the right-hand side of travel first, then the left
            var rect: RectF? = null
            for (side in floatArrayOf(1f, -1f)) {
                val ax = p.x + px * gap * side
                val ay = p.y + py * gap * side
                // Anchor the label's near edge to the offset point, centred vertically
                val left = if (px * side >= 0) ax else ax - w
                val top = ay - h / 2 + (if (abs(py) > 0.7f) py * side * h / 2 else 0f)
                val r = RectF(left, top, left + w, top + h)
                if (r.left < 0 || r.right > viewW || r.top < topInsetPx || r.bottom > viewH) continue
                if (placed.none { RectF.intersects(it, padded(r)) }) { rect = r; break }
            }
            val r = rect ?: continue
            placed += r

            val alpha = if (label.dimmed) 140 else 255
            fill.alpha = alpha
            outline.alpha = (alpha * 0.92f).toInt()
            val baseline = r.bottom - fill.descent() * 0.6f
            canvas.drawText(label.text, r.left, baseline, outline)
            canvas.drawText(label.text, r.left, baseline, fill)
        }
    }

    private fun padded(r: RectF) = RectF(r.left - 3 * dp, r.top - 2 * dp, r.right + 3 * dp, r.bottom + 2 * dp)
}

/**
 * Short map label for a stop: "Coronation Dr at Wesley Hospital, stop 9" -> "Wesley Hospital",
 * "Roma Street busway, platform 2" -> "Roma Street busway". The full name is in the list below.
 */
fun mapStopName(name: String): String {
    val core = name.substringAfter(" at ", name)
    return core
        .replace(Regex(""",\s*(stop|platform|zone)\b.*$""", RegexOption.IGNORE_CASE), "")
        .replace(Regex("""\s+(stop|platform)\s+[\w/]+$""", RegexOption.IGNORE_CASE), "")
        .trim()
        .ifEmpty { name }
}
