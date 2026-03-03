package space.snapp.waygo.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector
import space.snapp.waygo.data.api.models.VehicleType

fun VehicleType.icon(): ImageVector = when (this) {
    VehicleType.Rail   -> Icons.Default.Train
    VehicleType.Tram   -> Icons.Default.Tram
    VehicleType.Ferry  -> Icons.Default.DirectionsBoat
    VehicleType.Bus    -> Icons.Default.DirectionsBus
}
