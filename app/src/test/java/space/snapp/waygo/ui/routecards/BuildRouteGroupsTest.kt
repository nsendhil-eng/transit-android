package space.snapp.waygo.ui.routecards

import org.junit.Assert.assertEquals
import org.junit.Test
import space.snapp.waygo.data.api.models.Departure
import space.snapp.waygo.data.api.models.Stop
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class BuildRouteGroupsTest {

    private val now = 1_800_000_000_000L
    private val iso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        .apply { timeZone = TimeZone.getTimeZone("UTC") }

    private fun stop(id: String, distance: Double, parent: String = "", routeType: Int = 2) = Stop(
        id = id, name = "Stop $id", stopCode = id, latitude = 0.0, longitude = 0.0,
        parentStation = parent, parentStationName = null, servicingRoutes = null,
        routeTypes = listOf(routeType), distanceMeters = distance
    )

    private fun dep(
        trip: String, stopId: String, minutes: Int, headsign: String,
        type: String = "Train", route: String = "CAIP", routeId: String = route, direction: Int = 0,
        platform: String? = null
    ) = Departure(
        tripId = trip, stopId = stopId, stopName = "Stop $stopId", vehicleType = type,
        routeNumber = route, routeId = routeId, headsign = headsign, directionId = direction,
        scheduledDepartureUtc = iso.format(now + minutes * 60_000L), expectedDepartureUtc = null,
        secondsUntilDeparture = minutes * 60, platformCode = platform,
        routeColor = null, routeTextColor = null, routeLongName = null
    )

    private fun minutesOf(card: DirectionCard) =
        card.departures.map { ((it.departureDate.time - now) / 60_000).toInt() }

    @Test
    fun `station platforms are merged so a train on a slightly farther platform is not hidden`() {
        // Real case at Auchenflower: Ipswich trains leave from platform 1 and platform 3,
        // and platform 3 is two metres closer
        val stops = listOf(
            stop("p1", 459.0, parent = "place_aucsta"),
            stop("p3", 457.0, parent = "place_aucsta")
        )
        val deps = listOf(
            dep("a", "p1", 9, "Ipswich station", platform = "1"),
            dep("b", "p3", 36, "Ipswich station", platform = "3"),
            dep("c", "p3", 79, "Ipswich station", route = "NAIP", platform = "3")
        )

        val card = buildRouteGroups(deps, stops, null, now).single().directions.single()

        assertEquals(listOf(9, 36, 79), minutesOf(card))
        assertEquals("1", card.platformCode)
        assertEquals(457.0, card.distanceMeters!!, 0.0)
    }

    @Test
    fun `bus uses its nearest stop even when an upstream stop sees the same bus first`() {
        val stops = listOf(stop("near", 192.0, routeType = 3), stop("upstream", 378.0, routeType = 3))
        val deps = listOf(
            dep("t1", "upstream", 8, "City", type = "Bus", route = "433", routeId = "433-1"),
            dep("t1", "near", 10, "City", type = "Bus", route = "433", routeId = "433-1")
        )

        val card = buildRouteGroups(deps, stops, null, now).single().directions.single()

        assertEquals(listOf(10), minutesOf(card))
        assertEquals("Stop near", card.stopName)
    }

    @Test
    fun `bus directions pair into one swipeable group, trains stay one group per destination`() {
        val stops = listOf(
            stop("b1", 100.0, routeType = 3), stop("b2", 110.0, routeType = 3),
            stop("p1", 400.0, parent = "st"), stop("p2", 401.0, parent = "st")
        )
        val deps = listOf(
            dep("x", "b1", 5, "City", type = "Bus", route = "412", routeId = "412-1", direction = 0),
            dep("y", "b2", 7, "St Lucia", type = "Bus", route = "412", routeId = "412-1", direction = 1),
            dep("z", "p1", 12, "Ipswich station"),
            dep("w", "p2", 15, "Kippa-Ring station", route = "SPRP")
        )

        val groups = buildRouteGroups(deps, stops, null, now)

        assertEquals(3, groups.size)
        assertEquals(2, groups.first { it.directions.first().routeNumber == "412" }.directions.size)
    }

    @Test
    fun `cards sort by soonest departure, regardless of walking time`() {
        // 400 m = 5 min walk: the 2-min train may not be reachable on foot, but the user could
        // be dropped off or the train could be late, so it still ranks first
        val stops = listOf(stop("far", 400.0, parent = "st"), stop("bus", 80.0, routeType = 3))
        val deps = listOf(
            dep("t1", "far", 2, "Ipswich station"),
            dep("t2", "far", 20, "Ipswich station"),
            dep("b1", "bus", 10, "City", type = "Bus", route = "412", routeId = "412-1")
        )

        val groups = buildRouteGroups(deps, stops, null, now)

        assertEquals("Ipswich station", groups.first().directions.first().headsign)
        assertEquals(listOf(2, 20), minutesOf(groups.first().directions.first()))
    }

    @Test
    fun `the soonest direction of a route is its front page`() {
        val stops = listOf(stop("b1", 150.0, routeType = 3), stop("b2", 160.0, routeType = 3))
        val deps = listOf(
            dep("m", "b1", 1, "Moggill", type = "Bus", route = "444", routeId = "444-1", direction = 1),
            dep("q", "b2", 14, "Queensland Theatre", type = "Bus", route = "444", routeId = "444-1", direction = 0)
        )

        val group = buildRouteGroups(deps, stops, null, now).single()

        assertEquals("Moggill", group.directions.first().headsign)
    }

    @Test
    fun `mode filter keeps only that mode`() {
        val stops = listOf(stop("p1", 400.0, parent = "st"), stop("bus", 80.0, routeType = 3))
        val deps = listOf(
            dep("t1", "p1", 5, "Ipswich station"),
            dep("b1", "bus", 3, "City", type = "Bus", route = "412", routeId = "412-1")
        )

        val groups = buildRouteGroups(deps, stops, space.snapp.waygo.data.api.models.VehicleType.Rail, now)

        assertEquals(listOf("Ipswich station"), groups.map { it.directions.first().headsign })
    }
}
