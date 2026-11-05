package space.snapp.waygo.ui.tripdetail

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Point
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
    labels: List<StopLabel>,
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

    // Everything draw() needs is worked out here: it runs every frame while panning, so it can't
    // sort, measure text or allocate
    private var sorted: List<StopLabel> = emptyList()
    private var alongPoints: List<GeoPoint> = emptyList()
    private var widths = FloatArray(0)
    // Rectangles already taken this frame, as left, top, right, bottom
    private var taken = FloatArray(0)
    private var takenCount = 0

    /** The labels to draw; replaces the current ones */
    var labels: List<StopLabel> = emptyList()
        set(value) {
            field = value
            sorted = value.sortedByDescending { it.priority }
            alongPoints = sorted.map { GeoPoint(it.point.latitude + it.alongLat, it.point.longitude + it.alongLon) }
            widths = FloatArray(sorted.size) { i ->
                fill.typeface = if (sorted[i].bold) bold else regular
                fill.measureText(sorted[i].text)
            }
            // Each label can take a dot and a text rectangle, plus the avoid point
            taken = FloatArray((sorted.size * 2 + 1) * 4)
        }

    init { this.labels = labels }

    private fun take(left: Float, top: Float, right: Float, bottom: Float) {
        val i = takenCount++ * 4
        taken[i] = left; taken[i + 1] = top; taken[i + 2] = right; taken[i + 3] = bottom
    }

    /** Whether the rectangle touches anything already taken */
    private fun isTaken(left: Float, top: Float, right: Float, bottom: Float): Boolean {
        for (n in 0 until takenCount) {
            val i = n * 4
            if (left < taken[i + 2] && taken[i] < right && top < taken[i + 3] && taken[i + 1] < bottom) return true
        }
        return false
    }

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow || sorted.isEmpty()) return
        val proj = mapView.projection
        val showAll = mapView.zoomLevelDouble >= allLabelsZoom
        val viewW = mapView.width.toFloat()
        val viewH = mapView.height.toFloat()
        takenCount = 0
        if (dotR > 0f) for (label in sorted) {
            proj.toPixels(label.point, p)
            take(p.x - dotR, p.y - dotR, p.x + dotR, p.y + dotR)
        }
        avoid?.let {
            proj.toPixels(it, p)
            take(p.x - 12 * dp, p.y - 12 * dp, p.x + 12 * dp, p.y + 12 * dp)
        }
        val padX = 3 * dp
        val padY = 2 * dp

        for (index in sorted.indices) {
            val label = sorted[index]
            if (!showAll && label.priority < PRIORITY_KEY) continue
            proj.toPixels(label.point, p)
            if (p.x < -50 || p.y < -50 || p.x > viewW + 50 || p.y > viewH + 50) continue

            // Route direction on screen at this stop, then its perpendicular (screen y points down)
            proj.toPixels(alongPoints[index], q)
            var dx = (q.x - p.x).toFloat()
            var dy = (q.y - p.y).toFloat()
            val len = hypot(dx, dy).takeIf { it > 0.5f } ?: 1f
            dx /= len; dy /= len
            val px = -dy
            val py = dx

            val face = if (label.bold) bold else regular
            fill.typeface = face
            outline.typeface = face
            val w = widths[index]
            val h = fill.textSize

            // Try the right-hand side of travel first, then the left
            var placed = false
            var labelLeft = 0f
            var labelBottom = 0f
            var side = 1f
            for (attempt in 0..1) {
                val ax = p.x + px * gap * side
                val ay = p.y + py * gap * side
                // Anchor the label's near edge to the offset point, centred vertically
                val left = if (px * side >= 0) ax else ax - w
                val top = ay - h / 2 + (if (abs(py) > 0.7f) py * side * h / 2 else 0f)
                val right = left + w
                val bottom = top + h
                val fits = left >= 0 && right <= viewW && top >= topInsetPx && bottom <= viewH
                if (fits && !isTaken(left - padX, top - padY, right + padX, bottom + padY)) {
                    take(left, top, right, bottom)
                    labelLeft = left; labelBottom = bottom
                    placed = true
                    break
                }
                side = -1f
            }
            if (!placed) continue

            val alpha = if (label.dimmed) 140 else 255
            fill.alpha = alpha
            outline.alpha = (alpha * 0.92f).toInt()
            val baseline = labelBottom - fill.descent() * 0.6f
            canvas.drawText(label.text, labelLeft, baseline, outline)
            canvas.drawText(label.text, labelLeft, baseline, fill)
        }
    }
}

private val NUMBERED_STOP = Regex("""^(.+?)\s+Stop\s+\w+\s+near\s+(.+)$""", RegexOption.IGNORE_CASE)
private val STOP_SUFFIX = Regex(""",\s*(stop|platform|zone)\b.*$""", RegexOption.IGNORE_CASE)
private val STOP_TAIL = Regex("""\s+(stop|platform)\s+[\w/]+$""", RegexOption.IGNORE_CASE)

/**
 * Short map label for a stop: "Coronation Dr at Wesley Hospital, stop 9" -> "Wesley Hospital",
 * "Roma Street busway, platform 2" -> "Roma Street busway". The full name is in the list below.
 */
fun mapStopName(name: String): String {
    // City stops numbered mid-name: "Wickham Terrace Stop 132 near Albert St" -> "Wickham Tce near Albert St"
    NUMBERED_STOP.find(name)?.let { m ->
        return "${abbreviateStreet(m.groupValues[1])} near ${m.groupValues[2]}"
    }
    val core = name.substringAfter(" at ", name)
    return core
        .replace(STOP_SUFFIX, "")
        .replace(STOP_TAIL, "")
        .trim()
        .ifEmpty { name }
}

/** "Wickham Terrace" -> "Wickham Tce": the street type at the end, as on street signs */
private fun abbreviateStreet(street: String): String {
    val types = mapOf("Street" to "St", "Terrace" to "Tce", "Road" to "Rd", "Avenue" to "Ave", "Parade" to "Pde", "Drive" to "Dr")
    val last = street.substringAfterLast(' ')
    return types[last]?.let { street.dropLast(last.length) + it } ?: street
}
