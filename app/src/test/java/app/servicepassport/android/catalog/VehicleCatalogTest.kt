package app.servicepassport.android.catalog

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.servicepassport.vehicleid.VinError
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The list shipped in the APK, read the way the app reads it. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class VehicleCatalogTest {
    private val catalog: VehicleCatalog by lazy { VehicleCatalog.fromAssets(ApplicationProvider.getApplicationContext<Context>()) }

    @Test fun theShippedListIsReadableAndCarriesItsSourceAndLicence() {
        assertTrue(catalog.makes.size > 50)
        assertTrue(catalog.source, "RDW" in catalog.source && "EEA" in catalog.source)
        assertEquals("CC0 1.0", catalog.licence)
        assertEquals(catalog.makes.sortedBy { it.lowercase() }, catalog.makes)
    }

    @Test fun makesAreSuggestedWithoutCaringAboutCaseOrAccents() {
        assertEquals(listOf("Volkswagen", "Volvo"), catalog.suggestMakes("vol"))
        assertEquals("Škoda", catalog.suggestMakes("skod").single())
        assertEquals("Citroën", catalog.make("CITROEN"))
        assertEquals("Mercedes-Benz", catalog.make("mercedes benz"))
        assertNull(catalog.make("Volks"))
        assertTrue(catalog.suggestMakes("  ").isEmpty())
        assertTrue(catalog.suggestMakes("zzzz").isEmpty())
    }

    @Test fun modelsBelongToTheirMakeAndTheMostCommonComeFirst() {
        val common = catalog.suggestModels("volkswagen", "")
        assertEquals(8, common.size); assertTrue("GOLF" in common && "POLO" in common)
        assertEquals("GOLF", catalog.suggestModels("Volkswagen", "gol").first())
        assertTrue(catalog.suggestModels("Volkswagen", "troc").contains("T-ROC"))
        assertTrue(catalog.suggestModels("Tesla", "golf").isEmpty())
        assertTrue("an unknown make gets no model suggestions", catalog.suggestModels("My own make", "").isEmpty())
        val golf = requireNotNull(catalog.model("VOLKSWAGEN", "golf"))
        assertTrue("NO" in golf.nordic)
        assertNull(catalog.model("Volkswagen", "No such model"))
    }

    @Test fun vinFeedbackIsAboutFormOnly() {
        assertEquals(VinFeedback.Empty, VinFeedback.of("  "))
        assertEquals(VinFeedback.WellFormed("WVWZZZ1KZ6W000001", "Volkswagen"), VinFeedback.of("wvw zzz1kz6w000001"))
        assertEquals(VinFeedback.WellFormed("XXDEMA0000000T123", null), VinFeedback.of("XXDEMA0000000T123"))
        assertEquals(setOf(VinError.TOO_SHORT), (VinFeedback.of("WVW123") as VinFeedback.Invalid).errors)
        assertEquals(setOf(VinError.FORBIDDEN_LETTER), (VinFeedback.of("WVWZZZ1KZ6WO00001") as VinFeedback.Invalid).errors)
        val hint = (VinFeedback.of("5YJ3E7EB0KF000001") as VinFeedback.WellFormed).makeHint
        assertEquals("every hint is a make in the shipped list", hint, catalog.make(requireNotNull(hint)))
    }

    @Test fun onlyImpossiblePlatesAreFlagged() {
        assertFalse(plateLooksWrong("")); assertFalse(plateLooksWrong("AB 12345")); assertFalse(plateLooksWrong("EL12345")); assertFalse(plateLooksWrong("MINBIL"))
        assertTrue(plateLooksWrong("AB 12345 678")); assertTrue(plateLooksWrong("A"))
    }
}
