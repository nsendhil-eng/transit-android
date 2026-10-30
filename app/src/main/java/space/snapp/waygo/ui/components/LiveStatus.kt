package space.snapp.waygo.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke

// Status colours for live times, shared by line details and Near Me
val OnTimeGreen = Color(0xFF4CAF50)
val LateAmber = Color(0xFFFFA726)
val EarlyBlue = Color(0xFF64B5F6)

/** Pulsing "broadcast" arcs marking live GTFS-RT data. */
@Composable
fun LiveIndicator(color: Color, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "live")
    val alpha by transition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "liveAlpha"
    )
    Canvas(modifier = modifier) {
        val stroke = size.minDimension * 0.14f
        val origin = Offset(0f, size.height)
        drawCircle(color, radius = stroke * 0.9f, center = Offset(stroke, size.height - stroke))
        listOf(0.55f, 0.95f).forEach { r ->
            val radius = size.minDimension * r
            drawArc(
                color = color.copy(alpha = alpha),
                startAngle = -90f,
                sweepAngle = 90f,
                useCenter = false,
                topLeft = Offset(origin.x - radius, origin.y - radius),
                size = Size(radius * 2, radius * 2),
                style = Stroke(stroke, cap = StrokeCap.Round)
            )
        }
    }
}

/** "On time" / "3 min late" / "2 min early" / "Scheduled", with a status colour (null = neutral). */
fun delayLabel(isLive: Boolean, delaySeconds: Int?): Pair<String, Color?> {
    if (!isLive) return "Scheduled" to null
    val d = delaySeconds ?: 0
    return when {
        d >= 60 -> "${(d + 30) / 60} min late" to LateAmber
        d <= -60 -> "${(-d + 30) / 60} min early" to EarlyBlue
        else -> "On time" to OnTimeGreen
    }
}
