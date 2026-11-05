package space.snapp.waygo.ui.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import space.snapp.waygo.data.api.models.Departure
import space.snapp.waygo.data.api.models.Stop
import space.snapp.waygo.data.api.models.VehicleType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class NearbyRoutesTest {

    // User at the Wesley: bus stops either side of Coronation Dr, Auchenflower station ~700 m
    private val userLat = -27.4802
    private val userLon = 152.9960

    private fun stop(id: String, name: String, lat: Double, lon: Double, dist: Double,
                     parent: String = "", parentName: String? = null, type: Int = 3) = Stop(
        id = id, name = name, stopCode = "00$id", latitude = lat, longitude = lon,
        parentStation = parent, parentStationName = parentName,
        servicingRoutes = null, routeTypes = listOf(type), distanceMeters = dist
    )

    private val iso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }

    private fun dep(stopId: String, route: String, dir: Int, inMin: Int, type: String = "Bus", headsign: String = "To $route") = Departure(
        tripId = "$route-$inMin-$stopId", stopId = stopId, stopName = "", vehicleType = type,
        routeNumber = route, routeId = "$route-5146", headsign = headsign, directionId = dir,
        scheduledDepartureUtc = iso.format(Date(System.currentTimeMillis() + inMin * 60_000L + 30_000L)),
        expectedDepartureUtc = null, secondsUntilDeparture = inMin * 60, platformCode = null,
        routeColor = null, routeTextColor = null, routeLongName = null
    )

    private val stop9 = stop("1951", "Coronation Dr at Wesley Hospital, stop 9", -27.4806, 152.9965, 60.0)
    private val stop10 = stop("1944", "Coronation Dr at Wesley Hospital, stop 10", -27.4809, 152.9962, 80.0)
    private val milton = stop("1882", "Milton Rd at Croydon St, stop 12", -27.4750, 152.9900, 420.0)
    private val platform = stop("600284", "Auchenflower station, platform 2", -27.4762, 152.9978, 700.0,
        parent = "place_aucsta", parentName = "Auchenflower station", type = 2)
    private val groups = groupStops(listOf(stop9, stop10, milton, platform))

    @Test
    fun `every route gets a row at its own stop, so the bus across the road is listed`() {
        val rows = buildNearbyRoutes(groups, listOf(
            dep("1951", "412", 0, 4),      // city-bound, this side
            dep("1944", "412", 1, 1),      // outbound, the stop across the road: the bus you can see coming
            dep("1944", "444", 1, 6)
        ))
        assertEquals(listOf("412|0" to "1951", "412|1" to "1944", "444|1" to "1944"),
            rows.map { it.first.routeNumber + "|" + it.first.directionId to it.first.stopId })
        assertEquals("Wesley Hospital · stop 10", rows[1].stopLabel)
    }

    @Test
    fun `a train ten minutes away is listed beside the buses at the door`() {
        val rows = buildNearbyRoutes(groups, listOf(
            dep("1951", "412", 0, 4),
            dep("600284", "IPNA", 0, 6, type = "Train", headsign = "Nambour station")
        ))
        val train = rows.single { it.mode == VehicleType.Rail }
        assertEquals("Auchenflower station", train.stopLabel)
        assertEquals(12, train.walkMinutes)   // 700 m x 1.3 at 80 m a minute
    }

    @Test
    fun `rows keep their order as times change, by distance then mode and route number`() {
        val a = buildNearbyRoutes(groups, listOf(dep("1882", "375", 0, 1), dep("1951", "444", 0, 9), dep("1951", "412", 0, 7)))
        val b = buildNearbyRoutes(groups, listOf(dep("1882", "375", 0, 8), dep("1951", "444", 0, 1), dep("1951", "412", 0, 3)))
        assertEquals(listOf("412", "444", "375"), a.map { it.first.routeNumber })
        assertEquals(a.map { it.key }, b.map { it.key })
    }

    @Test
    fun `next two times per row, soonest first, and services ending here are left out`() {
        val rows = buildNearbyRoutes(groups, listOf(
            dep("1951", "412", 0, 9), dep("1951", "412", 0, 2), dep("1951", "412", 0, 20),
            dep("1951", "450", 0, 3).copy(isLastStop = true),
            dep("1951", "460", 0, 4).copy(isCancelled = true)
        ))
        assertEquals(listOf("412"), rows.map { it.first.routeNumber })
        assertEquals(2, rows.single().departures.size)
        assertTrue(rows.single().departures[0].liveSeconds < rows.single().departures[1].liveSeconds)
    }

    @Test
    fun `a busway platform is a bus station, at least a few minutes from the street`() {
        val kgs = stop("10781", "King George Square, 1a", -27.4685, 153.0236, 5.0,
            parent = "place_kgbs", parentName = "King George Square bus station")
        val row = buildNearbyRoutes(groupStops(listOf(kgs)), listOf(dep("10781", "333", 0, 2).copy(platformCode = "1a"))).single()
        assertTrue(row.isBusStation)
        assertEquals("King George Square bus station · platform 1a", row.stopLabel)
        assertEquals(3, row.walkMinutes)
        assertFalse(buildNearbyRoutes(groups, listOf(dep("1951", "412", 0, 2))).single().isBusStation)
    }

    @Test
    fun `resting rows pick the route leaving soonest among those about as close as the nearest`() {
        // Chermside-style interchange: platform B is nearest but its 338 leaves in 40 min; the 333
        // goes in 1 min from platform A, 30 m further. Milton's 375 is 360 m further still.
        val platB = stop("1", "Chermside bus station, platform B", -27.3855, 153.0311, 20.0,
            parent = "place_chmbus", parentName = "Chermside bus station")
        val platA = stop("2", "Chermside bus station, platform A", -27.3856, 153.0312, 50.0,
            parent = "place_chmbus", parentName = "Chermside bus station")
        val far = stop("3", "Gympie Rd at Kittyhawk Dr, stop 12", -27.3880, 153.0330, 380.0)
        val routes = buildNearbyRoutes(groupStops(listOf(platB, platA, far)), listOf(
            dep("1", "338", 0, 40), dep("2", "333", 0, 1), dep("3", "375", 0, 0)
        ))
        assertEquals(listOf("338", "333", "375"), routes.map { it.first.routeNumber })   // full list: by distance
        assertEquals(listOf("333"), peekRoutes(routes).map { it.first.routeNumber })     // resting row: soonest close by
    }

    @Test
    fun `a lettered platform in a station is a bus station even without busway in its name`() {
        val cc = stop("1832", "Cultural Centre, platform 1", -27.4730, 153.0205, 120.0,
            parent = "place_cultbs", parentName = "Cultural Centre station")
        val row = buildNearbyRoutes(groupStops(listOf(cc)), listOf(dep("1832", "60", 0, 9).copy(platformCode = "1"))).single()
        assertTrue(row.isBusStation)
        assertEquals("Cultural Centre station · platform 1", row.stopLabel)
    }

    @Test
    fun `full list is by stop, nearest first, and soonest first within a stop`() {
        val routes = buildNearbyRoutes(groups, listOf(
            dep("1951", "411", 0, 31), dep("1951", "412", 0, 3), dep("1951", "425", 0, 13),   // stop 9
            dep("1944", "412", 1, 8),                                                         // stop 10, across the road
            dep("1882", "375", 0, 1)                                                          // Milton, further
        ))
        val sections = stopSections(routes)
        assertEquals(listOf("1951", "1944", "1882"), sections.map { it.first.first.stopId })
        // 411 used to sit above 412 on route number alone; within its stop it now goes by time
        assertEquals(listOf("412", "425", "411"), sections[0].routes.map { it.first.routeNumber })
        assertEquals("Wesley Hospital · stop 9", sections[0].first.placeLabel)
        // A stop whose only route is also a resting row (Regatta's F1) still has its section
        val ferry = stop("317571", "Regatta ferry terminal", -27.4830, 152.9990, 350.0, type = 4)
        val withFerry = buildNearbyRoutes(groupStops(listOf(stop9, ferry)), listOf(dep("1951", "412", 0, 3), dep("317571", "F1", 0, 6, type = "Ferry")))
        assertEquals(listOf("412", "F1"), stopSections(withFerry).map { it.first.first.routeNumber })
    }

    @Test
    fun `two stops sharing a name are told apart by which way their buses go`() {
        // Translink names both sides of Coronation Dr "Wesley Hospital, stop 9"
        val city = stop("1951", "Coronation Dr at Wesley Hospital, stop 9", -27.4806, 152.9965, 198.0)
        val out = stop("1944", "Coronation Dr at Wesley Hospital, stop 9", -27.4809, 152.9962, 213.0)
        val routes = buildNearbyRoutes(groupStops(listOf(city, out)), listOf(
            dep("1951", "412", 0, 8, headsign = "City, Roma St").copy(heading = 40),
            dep("1951", "425", 0, 4, headsign = "City, Adelaide St").copy(heading = 40),
            dep("1944", "412", 1, 4, headsign = "St Lucia South").copy(heading = 225),
            dep("1944", "444", 1, 12, headsign = "Moggill").copy(heading = 220)
        ))
        assertEquals(listOf("city-bound", "outbound"), stopSections(routes).map { it.direction })
        // Without a city-bound side, the compass says which way
        assertEquals("heading SW", directionHint(routes.filter { it.first.stopId == "1944" }))
        // A stop with a name of its own gets no hint
        assertEquals(null, stopSections(buildNearbyRoutes(groups, listOf(dep("1882", "375", 0, 1)))).single().direction)
    }

    @Test
    fun `stop numbers tell the two sides of a road apart`() {
        assertEquals("9", stopNumber("Coronation Dr at Wesley Hospital, stop 9"))
        assertEquals("132", stopNumber("Wickham Terrace Stop 132 near Albert St"))
        assertEquals(null, stopNumber("Regatta ferry terminal"))
    }

    @Test
    fun `zoomed out, crowded stops thin out but kept stops always stay`() {
        val miltonId = groups.single { g -> g.stops.any { it.id == milton.id } }.id
        val shown = declutterStops(groups, keep = setOf(miltonId), userLat = userLat, userLon = userLon, metersApart = 80.0)
            .flatMap { g -> g.stops.map { it.id } }
        // Stop 10 sits ~40 m from stop 9, which is nearer you, so it goes
        assertEquals(setOf(milton.id, stop9.id, platform.id), shown.toSet())
    }

    @Test
    fun `trains are labelled by destination, buses by route number`() {
        assertEquals("Springfield Central", dep("600284", "IPCA", 0, 5, type = "Train", headsign = "Springfield Central station").pillLabel)
        assertEquals("412", dep("1951", "412", 0, 4).pillLabel)
    }

    @Test
    fun `time labels never repeat the delay`() {
        val soon = dep("1951", "412", 0, 6).copy(isRealtime = true, delaySeconds = 300)
        assertEquals("6 min", departureTimeLabel(soon))
        assertTrue(soon.isLate)
        assertEquals("Now", departureTimeLabel(dep("1951", "412", 0, 0)))
    }

    @Test
    fun `only South East Queensland counts as in range`() {
        assertTrue(inSeq(-27.47, 153.02))      // Brisbane
        assertTrue(inSeq(-28.0, 153.4))        // Gold Coast
        assertFalse(inSeq(-33.87, 151.21))     // Sydney
    }
}
