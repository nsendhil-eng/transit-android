package space.snapp.waygo.ui.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import space.snapp.waygo.data.api.models.Departure
import space.snapp.waygo.data.api.models.Stop

class HomeSnapshotTest {
    private val stop = Stop("1951", "Coronation Dr at Wesley Hospital, stop 9", "001951", -27.4806, 152.9965,
        null, null, null, listOf(3), 60.0)
    private val dep = Departure(
        tripId = "T1", stopId = "1951", stopName = "", vehicleType = "Bus", routeNumber = "412",
        routeId = "412-5146", headsign = "City", directionId = 0,
        scheduledDepartureUtc = "2026-11-06T01:02:03.000Z", expectedDepartureUtc = "2026-11-06T01:04:03.000Z",
        secondsUntilDeparture = 60, platformCode = null, routeColor = "E85DA8", routeTextColor = null,
        routeLongName = null, isRealtime = true
    )
    private val savedAt = 1_000_000_000L
    private val snapshot = HomeSnapshot(savedAt, -27.4806, 152.9965, listOf(stop), listOf(dep))

    @Test
    fun `a recent view from here can be used`() {
        assertTrue(snapshot.usableAt(-27.4806, 152.9965, savedAt + 14 * 60_000L))
    }

    @Test
    fun `a view over 15 minutes old cannot`() {
        assertFalse(snapshot.usableAt(-27.4806, 152.9965, savedAt + 15 * 60_000L))
    }

    @Test
    fun `a view saved in the future cannot, if the clock changed`() {
        assertFalse(snapshot.usableAt(-27.4806, 152.9965, savedAt - 1))
    }

    @Test
    fun `nor can one from over 150 m away`() {
        assertTrue(snapshot.usableAt(-27.4806 + 0.001, 152.9965, savedAt)) // ~111 m
        assertFalse(snapshot.usableAt(-27.4806 + 0.002, 152.9965, savedAt)) // ~223 m
    }

    @Test
    fun `it survives being written and read back, times included`() {
        val back = HomeSnapshot.fromJson(snapshot.toJson())!!
        assertEquals(snapshot, back)
        assertEquals(dep.departureMs, back.departures.single().departureMs)
        assertEquals("001951", back.stops.single().stopCode)
    }

    @Test
    fun `a damaged file is ignored`() {
        assertNull(HomeSnapshot.fromJson("{not json"))
    }
}
