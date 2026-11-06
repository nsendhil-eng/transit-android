package space.snapp.waygo

import android.app.Application
import org.osmdroid.config.Configuration
import space.snapp.waygo.data.AppVisibility
import space.snapp.waygo.data.api.TransitApiService

/** Downloads at once per map layer (osmdroid's default is 2) */
private const val TILE_DOWNLOAD_THREADS: Short = 6
/** Tiles kept in memory per layer (default 9) */
private const val TILES_IN_MEMORY: Short = 32
private const val TILES_EXTRA_OFF_SCREEN: Short = 12
/** How long a downloaded tile stays good. Esri says a day, but the base map rarely changes. */
private const val TILE_KEEP_MS = 30L * 24 * 60 * 60 * 1000

class WayGoApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppVisibility.track(this)
        // Before any map exists: maps read these when they're created
        Configuration.getInstance().apply {
            userAgentValue = packageName
            tileDownloadThreads = TILE_DOWNLOAD_THREADS
            cacheMapTileCount = TILES_IN_MEMORY
            cacheMapTileOvershoot = TILES_EXTRA_OFF_SCREEN
            expirationOverrideDuration = TILE_KEEP_MS
        }
        // Built off the main thread, so the first screen doesn't wait for it
        Thread { TransitApiService.instance }.start()
    }
}
