package app.servicepassport.vehicleid

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Numery VIN w testach to publiczne przykłady z dokumentacji albo numery zmyślone. */
class VehicleIdTest {

    @Test
    fun `valid VIN is normalized and gets region and make hint`() {
        val r = assertIs<VinResult.Valid>(Vin.parse(" wvw zzz-1kz6w000001 "))
        assertEquals("WVWZZZ1KZ6W000001", r.vin)
        assertEquals("WVW", r.wmi)
        assertEquals(VinRegion.EUROPE, r.region)
        assertEquals("Volkswagen", r.makeHint)
        assertFalse(r.checkDigitMatches, "European VINs normally fail the North American check digit")
    }

    @Test
    fun `check digit is computed for North American style VINs`() {
        assertTrue(assertIs<VinResult.Valid>(Vin.parse("1HGCM82633A004352")).checkDigitMatches)
        assertTrue(assertIs<VinResult.Valid>(Vin.parse("11111111111111111")).checkDigitMatches)
        assertTrue(assertIs<VinResult.Valid>(Vin.parse("1M8GDM9AXKP042788")).checkDigitMatches) // cyfra kontrolna X
        assertFalse(assertIs<VinResult.Valid>(Vin.parse("1HGCM82643A004352")).checkDigitMatches)
    }

    @Test
    fun `unknown manufacturer prefix gives no hint instead of a guess`() {
        val r = assertIs<VinResult.Valid>(Vin.parse("XXDEMA0000000T123"))
        assertNull(r.makeHint)
        assertEquals(VinRegion.EUROPE, r.region)
        assertEquals(VinRegion.NORTH_AMERICA, assertIs<VinResult.Valid>(Vin.parse("5YJ3E7EB0KF000001")).region)
        assertEquals("Tesla", assertIs<VinResult.Valid>(Vin.parse("5YJ3E7EB0KF000001")).makeHint)
        assertEquals(VinRegion.ASIA, assertIs<VinResult.Valid>(Vin.parse("KMHXX00XXXX000001")).region)
    }

    @Test
    fun `invalid VINs report every problem`() {
        fun errors(s: String) = assertIs<VinResult.Invalid>(Vin.parse(s)).errors
        assertEquals(setOf(VinError.TOO_SHORT), errors("WVWZZZ1KZ6W00000"))
        assertEquals(setOf(VinError.TOO_LONG), errors("WVWZZZ1KZ6W0000012"))
        assertEquals(setOf(VinError.FORBIDDEN_LETTER), errors("WVWZZZ1KZ6WO00001"))
        assertEquals(setOf(VinError.FORBIDDEN_LETTER), errors("WVWZZZIKZ6W000001"))
        assertEquals(setOf(VinError.INVALID_CHARACTER), errors("WVWZZZ1KZ6W00000Å"))
        assertEquals(setOf(VinError.TOO_SHORT, VinError.INVALID_CHARACTER), errors("WVW?"))
        assertEquals(setOf(VinError.TOO_SHORT), errors(""))
    }

    @Test
    fun `every make hint exists under the same name in the vehicle list`() {
        // Nazwy przepisane z vehicle-catalog/vehicle-list.json; test pilnuje spójności pisowni.
        val catalog = setOf(
            "Volkswagen", "Audi", "BMW", "Mercedes-Benz", "Smart", "Porsche", "Opel", "Ford", "Škoda", "SEAT",
            "Renault", "Peugeot", "Citroën", "DS", "Dacia", "Fiat", "Alfa Romeo", "Ferrari", "Volvo", "Saab",
            "Land Rover", "Jaguar", "Toyota", "Lexus", "Nissan", "Mazda", "Honda", "Subaru", "Hyundai", "Kia", "Tesla",
        )
        val prefixes = listOf(
            "WVW", "WVG", "WV1", "WV2", "WAU", "WUA", "TRU", "WBA", "WBS", "WBY", "WDB", "WDD", "WDC", "W1K", "W1N",
            "WME", "WP0", "WP1", "W0L", "W0V", "WF0", "TMB", "VSS", "VF1", "VF3", "VR3", "VF7", "VR7", "VR1", "UU1",
            "ZFA", "ZAR", "ZFF", "YV1", "YV4", "YS3", "SAL", "SAJ", "JTD", "JTE", "SB1", "VNK", "JTH", "JN1", "SJN",
            "JMZ", "JHM", "JF1", "KMH", "TMA", "NLH", "KNA", "KND", "U5Y", "5YJ", "7SA", "LRW", "XP7",
        )
        for (p in prefixes) assertTrue(WmiTable.makeFor(p) in catalog, p)
    }

    @Test
    fun `norwegian plates`() {
        assertEquals(PlateResult.Accepted("AB12345", PlateKind.STANDARD_CAR), NorwegianPlate.parse(" ab 12345 "))
        assertEquals(PlateResult.Accepted("EL12345", PlateKind.STANDARD_CAR), NorwegianPlate.parse("EL-12345"))
        assertEquals(PlateResult.Accepted("AB1234", PlateKind.STANDARD_SHORT), NorwegianPlate.parse("AB1234"))
        assertEquals(PlateResult.Accepted("MINBIL", PlateKind.OTHER), NorwegianPlate.parse("minbil"))
        assertEquals(PlateResult.Accepted("BLÅBÆR", PlateKind.OTHER), NorwegianPlate.parse("blåbær"))
        for (bad in listOf("", "A", "AB123456", "AB 12 34 5!", "ABCDEFGH")) {
            assertEquals(PlateResult.Invalid, NorwegianPlate.parse(bad), bad)
        }
    }

    @Test
    fun `catalog matching ignores case, accents and punctuation`() {
        val makes = listOf("Citroën", "Škoda", "Mercedes-Benz", "Lynk & Co", "BMW", "Kia")
        assertEquals("Citroën", CatalogMatcher.exact("CITROEN", makes))
        assertEquals("Škoda", CatalogMatcher.exact("skoda", makes))
        assertEquals("Mercedes-Benz", CatalogMatcher.exact("mercedes benz", makes))
        assertEquals("Lynk & Co", CatalogMatcher.exact("LYNK&CO", makes))
        assertNull(CatalogMatcher.exact("Mercedes", makes))
        assertNull(CatalogMatcher.exact("  ", makes))
    }

    @Test
    fun `search puts prefix matches first and keeps popularity order`() {
        val models = listOf("POLO", "GOLF", "T-ROC", "GOLF PLUS", "ID.3 PRO", "NEW BEETLE", "E-GOLF")
        assertEquals(listOf("GOLF", "GOLF PLUS", "E-GOLF"), CatalogMatcher.search("golf", models))
        assertEquals(listOf("T-ROC"), CatalogMatcher.search("troc", models))
        assertEquals(listOf("ID.3 PRO"), CatalogMatcher.search("id3", models))
        assertEquals(listOf("POLO", "GOLF"), CatalogMatcher.search("", models, limit = 2))
        assertTrue(CatalogMatcher.search("xyz", models).isEmpty())
    }
}
