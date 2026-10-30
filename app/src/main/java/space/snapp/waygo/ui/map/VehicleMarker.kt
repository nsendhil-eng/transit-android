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

/**
 * Live vehicle marker: a route-coloured pill with the route number, and a white badge at its
 * leading end holding a real arrow (shaft + head) that points the direction of travel.
 *
 * Only the arrow rotates — the number stays upright and never overlaps it, so the marker
 * reads the same at every heading (a bare triangle pointing south looks like a map pin).
 * With [heading] null the badge is left out: no arrow beats a wrong one.
 *
 * [label] empty (rail, whose short names are internal codes) draws just the arrow badge.
 * [age] ("40s") adds a small dark tag under the pill. Anchor the marker at its centre.
 */
fun createVehicleMarkerIcon(
    context: Context,
    label: String,
    routeColor: Int,
    routeTextColor: Int,
    heading: Float?,
    age: String? = null
): BitmapDrawable {
    val dp = context.resources.displayMetrics.density
    val pillH = 30f * dp
    val radius = pillH / 2
    val border = 2f * dp
    val shadow = 3f * dp

    val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = routeTextColor
        textSize = (if (label.length > 3) 13f else 15f) * dp
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    val hasBadge = heading != null
    val textW = if (label.isEmpty()) 0f else text.measureText(label)
    val badgeW = if (hasBadge) pillH else 0f
    val textPad = if (label.isEmpty()) 0f else 10f * dp
    val pillW = maxOf(pillH, badgeW + textW + textPad * (if (hasBadge) 1.2f else 2f))

    val ageText = age?.let {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 10f * dp
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }
    }
    val ageH = if (ageText != null) 16f * dp else 0f
    val ageGap = if (ageText != null) 2f * dp else 0f

    // Pill centred in the bitmap so anchoring at the centre puts it on the vehicle;
    // the age tag hangs below and is mirrored by empty space above
    val w = (pillW + shadow * 2).toInt() + 1
    val h = (pillH + shadow * 2 + (ageH + ageGap) * 2).toInt() + 1
    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val left = (w - pillW) / 2
    val top = (h - pillH) / 2
    val pill = RectF(left, top, left + pillW, top + pillH)

    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = routeColor
        setShadowLayer(shadow, 0f, dp, Color.argb(110, 0, 0, 0))
    }
    canvas.drawRoundRect(pill, radius, radius, fill)
    val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = border
        color = Color.WHITE
    }
    canvas.drawRoundRect(pill, radius, radius, stroke)

    if (heading != null) {
        val cx = left + radius
        val cy = top + radius
        canvas.drawCircle(cx, cy, radius - 3.5f * dp, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })
        drawArrow(canvas, cx, cy, heading, 18f * dp, Color.rgb(15, 18, 22))
    }

    if (label.isNotEmpty()) {
        val textCx = left + badgeW + (pillW - badgeW) / 2 - (if (hasBadge) 2f * dp else 0f)
        canvas.drawText(label, textCx, top + radius - (text.descent() + text.ascent()) / 2, text)
    }

    if (age != null && ageText != null) {
        val tagW = ageText.measureText(age) + 10f * dp
        val tagTop = top + pillH + ageGap
        val tag = RectF(w / 2f - tagW / 2, tagTop, w / 2f + tagW / 2, tagTop + ageH)
        canvas.drawRoundRect(tag, 8f * dp, 8f * dp, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(225, 20, 20, 20) })
        canvas.drawText(age, w / 2f, tag.centerY() - (ageText.descent() + ageText.ascent()) / 2, ageText)
    }

    return BitmapDrawable(context.resources, bitmap)
}

/** Shaft + head arrow centred on (cx, cy), pointing [heading] degrees clockwise from north. */
private fun drawArrow(canvas: Canvas, cx: Float, cy: Float, heading: Float, len: Float, color: Int) {
    canvas.save()
    canvas.rotate(heading, cx, cy)
    val shaft = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        strokeWidth = len * 0.2f
        strokeCap = Paint.Cap.ROUND
    }
    canvas.drawLine(cx, cy + len * 0.5f, cx, cy - len * 0.05f, shaft)
    val head = Path().apply {
        moveTo(cx, cy - len * 0.55f)
        lineTo(cx + len * 0.38f, cy - len * 0.05f)
        lineTo(cx - len * 0.38f, cy - len * 0.05f)
        close()
    }
    canvas.drawPath(head, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color })
    canvas.restore()
}
