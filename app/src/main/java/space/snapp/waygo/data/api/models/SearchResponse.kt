package space.snapp.waygo.data.api.models

import space.snapp.waygo.data.api.models.Route
import space.snapp.waygo.data.api.models.Stop

data class SearchResponse(
    val stops: List<Stop> = emptyList(),
    val routes: List<Route> = emptyList(),
    val suburbs: List<SuburbResult> = emptyList()
)

data class SuburbResult(
    val suburb: String,
    val routes: List<Route> = emptyList()
)
