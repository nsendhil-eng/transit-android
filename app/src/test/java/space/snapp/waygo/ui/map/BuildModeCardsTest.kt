package space.snapp.waygo.ui.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import space.snapp.waygo.data.api.models.Departure
import space.snapp.waygo.data.api.models.Stop
import space.snapp.waygo.data.api.models.VehicleType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class BuildModeCardsTest {

    // User at the Wesley: bus stop ~60 m, Auchenflower station ~450 m, Regatta ferry ~350 m
    private val userLat = -27.4802
    private val userLon = 152.9960

    private fun stop(id: String, name: String, lat: Double, lon: Double, parent: String = "", type: Int = 3) = Stop(
        id = id, name = name, stopCode = "00$id", latitude = lat, longitude = lon,
        parentStation = parent, parentStationName = if (parent.isNotEmpty()) "Auchenflower station" else null,
        servicingRoutes = null, routeTypes = listOf(type), distanceMeters = null
    )

    private val iso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }

    private fun dep(stopId: String, route: String, dir: Int, inMin: Int, type: String = "Bus", cancelled: Boolean = false) = Departure(
        tripId = "$route-$inMin-$stopId", stopId = stopId, stopName = "", vehicleType = type,
        routeNumber = route, routeId = "$route-5146", headsign = "To $route", directionId = dir,
        scheduledDepartureUtc = iso.format(Date(System.currentTimeMillis() + inMin * 60_000L + 30_000L)),
        expectedDepartureUtc = null, secondsUntilDeparture = inMin * 60, platformCode = null,
        routeColor = null, routeTextColor = null, routeLongName = null, isCancelled = cancelled
    )

    private val wesley = stop("1951", "Coronation Dr at Wesley Hospital, stop 9", -27.4806, 152.9965)
    private val milton = stop("1882", "Milton Rd at Croydon St, stop 12", -27.4750, 152.9900)
    private val platform = stop("600284", "Auchenflower station, platform 2", -27.4762, 152.9978, parent = "place_aucsta", type = 2)
    private val bay = stop("320480", "Auchenflower station, stop A", -27.4763, 152.9977, parent = "place_aucsta")
    private val regatta = stop("317571", "Regatta ferry terminal", -27.4830, 152.9990, type = 4)
    private val groups = groupStops(listOf(wesley, milton, platform, bay, regatta))

    @Test
    fun `one card per mode, train then bus then ferry, each at its nearest stop`() {
        val cards = buildModeCards(groups, listOf(
            dep("1882", "375", 0, 1),                       // sooner, but Wesley is nearer
            dep("1951", "412", 0, 4),
            dep("600284", "SPRP", 0, 6, type = "Train"),
            dep("317571", "F1", 0, 3, type = "Ferry")
        ), userLat, userLon)

        assertEquals(listOf(VehicleType.Rail, VehicleType.Bus, VehicleType.Ferry), cards.map { it.mode })
        assertEquals(listOf("Auchenflower station", "Wesley Hospital", "Regatta ferry terminal"), cards.map { it.name })
        assertEquals(listOf("412"), cards[1].departures.map { it.routeNumber })
    }

    @Test
    fun `a replacement bus at a station counts as a bus, and the train card disappears when no trains run`() {
        val cards = buildModeCards(groups, listOf(
            dep("320480", "R599", 0, 5),
            dep("600284", "SPRP", 0, 6, type = "Train", cancelled = true)
        ), userLat, userLon)
        assertEquals(listOf(VehicleType.Bus), cards.map { it.mode })
        assertEquals("Auchenflower station", cards.single().name)
    }

    @Test
    fun `each route and direction once, soonest first, at most three rows`() {
        val card = buildModeCards(groups, listOf(
            dep("1951", "412", 0, 9),
            dep("1951", "412", 0, 1),
            dep("1951", "412", 1, 3),
            dep("1951", "444", 0, 6),
            dep("1951", "425", 0, 12)
        ), userLat, userLon).single()
        assertEquals(listOf("412|0", "412|1", "444|0"), card.departures.map { "${it.routeNumber}|${it.directionId}" })
    }

    // The stop across the road from Wesley stop 9, same name, ~40 m away
    private val wesleyOpposite = stop("1944", "Coronation Dr at Wesley Hospital, stop 10", -27.4809, 152.9962)

    @Test
    fun `both sides of the road share one card, nearer side first, two rows each`() {
        val pairGroups = groupStops(listOf(wesley, wesleyOpposite, milton))
        val card = buildModeCards(pairGroups, listOf(
            dep("1951", "412", 0, 4).copy(heading = 40),
            dep("1951", "444", 0, 9).copy(heading = 40),
            dep("1951", "450", 0, 12).copy(heading = 40),     // third on this side: dropped
            dep("1944", "412", 1, 1).copy(heading = 220),
            dep("1944", "425", 1, 3).copy(heading = 222),
            dep("1882", "375", 0, 2)                          // different name: not paired
        ), userLat, userLon).single()

        assertEquals(listOf("1951", "1944"), card.sides.map { it.group.id })
        assertEquals(listOf("412", "444"), card.sides[0].departures.map { it.routeNumber })
        assertEquals(listOf("412", "425"), card.sides[1].departures.map { it.routeNumber })
        assertTrue(card.isAcrossTheRoad)
        assertEquals(setOf("1951", "1944"), card.asOneGroup().stops.map { it.id }.toSet())
    }

    @Test
    fun `a lone stop keeps three rows and is not across the road`() {
        val card = buildModeCards(groups, listOf(
            dep("1951", "412", 0, 1), dep("1951", "444", 0, 2), dep("1951", "450", 0, 3)
        ), userLat, userLon).single()
        assertEquals(1, card.sides.size)
        assertEquals(3, card.departures.size)
        assertEquals(false, card.isAcrossTheRoad)
    }

    @Test
    fun `nothing running means no cards`() {
        assertTrue(buildModeCards(groups, listOf(dep("1951", "412", 0, -5)), userLat, userLon).isEmpty())
    }

    @Test
    fun `trains are labelled by destination, buses by route number`() {
        val train = dep("600284", "IPCA", 0, 5, type = "Train").copy(headsign = "Springfield Central station")
        assertEquals("Springfield Central", train.pillLabel)
        assertEquals("412", dep("1951", "412", 0, 4).pillLabel)
    }

    @Test
    fun `time labels never repeat the delay`() {
        val soon = dep("1951", "412", 0, 6).copy(isRealtime = true, delaySeconds = 300)
        assertEquals("6 min", departureTimeLabel(soon))
        assertTrue(soon.isLate)
        assertEquals("Now", departureTimeLabel(dep("1951", "412", 0, 0)))
    }
}
