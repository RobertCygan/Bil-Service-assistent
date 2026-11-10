package app.servicepassport.android.history

import app.servicepassport.android.data.*
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class HistoryValidationTest {
    private val today = LocalDate.of(2026, 11, 10)
    private fun service(vararg tasks: WorkInput) = ServiceInput("id", "car", null, today.toEpochDay(), null, tasks.toList())

    @Test fun moneyIsExactAndRespectsTheSelectedLanguage() {
        assertEquals(123450L, LocalNumbers.moneyMinor("1 234,50", "nb-NO"))
        assertEquals(123450L, LocalNumbers.moneyMinor("1,234.50", "en"))
        assertEquals(1L, LocalNumbers.moneyMinor("0,01", "nb-NO"))
        assertEquals(0L, LocalNumbers.moneyMinor("0", "en"))
        assertNull(LocalNumbers.moneyMinor(" ", "en"))
    }

    @Test fun moneyNeverGuessesSeparatorsOrRoundsExtraDecimals() {
        listOf("1.234,50", "1,234.50", "1,001", "-10,00", "1e6").forEach {
            assertThrows(InputRejected::class.java) { LocalNumbers.moneyMinor(it, "nb-NO") }
        }
        listOf("1,23", "12.345", "-0.01", "92233720368547758.08").forEach {
            assertThrows(InputRejected::class.java) { LocalNumbers.moneyMinor(it, "en") }
        }
    }

    @Test fun kilometersStayWholeAndWithinTheEngineRange() {
        assertEquals(59000L, LocalNumbers.kilometers("59 000", "nb-NO"))
        assertEquals(59000L, LocalNumbers.kilometers("59,000", "en"))
        assertEquals(0L, LocalNumbers.kilometers("0", "en"))
        assertNull(LocalNumbers.kilometers("", "nb-NO"))
        listOf("59.200", "-1", "1.5", "2147483648", "99999999999999999999999999").forEach {
            assertThrows(InputRejected::class.java) { LocalNumbers.kilometers(it, "en") }
        }
    }

    @Test fun confirmedWorkRequiresAtLeastOneValidOperation() {
        assertThrows(InputRejected::class.java) { HistoryValidation.service(service(), today) }
        assertThrows(InputRejected::class.java) { HistoryValidation.service(service(WorkInput("engine_oil", "other")), today) }
        HistoryValidation.service(service(WorkInput("engine_oil", "replace"), WorkInput("brakes", "inspect")), today)
        HistoryValidation.service(service(WorkInput(null, null, "Repaired window")), today)
    }

    @Test fun duplicatesAndFutureServicesAreRejectedBeforeSaving() {
        val work = WorkInput("engine_oil", "replace")
        assertThrows(InputRejected::class.java) { HistoryValidation.service(service(work, work), today) }
        assertThrows(InputRejected::class.java) { HistoryValidation.service(service(work).copy(performedDay = today.plusDays(1).toEpochDay()), today) }
        assertThrows(InputRejected::class.java) { HistoryValidation.service(service(WorkInput(null, null, "Work"), WorkInput(null, null, " Work ")), today) }
    }
}
