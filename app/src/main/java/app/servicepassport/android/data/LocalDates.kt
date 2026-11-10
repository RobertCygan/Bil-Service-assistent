package app.servicepassport.android.data

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle

object LocalDates {
    private fun formatter(locale: String) = DateTimeFormatter.ofPattern(if (locale.startsWith("nb") || locale.startsWith("no")) "dd.MM.uuuu" else "uuuu-MM-dd").withResolverStyle(ResolverStyle.STRICT)
    fun text(day: Long, locale: String): String = LocalDate.ofEpochDay(day).format(formatter(locale))
    fun day(value: String, locale: String, today: LocalDate, optional: Boolean = false): Long? {
        if (value.isBlank() && optional) return null
        val day = try { LocalDate.parse(value.trim(), formatter(locale)).toEpochDay() } catch (_: RuntimeException) { throw InputRejected(InputProblem.INVALID_DATE) }
        HistoryValidation.day(day, today)
        return day
    }
}
