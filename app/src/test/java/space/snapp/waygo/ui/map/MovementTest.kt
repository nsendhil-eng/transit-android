package space.snapp.waygo.ui.map

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MovementTest {
    private val lat = -27.4806
    private val lon = 152.9965
    private val now = 1_000_000_000L
    /** Degrees of latitude for a distance in metres */
    private fun north(m: Double) = lat + m / 111_320.0
    private fun fix(m: Double, accuracy: Float? = 10f, speed: Float? = 1.3f, at: Long = now) =
        UserFix(north(m), lon, accuracy, speed, at)
    private fun refetch(fix: UserFix, lastFetchMs: Long = now - 120_000, held: Boolean = false) =
        shouldRefetchStops(lat, lon, fix, lastFetchMs, now, held)

    @Test
    fun `the first fix always fetches, however vague`() {
        assertTrue(shouldRefetchStops(null, null, fix(0.0, accuracy = 500f), 0, now, held = true))
    }

    @Test
    fun `under 100 m changes nothing`() {
        assertFalse(refetch(fix(95.0)))
        assertTrue(refetch(fix(105.0)))
    }

    @Test
    fun `a vague fix moves nothing, and drift inside its error isn't a move`() {
        assertFalse(refetch(fix(300.0, accuracy = 60f)))
        assertFalse(UserFix(lat, lon, 60f, timeMs = now).accurate)
        assertTrue(refetch(fix(120.0, accuracy = 50f)))
    }

    @Test
    fun `on foot stops refresh at most every 30 s`() {
        assertFalse(refetch(fix(150.0), lastFetchMs = now - 29_000))
        assertTrue(refetch(fix(150.0), lastFetchMs = now - 30_000))
    }

    @Test
    fun `at vehicle speed, every 60 s`() {
        assertFalse(refetch(fix(150.0, speed = 12f), lastFetchMs = now - 59_000))
        assertTrue(refetch(fix(150.0, speed = 12f), lastFetchMs = now - 60_000))
    }

    @Test
    fun `the list holds still while in use, until you've really gone somewhere`() {
        assertFalse(refetch(fix(300.0), held = true))
        assertTrue(refetch(fix(420.0), held = true))
    }

    @Test
    fun `GPS eases off after a minute standing still, and picks up on moving`() {
        val t = MovementTracker()
        t.reset(now)
        assertTrue(t.update(fix(0.0, speed = 0f), now))
        assertTrue(t.update(fix(5.0, speed = 0f), now + 59_000))
        assertFalse(t.update(fix(5.0, speed = 0f), now + 60_000))
        assertTrue(t.update(fix(30.0, speed = 0f), now + 90_000))
    }

    @Test
    fun `a fix that wanders within its own error isn't movement`() {
        val t = MovementTracker()
        t.reset(now)
        t.update(fix(0.0, accuracy = 40f, speed = 0f), now)
        assertFalse(t.update(fix(35.0, accuracy = 40f, speed = 0f), now + 60_000))
    }

    @Test
    fun `a brisk walk counts as moving by speed alone`() {
        val t = MovementTracker()
        t.reset(now)
        t.update(fix(0.0, speed = 0f), now)
        assertTrue(t.update(fix(2.0, speed = 1.4f), now + 60_000))
    }
}
