package app.servicepassport.extraction

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Wymyślone odczyty tekstu ze zdjęć zestawu wskaźników. To nie są zdjęcia prawdziwych aut. */
class OdometerPhotoTest {
    private fun read(vararg lines: String, last: Int? = null) = OdometerPhoto.candidates(lines.map { OcrLine(it) }, last)
    private fun best(vararg lines: String, last: Int? = null) = read(*lines, last = last).firstOrNull()?.km

    @Test fun `the total mileage is picked from among everything else on the cluster`() {
        assertEquals(93_231, best("20", "40", "60", "80", "100", "120", "140", "160", "180", "200", "ODO 93 231 km", "TRIP A 512.4 km", "12:45", "21.5 °C", "Range 350 km", "88 km/h"))
        assertEquals(93_231, best("93231 km", "Trip 1204.7", "P", "D", "14:02"))
        assertEquals(184_520, best("184.520 km", "6.4 l/100km", "9°C"))
    }

    @Test fun `the unit may stand on the next line and the label on the line before`() {
        assertEquals(93_231, best("120", "93 231", "km"))
        assertEquals(1_254, best("Kilometerstand", "1 254"))
        assertEquals(742, best("ODO", "742 km"), "a nearly new car: a short number counts when it is labelled")
    }

    @Test fun `the last known mileage decides between two plausible numbers`() {
        assertEquals(93_231, best("93231", "20261", last = 92_480))
        assertEquals(20_261, best("93231", "20261", last = 19_900))
        assertEquals(listOf(93_231, 20_261), read("93231", "20261", last = 92_480).map { it.km }, "the other number stays available as a second choice")
        assertEquals(listOf(93_231, 94_100), read("94100", "93231", last = 92_480).map { it.km }, "of two close numbers the nearer one comes first")
    }

    @Test fun `a number lower than the last known mileage is offered last, not hidden`() {
        val offered = read("ODO 91 000 km", last = 92_480)
        assertEquals(listOf(91_000), offered.map { it.km })
    }

    @Test fun `trip meters, range, time, speed and temperature are never the mileage`() {
        assertTrue(read("20", "40", "60", "80", "100", "120", "140", "160", "180", "200", "220").isEmpty(), "a speedometer scale alone")
        assertTrue(read("TRIP 512.4 km", "12:45", "21.5 °C", "88 km/h", "3000 rpm", "45 %").isEmpty())
        assertEquals(93_231, best("Range 1 150 km", "93 231 km"), "a long range on a labelled line loses to the plain mileage")
        assertEquals(93_231, best("Service 15000 km", "93231 km"))
        assertTrue(read("12.11.2026", "14:30").isEmpty(), "a date is not a mileage")
    }

    @Test fun `nothing readable gives no proposal`() {
        assertTrue(read().isEmpty())
        assertTrue(read("P R N D", "ECO", "READY").isEmpty())
        assertTrue(read("99999999999").isEmpty(), "a number longer than any mileage")
    }
}
