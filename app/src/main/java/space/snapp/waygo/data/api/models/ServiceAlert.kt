package space.snapp.waygo.data.api.models

import java.util.Date

/** A Translink service alert relevant to the routes/stops a screen asked about (see /api/alerts). */
data class ServiceAlert(
    val id: String,
    val header: String,
    val description: String,
    val url: String?,
    val cause: String,
    val effect: String,
    /** disruption | change | stopMoved | accessibility | extra | info */
    val kind: String,
    /** active | upcoming */
    val status: String,
    val priority: Int,
    val longRunning: Boolean,
    val activeFrom: String?,
    val activeUntil: String?,
    val matchedRouteIds: List<String> = emptyList(),
    val matchedStopIds: List<String> = emptyList(),
    // From /api/alerts/all: distance to the nearest affected stop, and a few route codes
    val distanceM: Int? = null,
    val nearestStopName: String? = null,
    val routeCodes: List<String> = emptyList(),
    val routeCount: Int? = null,
    /** Rail closures: the stations it covers, in line order ("Ipswich", "Thomas Street", …), when known */
    val affectedStations: List<String>? = null
) {
    val isUpcoming: Boolean get() = status == "upcoming"

    /** "Stations: Ipswich, Thomas Street +5 more", or null; short enough for one line */
    val stationsSummary: String?
        get() = affectedStations?.takeIf { it.isNotEmpty() }?.let { s ->
            if (s.size <= 2) "Stations: " + s.joinToString(", ")
            else "Stations: " + s.take(2).joinToString(", ") + " +${s.size - 2} more"
        }
    val fromDate: Date? get() = parseIsoUtc(activeFrom)
    val untilDate: Date? get() = parseIsoUtc(activeUntil)

    /** Worth flagging on a route card: services are actually reduced or delayed now. */
    val disruptsService: Boolean get() = kind == "disruption" && !isUpcoming && !longRunning
}

data class AlertsResponse(val alerts: List<ServiceAlert>)

private fun parseIsoUtc(iso: String?): Date? = iso?.let {
    runCatching {
        java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US)
            .apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
            .parse(it)
    }.getOrNull()
}
