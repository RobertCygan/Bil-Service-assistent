package app.servicepassport.engine

import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import kotlin.math.ceil

/** Deterministyczny silnik terminów. Bez I/O, zegara i tekstów — `today` przychodzi z wejścia. */
public object ServiceEngine {

    public fun evaluate(input: EvaluationInput): List<TaskStatus> {
        val today = input.today
        val history = input.history.filter { !it.date.isAfter(today) }
        val indicators = input.indicators.filter { !it.date.isAfter(today) }
        val selectedInFuture = (input.currentOdometer as? CurrentOdometer.Selected)?.reading?.date?.isAfter(today) == true
        val futureIgnored = selectedInFuture || history.size != input.history.size ||
            indicators.size != input.indicators.size ||
            input.odometer.any { it.date.isAfter(today) }

        val readings = (
            input.odometer.filter { !it.date.isAfter(today) } +
                history.mapNotNull { t -> t.odometerKm?.let { OdometerReading(t.date, it) } } +
                indicators.map { OdometerReading(it.date, it.odometerKm) }
            ).sortedWith(compareBy<OdometerReading> { it.date }.thenBy { it.km })

        val current = when (val choice = input.currentOdometer) {
            is CurrentOdometer.Selected -> choice.reading.takeIf { !it.date.isAfter(today) }
            CurrentOdometer.NotSelected -> null
            CurrentOdometer.LatestKnown -> readings.lastOrNull()
        }
        val odo = OdometerState(
            current = current,
            superseded = current != null && readings.any { it.date.isAfter(current.date) && it.km > current.km },
            inconsistent = readings.zipWithNext().any { (a, b) -> b.km < a.km },
            dailyKm = dailyKm(readings, input.settings),
        )
        val ctx = Context(input, history, indicators, odo, futureIgnored)

        return input.rules.map { evaluateRule(it, ctx) }.sortedWith(
            compareBy<TaskStatus> { it.category.ordinal }
                .thenBy { it.dueDate ?: LocalDate.MAX }
                .thenBy { it.remainingKm ?: Int.MAX_VALUE }
                .thenBy { it.rule.id },
        )
    }

    private class OdometerState(val current: OdometerReading?, val superseded: Boolean, val inconsistent: Boolean, val dailyKm: Double?)

    private class Context(
        val input: EvaluationInput,
        val history: List<PerformedTask>,
        val indicators: List<IndicatorReading>,
        val odo: OdometerState,
        val futureIgnored: Boolean,
    )

    /** Kandydat na termin przed oceną względem stanu obecnego. */
    private class Due(
        val anchorKind: AnchorKind,
        val anchorDate: LocalDate?,
        val anchorKm: Int?,
        val intervalKind: IntervalKind,
        val interval: Interval?,
        val dueKm: Int?,
        val dueDate: LocalDate?,
        val needsKm: Boolean,
        val needsDate: Boolean,
        /** Sam w sobie nie pozwala stwierdzić „bez pilnej potrzeby” (plan zmienny bez odczytu wskaźnika). */
        val inconclusive: Boolean,
        /** Suma przebiegu i interwału nie mieści się w zakresie; [dueKm] jest wtedy null. */
        val overflow: Boolean = false,
    )

    private fun evaluateRule(rule: ServiceRule, ctx: Context): TaskStatus {
        val input = ctx.input
        val today = input.today
        val settings = input.settings
        val current = ctx.odo.current
        val reasons = sortedSetOf<Reason>()

        val last = ctx.history.filter { it.taskCode == rule.taskCode }
            .maxWithOrNull(compareBy<PerformedTask> { it.date }.thenBy { it.odometerKm ?: -1 })
        val due = dueFor(rule, last, ctx)

        val remainingKm = if (due.dueKm != null && current != null) due.dueKm - current.km else null
        val remainingDays = due.dueDate?.let { ChronoUnit.DAYS.between(today, it) }
        val kmOverdue = remainingKm != null && remainingKm < 0
        val timeOverdue = remainingDays != null && remainingDays < 0
        val overdue = kmOverdue || timeOverdue

        if (due.overflow) {
            reasons += Reason.CALCULATION_LIMIT
        } else if (due.needsKm && remainingKm == null) {
            reasons += when {
                current == null -> Reason.ODOMETER_MISSING
                else -> Reason.LAST_SERVICE_ODOMETER_MISSING
            }
        }
        if (due.needsDate && due.dueDate == null) reasons += Reason.IN_SERVICE_DATE_MISSING
        val missing = reasons.isNotEmpty()

        if (due.inconclusive) reasons += Reason.VARIABLE_SCHEDULE
        if (ctx.futureIgnored) reasons += Reason.FUTURE_ENTRIES_IGNORED
        if (rule.source.validUntil?.isBefore(today) == true) reasons += Reason.SOURCE_EXPIRED

        // Aktualność przebiegu i szacunek dotyczą tylko reguł z wymiarem km.
        var estimatedMonth: YearMonth? = null
        var projectionPassed = false
        if (due.dueKm != null && current != null) {
            if (ctx.odo.inconsistent) reasons += Reason.ODOMETER_INCONSISTENT
            if (ctx.odo.superseded) reasons += Reason.NEWER_ODOMETER_IN_HISTORY
            val stale = ChronoUnit.DAYS.between(current.date, today) > settings.odometerStaleAfterDays
            if (stale) reasons += Reason.ODOMETER_STALE
            val rate = ctx.odo.dailyKm
            if (rate != null && remainingKm != null && remainingKm >= 0) {
                val estimated = current.date.plusDays(ceil(remainingKm / rate).toLong())
                estimatedMonth = YearMonth.from(estimated)
                projectionPassed = stale && estimated.isBefore(today)
            }
        }

        val kmSoon = remainingKm != null && remainingKm <= settings.dueSoonKm
        val timeSoon = remainingDays != null && remainingDays <= settings.dueSoonDays

        val category = when {
            // Termin wg planu minął, ale nie mamy żadnego wpisu: to brak potwierdzenia, nie zaległość.
            overdue && due.anchorKind == AnchorKind.VEHICLE_START -> {
                reasons += Reason.NO_CONFIRMATION_IN_DOCUMENTS
                Category.NEEDS_DATA
            }
            overdue -> Category.OVERDUE
            missing || due.inconclusive -> Category.NEEDS_DATA
            projectionPassed -> {
                reasons += Reason.ODOMETER_UPDATE_REQUIRED
                Category.NEEDS_DATA
            }
            kmSoon || timeSoon -> Category.DUE_SOON
            else -> Category.NO_URGENCY
        }

        val decidedBy = when {
            timeOverdue -> Dimension.TIME
            kmOverdue -> Dimension.DISTANCE
            category != Category.DUE_SOON -> null
            timeSoon -> Dimension.TIME
            else -> Dimension.DISTANCE
        }

        return TaskStatus(
            rule = rule,
            category = category,
            dueKm = due.dueKm,
            dueDate = due.dueDate,
            remainingKm = remainingKm,
            remainingDays = remainingDays,
            estimatedKmDueMonth = estimatedMonth,
            basis = Basis(
                due.anchorKind, due.anchorDate, due.anchorKm, due.intervalKind, due.interval,
                odometerUsed = if (due.dueKm != null || due.needsKm) current else null,
                decidedBy = decidedBy,
            ),
            reasons = reasons,
        )
    }

