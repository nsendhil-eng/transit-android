package space.snapp.waygo.data.models

import space.snapp.waygo.data.api.models.Stop
import java.util.UUID

data class FavoriteStop(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val stops: List<Stop>,
    val destinations: List<FavoriteDestination> = emptyList(),
    // Legacy fields — kept for Gson backward compat with existing saved data
    val getOffStopId: String? = null,
    val getOffStopName: String? = null
) {
    /** All destination stop IDs as a comma-separated string for the departures API */
    val effectiveGetOffStopId: String? get() {
        if (destinations.isNotEmpty()) {
            val ids = destinations.flatMap { it.stopIds }.filter { it.isNotEmpty() }
            return ids.joinToString(",").takeIf { it.isNotEmpty() }
        }
        return getOffStopId
    }

    /** Human-readable destination label for display on cards */
    val effectiveDestinationLabel: String? get() {
        if (destinations.isNotEmpty()) return destinations.joinToString(" · ") { it.displayName }
        return getOffStopName
    }
}
