package space.snapp.waygo.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.alertsDataStore by preferencesDataStore(name = "alerts")

// Alerts with no end time stay dismissed this long, then may show again
private const val OPEN_ENDED_DISMISS_MS = 30L * 24 * 3600 * 1000

/**
 * Alerts the user dismissed, each hidden until that alert ends.
 * Stored as "id|expiresAtMillis"; expired entries are dropped on the next write.
 */
class AlertDismissals(private val context: Context) {
    private val key = stringSetPreferencesKey("dismissed_alerts_v1")

    val dismissedIds: Flow<Set<String>> = context.alertsDataStore.data.map { prefs ->
        val now = System.currentTimeMillis()
        prefs[key].orEmpty().mapNotNull { entry ->
            val id = entry.substringBeforeLast('|')
            val until = entry.substringAfterLast('|').toLongOrNull() ?: return@mapNotNull null
            id.takeIf { until > now }
        }.toSet()
    }

    suspend fun dismiss(id: String, untilMillis: Long?) {
        val now = System.currentTimeMillis()
        val until = untilMillis?.takeIf { it > now } ?: (now + OPEN_ENDED_DISMISS_MS)
        context.alertsDataStore.edit { prefs ->
            val kept = prefs[key].orEmpty().filter { entry ->
                val expiry = entry.substringAfterLast('|').toLongOrNull() ?: 0
                expiry > now && entry.substringBeforeLast('|') != id
            }
            prefs[key] = (kept + "$id|$until").toSet()
        }
    }
}
