package space.snapp.waygo.ui.map

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Point
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Surface
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay
import kotlin.math.abs

private const val YOU_BLUE = 0xFF3D8BFF.toInt()
/** Half the width of the heading cone, degrees */
private const val CONE_HALF_DEG = 32f

/**
 * "You are here": a blue dot with a white ring, a translucent circle for the GPS accuracy, and a
 * cone showing which way the phone faces. Update [position], [accuracyM] and [heading], then
 * invalidate the map.
 */
class UserLocationOverlay(context: Context) : Overlay() {
    var position: GeoPoint? = null
    var accuracyM: Float? = null
    /** Compass heading in degrees (0 = north), or null when unknown */
    var heading: Float? = null

    private val dp = context.resources.displayMetrics.density
    private val p = Point()
    private val halo = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = YOU_BLUE; alpha = 34 }
    private val haloEdge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = YOU_BLUE; alpha = 90; style = Paint.Style.STROKE; strokeWidth = 1f * dp
    }
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = YOU_BLUE }
    private val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(90, 0, 0, 0) }
    private val cone = Paint(Paint.ANTI_ALIAS_FLAG)
    private val coneR = 46f * dp
    private val conePath = Path()
    private val oval = RectF()

    override fun draw(canvas: Canvas, mapView: MapView, shadowPass: Boolean) {
        if (shadowPass) return
        val pos = position ?: return
        val proj = mapView.projection
        proj.toPixels(pos, p)
        val x = p.x.toFloat()
        val y = p.y.toFloat()

        // Accuracy circle — only when it's bigger than the dot itself
        accuracyM?.let { acc ->
            val r = proj.metersToPixels(acc, pos.latitude, mapView.zoomLevelDouble)
            if (r > 10f * dp) {
                canvas.drawCircle(x, y, r, halo)
                canvas.drawCircle(x, y, r, haloEdge)
            }
        }

        heading?.let { h ->
            // Screen angles: 0° = east, clockwise. Map is north-up, so north = -90°.
            val start = h - 90f - CONE_HALF_DEG - mapView.mapOrientation
            oval.set(x - coneR, y - coneR, x + coneR, y + coneR)
            conePath.reset()
            conePath.moveTo(x, y)
            conePath.arcTo(oval, start, CONE_HALF_DEG * 2)
            conePath.close()
            cone.shader = RadialGradient(
                x, y, coneR,
                intArrayOf(Color.argb(150, 61, 139, 255), Color.argb(0, 61, 139, 255)),
                null, Shader.TileMode.CLAMP
            )
            canvas.drawPath(conePath, cone)
        }

        canvas.drawCircle(x, y + 1f * dp, 9.5f * dp, shadow)
        canvas.drawCircle(x, y, 9f * dp, ring)
        canvas.drawCircle(x, y, 6.5f * dp, dot)
    }
}

/**
 * Compass heading (degrees from north) from the rotation-vector sensor, smoothed and only
 * updated when it moves by a few degrees so the map isn't redrawn on every sensor tick.
 * Null if the phone has no such sensor.
 */
@Composable
fun rememberCompassHeading(): State<Float?> {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val heading = remember { mutableStateOf<Float?>(null) }
    DisposableEffect(lifecycleOwner) {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
            ?: return@DisposableEffect onDispose {}
        @Suppress("DEPRECATION")
        val display = (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay
        val rot = FloatArray(9)
        val remapped = FloatArray(9)
        val orient = FloatArray(3)
        var smooth: Float? = null
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(rot, e.values)
                val (ax, ay) = when (display.rotation) {
                    Surface.ROTATION_90 -> SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X
                    Surface.ROTATION_180 -> SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y
                    Surface.ROTATION_270 -> SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X
                    else -> SensorManager.AXIS_X to SensorManager.AXIS_Y
                }
                SensorManager.remapCoordinateSystem(rot, ax, ay, remapped)
                SensorManager.getOrientation(remapped, orient)
                val raw = (Math.toDegrees(orient[0].toDouble()).toFloat() + 360f) % 360f
                // Low-pass along the shortest way round the circle
                val s = smooth?.let { prev -> (prev + 0.2f * angleDiff(raw, prev) + 360f) % 360f } ?: raw
                smooth = s
                val shown = heading.value
                if (shown == null || abs(angleDiff(s, shown)) >= 3f) heading.value = s
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        // Only listen while the app is in front
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
                Lifecycle.Event.ON_PAUSE -> sm.unregisterListener(listener)
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            sm.unregisterListener(listener)
        }
    }
    return heading
}

/** Signed difference a - b in degrees, in -180..180 */
private fun angleDiff(a: Float, b: Float): Float = ((a - b + 540f) % 360f) - 180f
