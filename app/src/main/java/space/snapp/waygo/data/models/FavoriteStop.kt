package space.snapp.waygo.data.models

import space.snapp.waygo.data.api.models.Stop
import java.util.UUID

data class FavoriteStop(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val stops: List<Stop>,
    val getOffStopId: String? = null,   // comma-separated for multi-platform
    val getOffStopName: String? = null
)
