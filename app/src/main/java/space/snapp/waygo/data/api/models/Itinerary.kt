package space.snapp.waygo.data.api.models

data class ItineraryPlace(
    val name: String,
    val lat: Double,
    val lon: Double,
    val stopCode: String?
)

data class ItineraryStep(
    val distance: Int,
    val streetName: String,
    val relativeDirection: String,
    val absoluteDirection: String
)

data class ItineraryLeg(
    val mode: String,
    val startTime: Long,
    val endTime: Long,
    val duration: Int,
    val distance: Int,
    val from: ItineraryPlace,
    val to: ItineraryPlace,
    val routeShortName: String?,
    val routeLongName: String?,
    val routeColor: String?,
    val headsign: String?,
    val tripId: String?,
    val legGeometry: String?,
    val steps: List<ItineraryStep>
) {
    val isTransit: Boolean get() = mode in listOf("BUS", "RAIL", "TRAM", "FERRY", "SUBWAY", "TRANSIT")

    /** The leg's badge: route number, except trains, whose short names are internal codes ("CAIP") */
    val label: String
        get() = if (mode == "RAIL") headsign?.removeSuffix(" station")?.removeSuffix(" Station") ?: "Train"
            else routeShortName ?: mode
    val durationMins: Int get() = duration / 60
}

data class Itinerary(
    val duration: Int,
    val startTime: Long,
    val endTime: Long,
    val walkDistance: Int,
    val transfers: Int,
    val legs: List<ItineraryLeg>
) {
    val durationMins: Int get() = duration / 60
    val transitLegs: List<ItineraryLeg> get() = legs.filter { it.isTransit }
}

/**
 * "Arrive first": soonest arrival; trips arriving in the same minute go fewest changes first,
 * then leaving later (a bus hop and a change that only gets you to the same 412 isn't better)
 */
val ArriveFirstOrder: Comparator<Itinerary> =
    compareBy<Itinerary>({ it.endTime / 60_000 }, { it.transfers }, { -it.startTime })

data class PlanResponse(
    val itineraries: List<Itinerary>
)
