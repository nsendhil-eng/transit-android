package space.snapp.waygo.data

import android.app.Activity
import android.app.Application
import android.os.Bundle
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest

/**
 * Whether any of the app's screens is visible. Refresh loops wait on this, so nothing is
 * fetched while the app sits in the background (a left-open stop sheet used to call the API
 * over a thousand times an hour from a pocket).
 */
object AppVisibility {
    private val _visible = MutableStateFlow(false)
    val visible: StateFlow<Boolean> = _visible

    /** Counts started activities; registered once from WayGoApplication */
    fun track(app: Application) {
        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            private var started = 0
            override fun onActivityStarted(activity: Activity) { started++; _visible.value = true }
            override fun onActivityStopped(activity: Activity) { started--; _visible.value = started > 0 }
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }
}

/**
 * Runs [block] every [periodMs] while the app is visible. Stops when it goes to the
 * background (cancelling a fetch in flight) and runs again straight away on return.
 */
suspend fun pollWhileVisible(periodMs: Long, block: suspend () -> Unit) {
    AppVisibility.visible.collectLatest { visible ->
        if (!visible) return@collectLatest
        while (true) {
            block()
            delay(periodMs)
        }
    }
}
