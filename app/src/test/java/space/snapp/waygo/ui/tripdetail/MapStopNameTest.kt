package space.snapp.waygo.ui.tripdetail

import org.junit.Assert.assertEquals
import org.junit.Test

class MapStopNameTest {
    @Test
    fun `real SEQ stop names shorten to a readable map label`() {
        val cases = mapOf(
            "Coronation Dr at Wesley Hospital, stop 9" to "Wesley Hospital",
            "High St at Toowong Village, stop 23" to "Toowong Village",
            "Sherwood Rd at Kensington Terrace, stop 21" to "Kensington Terrace",
            "UQ Chancellor's Place, zone D" to "UQ Chancellor's Place",
            "Roma Street busway, platform 2" to "Roma Street busway",
            "Roma Street Stop 125 at Transit Centre" to "Transit Centre",
            "Elizabeth Street Stop 81 near George St" to "Elizabeth St near George St",
            "Wickham Terrace Stop 132 near Albert St" to "Wickham Tce near Albert St",
            "Regatta ferry terminal" to "Regatta ferry terminal",
            "Auchenflower station, platform 2" to "Auchenflower station",
            "Coldridge St near Carmody Rd, stop 23a" to "Coldridge St near Carmody Rd"
        )
        for ((full, short) in cases) assertEquals(full, short, mapStopName(full))
    }
}
