package space.snapp.waygo.data.api.models

/** /api/eta: a saved place's travel time from here, as its home screen chip shows it */
data class Eta(
    val minutes: Int,
    val leaveInMinutes: Int,
    val departAt: String,
    val arriveAt: String,
    val walkMinutes: Int,
    val transfers: Int,
    val walkOnly: Boolean,
    val firstLeg: EtaLeg?
)

data class EtaLeg(
    val mode: String,
    val route: String?,
    val headsign: String?,
    val color: String?,
    val textColor: String?,
    val stopName: String?,
    val departAt: String,
    val realTime: Boolean
)

data class EtaResponse(val eta: Eta? = null)
