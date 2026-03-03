package space.snapp.waygo.ui.map

import space.snapp.waygo.data.api.models.Stop
import space.snapp.waygo.data.api.models.VehicleType

data class StopGroup(
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val stops: List<Stop>
) {
    val primaryType: VehicleType get() = stops.firstOrNull()?.primaryVehicleType ?: VehicleType.Bus
    val distanceLabel: String? get() = stops.firstOrNull()?.distanceLabel
    val isMultiPlatform: Boolean get() = stops.size > 1
}

fun groupStops(stops: List<Stop>): List<StopGroup> {
    val usable = stops.filter { it.stopCode != null }
    val grouped = mutableMapOf<String, MutableList<Stop>>()
    val standalone = mutableListOf<Stop>()

    for (stop in usable) {
        val parent = stop.parentStation?.takeIf { it.isNotEmpty() }
        if (parent != null) {
            grouped.getOrPut(parent) { mutableListOf() }.add(stop)
        } else {
            standalone.add(stop)
        }
    }

    val result = mutableListOf<StopGroup>()

    for ((parentId, groupStops) in grouped) {
        val name = groupStops.firstOrNull()?.parentStationName
            ?: groupStops.firstOrNull()?.name
            ?: parentId
        result.add(
            StopGroup(
                id = parentId,
                name = name,
                latitude = groupStops.map { it.latitude }.average(),
                longitude = groupStops.map { it.longitude }.average(),
                stops = groupStops
            )
        )
    }

    for (stop in standalone) {
        result.add(
            StopGroup(
                id = stop.id,
                name = stop.name,
                latitude = stop.latitude,
                longitude = stop.longitude,
                stops = listOf(stop)
            )
        )
    }

    return result.sortedBy { it.stops.firstOrNull()?.distanceMeters ?: Double.MAX_VALUE }
}
