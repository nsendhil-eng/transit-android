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
    val matchedStopIds: List<String> = emptyList()
) {
    val isUpcoming: Boolean get() = status == "upcoming"
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
