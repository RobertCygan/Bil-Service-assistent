package app.servicepassport.android.data

import app.servicepassport.contract.ServiceTask
import java.math.BigDecimal
import java.time.LocalDate

data class WorkInput(val actionCode: String?, val operationCode: String?, val customLabel: String? = null) {
    val task: ServiceTask? get() = if (actionCode == null || operationCode == null) null else ServiceTask.fromTaskCode("$actionCode:$operationCode")
}
data class ServiceInput(
    val id: String, val vehicleId: String, val expectedRevision: Long?, val performedDay: Long,
    val km: Long?, val actions: List<WorkInput>, val costMinor: Long? = null, val note: String = "",
    val useAsCurrent: Boolean = false, val exceptionReason: String = "", val warningsAcknowledged: Boolean = false, val reviewToken: String? = null,
)
data class ReadingInput(
    val id: String, val vehicleId: String, val expectedRevision: Long?, val observedDay: Long,
    val km: Long, val useAsCurrent: Boolean, val exceptionReason: String = "", val warningsAcknowledged: Boolean = false, val reviewToken: String? = null,
)
data class CurrentReadingInput(
    val readingId: String,
    val vehicleId: String,
    val expectedRevision: Long,
    val exceptionReason: String? = null,
    val warningsAcknowledged: Boolean = false,
    val reviewToken: String? = null,
)
/** One reading of the car's service display: distance and/or days left, as shown. Negative means the car says it is overdue. */
data class IndicatorInput(val id: String, val vehicleId: String, val observedDay: Long, val odometerKm: Long, val remainingKm: Int?, val remainingDays: Int?)
object IndicatorValidation {
    const val MAX_KM = 200_000
    const val MAX_DAYS = 3_650
    fun validate(input: IndicatorInput, today: java.time.LocalDate) {
        HistoryValidation.day(input.observedDay, today); HistoryValidation.km(input.odometerKm)
        if (input.remainingKm == null && input.remainingDays == null) throw InputRejected(InputProblem.INVALID_INDICATOR)
        if (input.remainingKm != null && input.remainingKm !in -MAX_KM..MAX_KM) throw InputRejected(InputProblem.INVALID_INDICATOR)
        if (input.remainingDays != null && input.remainingDays !in -MAX_DAYS..MAX_DAYS) throw InputRejected(InputProblem.INVALID_INDICATOR)
    }
}
enum class ReadingWarning { INCONSISTENT_VALUE, SAME_DAY_DIFFERENT, OLDER_CURRENT_SELECTION }
enum class InputProblem { DATE_IN_FUTURE, INVALID_DATE, INVALID_KM, NO_ACTIONS, INVALID_ACTION, DUPLICATE_ACTION, INVALID_COST, INVALID_PROFILE, NOTE_TOO_LONG, WARNINGS_NEED_REVIEW, NOT_FOUND, CONFLICT, INVALID_INTERVAL, DUPLICATE_INTERVAL, INVALID_INDICATOR }
class InputRejected(val problem: InputProblem) : IllegalArgumentException(problem.name)

object ReadingConsistency {
    fun token(snapshot: GarageSnapshot, excluding: String?, day: Long, km: Long, useAsCurrent: Boolean): String =
        "$day/$km/$useAsCurrent/${snapshot.currentReading?.id}/" + snapshot.readings.filter { it.id != excluding }.sortedBy { it.id }.joinToString("|") { "${it.id}:${it.revision}:${it.observedDay}:${it.km}" }
    fun warnings(day: Long, km: Long, others: List<OdometerReadingEntity>, current: OdometerReadingEntity?, useAsCurrent: Boolean): Set<ReadingWarning> = buildSet {
        if (others.any { (it.observedDay < day && it.km > km) || (it.observedDay > day && it.km < km) }) add(ReadingWarning.INCONSISTENT_VALUE)
        if (others.any { it.observedDay == day && it.km != km }) add(ReadingWarning.SAME_DAY_DIFFERENT)
        if (useAsCurrent && current != null && day < current.observedDay) add(ReadingWarning.OLDER_CURRENT_SELECTION)
    }
}

/** Strict, locale-aware input: never guess whether a comma is a decimal separator. */
object LocalNumbers {
    private fun norwegian(locale: String) = locale.startsWith("nb", true) || locale.startsWith("no", true)
    private fun integerPattern(nb: Boolean) = if (nb) "(?:[0-9]+|[0-9]{1,3}(?:[ \\u00a0\\u202f][0-9]{3})+)" else "(?:[0-9]+|[0-9]{1,3}(?:,[0-9]{3})+)"
    fun kilometers(value: String, locale: String): Long? {
        val s = value.trim(); if (s.isEmpty()) return null
        if (s.length > 24) throw InputRejected(InputProblem.INVALID_KM)
        if (!Regex(integerPattern(norwegian(locale))).matches(s)) throw InputRejected(InputProblem.INVALID_KM)
        val n = s.filter { it in '0'..'9' }.toLongOrNull() ?: throw InputRejected(InputProblem.INVALID_KM)
        if (n !in 0..Int.MAX_VALUE.toLong()) throw InputRejected(InputProblem.INVALID_KM)
        return n
    }
    fun moneyMinor(value: String, locale: String): Long? {
        val s = value.trim(); if (s.isEmpty()) return null
        if (s.length > 40) throw InputRejected(InputProblem.INVALID_COST)
        val nb = norwegian(locale); val fraction = if (nb) "(?:,[0-9]{1,2})?" else "(?:\\.[0-9]{1,2})?"
        if (!Regex(integerPattern(nb) + fraction).matches(s)) throw InputRejected(InputProblem.INVALID_COST)
        val normalized = if (nb) s.filterNot { it == ' ' || it == '\u00a0' || it == '\u202f' }.replace(',', '.') else s.replace(",", "")
        return try { BigDecimal(normalized).movePointRight(2).longValueExact() } catch (_: ArithmeticException) { throw InputRejected(InputProblem.INVALID_COST) }
    }
}
object HistoryValidation {
    fun day(value: Long, today: LocalDate) {
        val date = try { LocalDate.ofEpochDay(value) } catch (_: RuntimeException) { throw InputRejected(InputProblem.INVALID_DATE) }
        if (date.isAfter(today)) throw InputRejected(InputProblem.DATE_IN_FUTURE)
        if (date.year < 1900) throw InputRejected(InputProblem.INVALID_DATE)
    }
    fun km(value: Long) { if (value !in 0..Int.MAX_VALUE.toLong()) throw InputRejected(InputProblem.INVALID_KM) }
    fun service(input: ServiceInput, today: LocalDate) {
        day(input.performedDay, today); input.km?.let(::km)
        if (input.actions.isEmpty()) throw InputRejected(InputProblem.NO_ACTIONS)
        if (input.actions.size > 30 || input.actions.any { if (it.customLabel != null) it.customLabel.isBlank() || it.customLabel.length > 120 || it.actionCode != null || it.operationCode != null else it.task == null }) throw InputRejected(InputProblem.INVALID_ACTION)
        if (input.actions.map { it.copy(customLabel = it.customLabel?.trim()) }.distinct().size != input.actions.size) throw InputRejected(InputProblem.DUPLICATE_ACTION)
        if (input.costMinor != null && input.costMinor < 0) throw InputRejected(InputProblem.INVALID_COST)
        if (input.note.length > 4000 || input.exceptionReason.length > 1000) throw InputRejected(InputProblem.NOTE_TOO_LONG)
        if (input.useAsCurrent && input.km == null) throw InputRejected(InputProblem.INVALID_KM)
    }
}
