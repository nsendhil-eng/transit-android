package space.snapp.waygo.ui.map

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Point
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay

/** The whole route of one of the cards' next services */
data class RoutePath(
    val points: List<GeoPoint>,
    val color: Int,
    /** Another card is focused: drawn faint */
    val dimmed: Boolean
)

/**
 * Each line is drawn this far to the left of its direction of travel (traffic keeps left),
 * so the two directions along one road sit side by side instead of on top of each other
 */
private const val LANE_OFFSET_DP = 3.5f

/** Unit vector to the left of travel at screen point [i] (y points down), averaged over its two segments */
private fun leftNormal(xy: List<FloatArray>, i: Int): Pair<Float, Float> {
    var sx = 0f
    var sy = 0f
    for ((a, b) in listOf(i - 1 to i, i to i + 1)) {
        if (a < 0 || b >= xy.size) continue
        val dx = xy[b][0] - xy[a][0]
        val dy = xy[b][1] - xy[a][1]
        val len = kotlin.math.hypot(dx, dy)
        if (len < 0.01f) continue
        sx += dy / len; sy += -dx / len
    }
    val len = kotlin.math.hypot(sx, sy)
    return if (len < 0.01f) 0f to 0f else (sx / len) to (sy / len)
}

/**
 * The whole routes of the home map's upcoming services, each a raised ribbon: a shadow
 * beneath, the route colour, a thin highlight on top. Static: the live vehicles on them are
 * what moves.
 */
class RoutePathsOverlay(private val dp: Float) : Overlay() {
    var paths: List<RoutePath> = emptyList()

    private val pt = Point()
    private fun stroke(width: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
        strokeWidth = width * dp
    }
    private val shadowPaint = stroke(9f).apply { color = Color.BLACK }
    private val bodyPaint = stroke(5f)
    private val highlightPaint = stroke(1.4f).apply { color = Color.WHITE }

    override fun draw(canvas: Canvas, map: MapView, shadow: Boolean) {
        if (shadow || paths.isEmpty()) return
        val proj = map.projection
        val lane = LANE_OFFSET_DP * dp
        // Dimmed lines underneath
        for (p in paths.sortedBy { if (it.dimmed) 0 else 1 }) {
            val xy = p.points.map { g -> proj.toPixels(g, pt); floatArrayOf(pt.x.toFloat(), pt.y.toFloat()) }
            val path = Path().apply {
                xy.forEachIndexed { i, (x, y) ->
                    val (nx, ny) = leftNormal(xy, i)
                    if (i == 0) moveTo(x + nx * lane, y + ny * lane) else lineTo(x + nx * lane, y + ny * lane)
                }
            }
            val a = if (p.dimmed) 0.3f else 1f
            bodyPaint.color = p.color
            drawShifted(canvas, path, shadowPaint, 90 * a, dy = 2.5f * dp)
            drawShifted(canvas, path, bodyPaint, 255 * a, dy = 0f)
            drawShifted(canvas, path, highlightPaint, 80 * a, dy = -1.2f * dp)
        }
    }

    private fun drawShifted(canvas: Canvas, path: Path, paint: Paint, alpha: Float, dy: Float) {
        canvas.save()
        canvas.translate(0f, dy)
        paint.alpha = alpha.toInt()
        canvas.drawPath(path, paint)
        canvas.restore()
    }
}
