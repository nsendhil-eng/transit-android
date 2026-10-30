package space.snapp.waygo.ui.map

import org.junit.Assert.assertEquals
import org.junit.Test
import space.snapp.waygo.data.api.models.Stop

class GroupStopsTest {

    private fun stop(id: String, code: String?, parent: String = "", distance: Double = 100.0, types: List<Int>? = listOf(3)) = Stop(
        id = id, name = "Stop $id", stopCode = code, latitude = 0.0, longitude = 0.0,
        parentStation = parent, parentStationName = null, servicingRoutes = null,
        routeTypes = types, distanceMeters = distance
    )

    @Test
    fun `parent-station record with blank stop code does not become its own marker`() {
        // Real API data at Regatta: the ferry stop plus its parent "place" record (stop_code "")
        val groups = groupStops(listOf(
            stop("317571", "317571", parent = "place_regaft", distance = 346.0, types = listOf(4)),
            stop("place_regaft", "", distance = 346.0, types = null)
        ))

        assertEquals(1, groups.size)
        assertEquals(listOf("317571"), groups.single().stops.map { it.id })
    }

    @Test
    fun `station platforms group under their parent`() {
        val groups = groupStops(listOf(
            stop("600283", "600283", parent = "place_aucsta", types = listOf(2)),
            stop("600284", "600284", parent = "place_aucsta", types = listOf(2)),
            stop("1949", "001949")
        ))

        assertEquals(2, groups.size)
        assertEquals(2, groups.first { it.id == "place_aucsta" }.stops.size)
    }
}