    private fun dueFor(rule: ServiceRule, last: PerformedTask?, ctx: Context): Due {
        val anchorKind = if (last != null) AnchorKind.LAST_CONFIRMED_TASK else AnchorKind.VEHICLE_START
        val anchorDate = if (last != null) last.date else ctx.input.inServiceDate
        val anchorKm = if (last != null) last.odometerKm else 0

        fun fromAnchor(interval: Interval?, kind: IntervalKind, inconclusive: Boolean) = Due(
            anchorKind, anchorDate, anchorKm, kind, interval,
            dueKm = interval?.km?.let { km -> anchorKm?.let { add(it, km) } },
            overflow = interval?.km != null && anchorKm != null && add(anchorKm, interval.km) == null,
            dueDate = interval?.months?.let { m -> anchorDate?.plusMonths(m.toLong()) },
            needsKm = interval?.km != null,
            needsDate = interval?.months != null,
            inconclusive = inconclusive,
        )

        return when (val s = rule.schedule) {
            is Schedule.Fixed ->
                if (last != null) fromAnchor(s.repeat, IntervalKind.REPEAT, false)
                else fromAnchor(s.first ?: s.repeat, IntervalKind.FIRST, false)

            is Schedule.Variable -> {
                // Liczy się tylko odczyt wskaźnika zrobiony po ostatniej potwierdzonej czynności.
                val indicator = ctx.indicators
                    .filter { it.taskCode == rule.taskCode && (last == null || it.date.isAfter(last.date)) }
                    .maxWithOrNull(compareBy<IndicatorReading> { it.date }.thenBy { it.odometerKm })
                if (indicator != null) {
                    Due(
                        AnchorKind.VEHICLE_INDICATOR, indicator.date, indicator.odometerKm,
                        IntervalKind.INDICATOR, null,
                        dueKm = indicator.remainingKm?.let { add(indicator.odometerKm, it) },
                        overflow = indicator.remainingKm != null && add(indicator.odometerKm, indicator.remainingKm) == null,
                        dueDate = indicator.remainingDays?.let { indicator.date.plusDays(it.toLong()) },
                        needsKm = indicator.remainingKm != null,
                        needsDate = indicator.remainingDays != null,
                        inconclusive = false,
                    )
                } else {
                    fromAnchor(s.upperLimit, IntervalKind.UPPER_LIMIT, inconclusive = true)
                }
            }
        }
    }

    /** Dodawanie bez zawijania: null, gdy wynik nie mieści się w Int. */
    private fun add(a: Int, b: Int): Int? = (a.toLong() + b.toLong()).takeIf { it in Int.MIN_VALUE..Int.MAX_VALUE }?.toInt()

    /** Średni dzienny przebieg; null, gdy danych jest za mało lub są niespójne — wtedy nie szacujemy. */
    private fun dailyKm(sorted: List<OdometerReading>, settings: EngineSettings): Double? {
        if (sorted.size < 2 || sorted.zipWithNext().any { (a, b) -> b.km < a.km }) return null
        val latest = sorted.last()
        val windowStart = latest.date.minusDays(settings.estimationWindowDays.toLong())
        var first = sorted.first { !it.date.isBefore(windowStart) }
        if (ChronoUnit.DAYS.between(first.date, latest.date) < settings.estimationMinSpanDays) first = sorted.first()
        val span = ChronoUnit.DAYS.between(first.date, latest.date)
        if (span < settings.estimationMinSpanDays) return null
        val rate = (latest.km - first.km).toDouble() / span
        return rate.takeIf { it > 0 }
    }
}
