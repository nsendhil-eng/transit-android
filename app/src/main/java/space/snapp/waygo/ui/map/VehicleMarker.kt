package space.snapp.waygo.ui.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.util.LruCache
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorPath
import space.snapp.waygo.data.api.models.VehicleType
import space.snapp.waygo.ui.components.icon
import kotlin.math.max
import kotlin.math.roundToInt

/** The marker's round token: the vehicle sits on the map at about this size */
private const val TOKEN_DP = 24f
private const val ICON_DP = 16f
/** Material icons are drawn on a 24 unit grid */
private const val ICON_GRID = 24f
/** How far the arrow's tip reaches past the token's edge */
private const val ARROW_REACH_DP = 7f
private const val TAG_HEIGHT_DP = 16f
private const val TAG_GAP_DP = 3f
private const val SHADOW_DP = 2f

/**
 * Live vehicle marker: a round token in the route's colour, holding the same vehicle icon the
 * lists use, with an arrow on its edge pointing the way it's going. Only the arrow turns, so the
 * icon and the route number stay upright and read the same at every heading.
 *
 * With [heading] null the arrow is left out: no arrow beats a wrong one. [label] empty (rail,
 * whose short names are internal codes) leaves out the number. [age] ("40s") adds a small dark
 * tag. The token sits at the bitmap's centre, so anchor the marker there.
 */
fun createVehicleMarkerIcon(
    context: Context,
    mode: VehicleType,
    label: String,
    routeColor: Int,
    routeTextColor: Int,
    heading: Float?,
    age: String? = null
): BitmapDrawable {
    // Age tags change by the second, so those aren't worth keeping
    if (age != null) return BitmapDrawable(context.resources, drawVehicleMarker(context, mode, label, routeColor, routeTextColor, heading, age))
    val rounded = roundedHeading(heading)
    val key = VehicleIconKey(mode, label, routeColor, routeTextColor, rounded)
    val bitmap = vehicleIcons.get(key)
        ?: drawVehicleMarker(context, mode, label, routeColor, routeTextColor, rounded, null).also { vehicleIcons.put(key, it) }
    // A new drawable each time: markers set alpha on theirs
    return BitmapDrawable(context.resources, bitmap)
}

/** Vehicle icons differ by so little that the nearest HEADING_STEP degrees looks the same */
private const val HEADING_STEP = 5f

fun roundedHeading(heading: Float?): Float? = heading?.let { (it / HEADING_STEP).roundToInt() * HEADING_STEP % 360f }

/** The route number goes under the token, or above it when the arrow points down that way */
fun numberGoesAbove(heading: Float?): Boolean = heading != null && heading in 110f..250f

data class VehicleIconKey(val mode: VehicleType, val label: String, val routeColor: Int, val routeTextColor: Int, val heading: Float?)

private val vehicleIcons = LruCache<VehicleIconKey, Bitmap>(64)
private val iconPaths = HashMap<VehicleType, Path>()

/** The list's icon as a path for the map: the same drawing, so the two always match */
private fun iconPath(mode: VehicleType): Path = iconPaths.getOrPut(mode) {
    val out = Path()
    fun add(group: VectorGroup) {
        for (node in group) when (node) {
            is VectorGroup -> add(node)
            is VectorPath -> {
                val part = PathParser().addPathNodes(node.pathData).toPath().asAndroidPath()
                if (node.pathFillType == PathFillType.EvenOdd) part.fillType = Path.FillType.EVEN_ODD
                out.addPath(part)
            }
        }
    }
    add(mode.icon().root)
    out
}

private fun drawVehicleMarker(
    context: Context,
    mode: VehicleType,
    label: String,
    routeColor: Int,
    routeTextColor: Int,
    heading: Float?,
    age: String?
): Bitmap {
    val dp = context.resources.displayMetrics.density
    val radius = TOKEN_DP / 2 * dp
    val reach = radius + ARROW_REACH_DP * dp
    val tagH = TAG_HEIGHT_DP * dp
    val tagGap = TAG_GAP_DP * dp
    val shadow = SHADOW_DP * dp

    val number = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = routeTextColor
        textSize = 11f * dp
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    val ageText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 10f * dp
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }
    val numberW = if (label.isEmpty()) 0f else max(tagH * 1.3f, number.measureText(label) + 10f * dp)
    val ageW = age?.let { ageText.measureText(it) + 10f * dp } ?: 0f

    // Symmetrical around the token, so anchoring at the centre puts the token on the vehicle
    val halfW = max(reach, max(numberW, ageW) / 2) + shadow
    val halfH = reach + tagH + tagGap + shadow
    val size = (halfW * 2).toInt() + 1
    val height = (halfH * 2).toInt() + 1
    val bitmap = Bitmap.createBitmap(size, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val cx = size / 2f
    val cy = height / 2f

    if (heading != null) drawArrow(canvas, cx, cy, heading, radius, reach, dp)

    val token = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = routeColor
        setShadowLayer(shadow, 0f, dp, Color.argb(120, 0, 0, 0))
    }
    canvas.drawCircle(cx, cy, radius, token)
    // A white ring keeps even Translink's darkest colours visible on the dark map
    canvas.drawCircle(cx, cy, radius - 0.75f * dp, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * dp
        color = Color.WHITE
    })

    val iconSize = ICON_DP * dp
    canvas.save()
    canvas.translate(cx - iconSize / 2, cy - iconSize / 2)
    canvas.scale(iconSize / ICON_GRID, iconSize / ICON_GRID)
    canvas.drawPath(iconPath(mode), Paint(Paint.ANTI_ALIAS_FLAG).apply { color = routeTextColor })
    canvas.restore()

    val above = numberGoesAbove(heading)
    val nearTop = cy - radius - tagGap - tagH
    val nearBottom = cy + radius + tagGap
    if (label.isNotEmpty()) {
        val top = if (above) nearTop else nearBottom
        val rect = RectF(cx - numberW / 2, top, cx + numberW / 2, top + tagH)
        canvas.drawRoundRect(rect, 5f * dp, 5f * dp, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = routeColor
            setShadowLayer(shadow, 0f, dp, Color.argb(120, 0, 0, 0))
        })
        canvas.drawRoundRect(rect, 5f * dp, 5f * dp, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1f * dp
            color = Color.WHITE
        })
        canvas.drawText(label, cx, rect.centerY() - (number.descent() + number.ascent()) / 2, number)
    }
    if (age != null) {
        // The other side from the number
        val top = if (above) nearBottom else nearTop
        val rect = RectF(cx - ageW / 2, top, cx + ageW / 2, top + tagH)
        canvas.drawRoundRect(rect, 8f * dp, 8f * dp, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(225, 20, 20, 20) })
        canvas.drawText(age, cx, rect.centerY() - (ageText.descent() + ageText.ascent()) / 2, ageText)
    }
    return bitmap
}

/** A white arrowhead on the token's edge, pointing [heading] degrees clockwise from north */
private fun drawArrow(canvas: Canvas, cx: Float, cy: Float, heading: Float, radius: Float, reach: Float, dp: Float) {
    canvas.save()
    canvas.rotate(heading, cx, cy)
    val head = Path().apply {
        moveTo(cx, cy - reach)
        lineTo(cx + 6f * dp, cy - radius + dp)
        lineTo(cx - 6f * dp, cy - radius + dp)
        close()
    }
    canvas.drawPath(head, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL_AND_STROKE
        strokeWidth = 2f * dp
        strokeJoin = Paint.Join.ROUND
        color = Color.argb(200, 15, 18, 22)
    })
    canvas.drawPath(head, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })
    canvas.restore()
}
