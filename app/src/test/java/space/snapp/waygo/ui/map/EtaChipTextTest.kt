package space.snapp.waygo.ui.map

import org.junit.Assert.assertEquals
import org.junit.Test
import space.snapp.waygo.data.api.models.Eta
import space.snapp.waygo.data.api.models.EtaLeg

class EtaChipTextTest {
    private fun eta(minutes: Int, leaveIn: Int, mode: String? = "BUS", route: String? = "412") = Eta(
        minutes = minutes, leaveInMinutes = leaveIn, departAt = "", arriveAt = "", walkMinutes = 5, transfers = 0,
        walkOnly = mode == null,
        firstLeg = mode?.let { EtaLeg(it, route, "City", null, null, "Wesley Hospital, stop 9", "", true) }
    )

    @Test
    fun `chip says how long, when to leave and what to catch`() {
        assertEquals("34 min · leave in 3 · 412", etaChipText(eta(34, 3)))
        assertEquals("34 min · leave now · 412", etaChipText(eta(34, 0)))
        // Rail route names are internal codes, so say "train"
        assertEquals("22 min · leave in 6 · train", etaChipText(eta(22, 6, mode = "RAIL", route = "IPCA")))
        assertEquals("12 min walk", etaChipText(eta(12, 0, mode = null)))
        assertEquals("You're here", etaChipText(eta(0, 0, mode = null)))
    }
}
