package space.snapp.waygo.data.api.models

data class RouteShape(
    val routeId: String,
    val routeColor: String?,
    val points: List<List<Double>>  // [[lat, lon], ...]
)
