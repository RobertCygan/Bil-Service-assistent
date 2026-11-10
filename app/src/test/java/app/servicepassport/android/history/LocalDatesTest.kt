package app.servicepassport.android.history

import app.servicepassport.android.data.*
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class LocalDatesTest {
    private val today = LocalDate.of(2026, 11, 10)
    @Test fun datesRespectLanguageAndKeepTheSameCalendarDay() {
        val day = LocalDate.of(2024, 2, 29).toEpochDay()
        assertEquals(day, LocalDates.day("29.02.2024", "nb-NO", today))
        assertEquals(day, LocalDates.day("2024-02-29", "en", today))
        assertEquals("29.02.2024", LocalDates.text(day, "nb-NO"))
        assertEquals("2024-02-29", LocalDates.text(day, "en"))
        assertNull(LocalDates.day("", "en", today, optional = true))
    }
    @Test fun invalidOrFutureDatesAreRejectedRatherThanNormalized() {
        listOf("2023-02-29", "2026-11-11", "1899-12-31", "31.12.2025", "").forEach {
            assertThrows(InputRejected::class.java) { LocalDates.day(it, "en", today) }
        }
        listOf("31.04.2026", "2026-01-02", "1.2.2026").forEach {
            assertThrows(InputRejected::class.java) { LocalDates.day(it, "nb-NO", today) }
        }
    }
}
