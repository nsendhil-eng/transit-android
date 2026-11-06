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

    private fun fix(lat: Double = -27.4806, lon: Double = 152.9965, at: Long = savedAt) = UserFix(lat, lon, timeMs = at)

    @Test
    fun `a recent view from here can be used`() {
        val now = savedAt + 2 * 60_000L
        assertTrue(snapshot.usableAt(fix(at = now), now))
    }

    @Test
    fun `a view over 3 minutes old cannot`() {
        val now = savedAt + 3 * 60_000L
        assertFalse(snapshot.usableAt(fix(at = now), now))
    }

    @Test
    fun `a view saved in the future cannot, if the clock changed`() {
        assertFalse(snapshot.usableAt(fix(at = savedAt - 1), savedAt - 1))
    }

    @Test
    fun `nor can one from over 100 m away`() {
        assertTrue(snapshot.usableAt(fix(lat = -27.4806 + 0.0005), savedAt)) // ~56 m
        assertFalse(snapshot.usableAt(fix(lat = -27.4806 + 0.001), savedAt)) // ~111 m
    }

    @Test
    fun `nor can one checked against an old cached fix, which may be from before you moved`() {
        val now = savedAt + 60_000L
        assertFalse(snapshot.usableAt(fix(at = now - 2 * 60_000L), now))
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
