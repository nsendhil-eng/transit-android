package space.snapp.waygo.data.models

data class FavoriteDestination(
    val stopIds: List<String>,
    val displayName: String,
    val fromPlaceName: String? = null  // set when resolved from a saved place
)
