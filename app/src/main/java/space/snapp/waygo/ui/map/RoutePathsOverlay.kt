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

/**
 * The whole routes of the home map's upcoming services, each a raised ribbon: a shadow
 * beneath, the route colour, a thin highlight on top. Static: the live vehicles on them are
 * what moves.
 */
class RoutePathsOverlay(private val dp: Float) : Overlay() {
    /** Dimmed lines first, so they sit underneath */
    var paths: List<RoutePath> = emptyList()
        set(value) { field = value.sortedBy { if (it.dimmed) 0 else 1 } }

    // Drawn every frame while panning, so nothing here is allocated per frame
    private val pt = Point()
    private val path = Path()
    private var xs = FloatArray(0)
    private var ys = FloatArray(0)
    private var normalX = 0f
    private var normalY = 0f

    private fun addSegment(a: Int, b: Int, n: Int) {
        if (a < 0 || b >= n) return
        val dx = xs[b] - xs[a]
        val dy = ys[b] - ys[a]
        val len = kotlin.math.hypot(dx, dy)
        if (len < 0.01f) return
        normalX += dy / len; normalY += -dx / len
    }

    /** Unit vector to the left of travel at screen point [i] (y points down), averaged over its two segments */
    private fun leftNormal(i: Int, n: Int) {
        normalX = 0f; normalY = 0f
        addSegment(i - 1, i, n)
        addSegment(i, i + 1, n)
        val len = kotlin.math.hypot(normalX, normalY)
        if (len < 0.01f) { normalX = 0f; normalY = 0f } else { normalX /= len; normalY /= len }
    }

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
        for (p in paths) {
            val n = p.points.size
            if (xs.size < n) { xs = FloatArray(n); ys = FloatArray(n) }
            for (i in 0 until n) {
                proj.toPixels(p.points[i], pt)
                xs[i] = pt.x.toFloat(); ys[i] = pt.y.toFloat()
            }
            path.rewind()
            for (i in 0 until n) {
                leftNormal(i, n)
                val x = xs[i] + normalX * lane
                val y = ys[i] + normalY * lane
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
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
