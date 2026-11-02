package space.snapp.waygo

import android.app.Application
import space.snapp.waygo.data.AppVisibility

class WayGoApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppVisibility.track(this)
    }
}
