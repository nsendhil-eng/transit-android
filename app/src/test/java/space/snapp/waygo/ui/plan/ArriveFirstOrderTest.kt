package space.snapp.waygo.ui.plan

import org.junit.Assert.assertEquals
import org.junit.Test
import space.snapp.waygo.data.api.models.ArriveFirstOrder
import space.snapp.waygo.data.api.models.Itinerary

class ArriveFirstOrderTest {
    private val t0 = 1_793_874_000_000L
    private fun itin(leaveMin: Int, arriveMin: Int, transfers: Int) =
        Itinerary(duration = (arriveMin - leaveMin) * 60, startTime = t0 + leaveMin * 60_000L,
            endTime = t0 + arriveMin * 60_000L, walkDistance = 500, transfers = transfers, legs = emptyList())

    @Test
    fun `same arrival minute, the direct trip leaving later wins over a hop and a change`() {
        val hopAndChange = itin(0, 24, transfers = 1)    // go now, bus hop, change to the 412
        val direct412 = itin(5, 24, transfers = 0)       // go in 5, the same 412, no change
        val later = itin(9, 26, transfers = 0)
        assertEquals(listOf(direct412, hopAndChange, later), listOf(hopAndChange, later, direct412).sortedWith(ArriveFirstOrder))
    }

    @Test
    fun `arriving a minute earlier still wins, change or not`() {
        val earlier = itin(0, 23, transfers = 1)
        val direct = itin(5, 24, transfers = 0)
        assertEquals(earlier, listOf(direct, earlier).sortedWith(ArriveFirstOrder).first())
    }
}
