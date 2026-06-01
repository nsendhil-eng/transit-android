package space.snapp.waygo.data.models

import java.util.UUID

data class FavoritePlace(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val displayName: String,
    val lat: Double,
    val lon: Double
)
