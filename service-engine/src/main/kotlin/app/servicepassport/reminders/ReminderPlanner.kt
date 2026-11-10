package app.servicepassport.reminders

import app.servicepassport.engine.Category
import app.servicepassport.engine.Dimension
import app.servicepassport.engine.EngineSettings
import app.servicepassport.engine.OdometerReading
import app.servicepassport.engine.Reason
import app.servicepassport.engine.TaskStatus
import java.time.LocalDate
import java.time.temporal.ChronoUnit

public enum class ReminderKind {
    OVERDUE,
    DUE_SOON,
    /** Jedno przypomnienie na pojazd: aplikacja nie zna bieżącego przebiegu bez wpisu użytkownika. */
    UPDATE_ODOMETER,
}

/** Ustawienia użytkownika. Uprawnienie systemowe do powiadomień sprawdza warstwa Androida. */
public data class ReminderSettings(
    val enabled: Boolean = true,
    /** Co ile dni powtarzać to samo przypomnienie; null = tylko raz. */
    val repeatEveryDays: Int? = 14,
    val odometerReminders: Boolean = true,
) {
    init {
        require(repeatEveryDays == null || repeatEveryDays > 0) { "repeatEveryDays must be positive" }
    }
}

/**
 * To, o czym można przypomnieć: KOŃCOWY wynik jednej reguły, taki jaki widzi użytkownik.
 * Aplikacja buduje go z wyniku pulpitu, żeby pulpit i powiadomienia nigdy się nie różniły;
 * [from] służy tylko wywołaniom bez pulpitu (testy, narzędzia).
 */
public data class ReminderSubject(
    /** Stały identyfikator reguły lub pozycji pulpitu. */
    val id: String,
    val category: Category,
    val dueDate: LocalDate?,
    val dueKm: Int?,
    /** Odczyt przebiegu użyty w wyniku; null, gdy go nie ma. */
    val odometerUsed: OdometerReading?,
    /** Przebieg wymaga uzupełnienia lub sprawdzenia (brak, nieaktualny, niespójny, nowszy w historii). */
    val odometerNeedsAttention: Boolean,
    /** Powód, dla którego mimo kategorii nie wolno wysłać pewnego zalecenia. */
    val doubt: Reason? = null,
) {
    public companion object {
        private val ODOMETER_ATTENTION = setOf(
            Reason.ODOMETER_STALE, Reason.ODOMETER_UPDATE_REQUIRED, Reason.ODOMETER_INCONSISTENT, Reason.NEWER_ODOMETER_IN_HISTORY,
        )

        public fun from(status: TaskStatus): ReminderSubject = ReminderSubject(
            id = status.rule.id,
            category = status.category,
            dueDate = status.dueDate,
            dueKm = status.dueKm,
            odometerUsed = status.basis.odometerUsed,
            odometerNeedsAttention = ODOMETER_ATTENTION.any { it in status.reasons },
            doubt = when {
                Reason.SOURCE_EXPIRED in status.reasons -> Reason.SOURCE_EXPIRED
                Reason.ODOMETER_INCONSISTENT in status.reasons && status.basis.decidedBy == Dimension.DISTANCE -> Reason.ODOMETER_INCONSISTENT
                else -> null
            },
        )
    }
}

public data class Reminder(
    /** Stały identyfikator: ten sam dla tego samego pojazdu, rodzaju i reguły. Służy jako id powiadomienia. */
    val key: String,
    val kind: ReminderKind,
    val vehicleId: String,
    /** Pozycja, której dotyczy przypomnienie; null dla UPDATE_ODOMETER. */
    val subject: ReminderSubject?,
    /** Zmienia się, gdy zmienia się termin (np. po dodaniu serwisu) — wtedy stare „już wysłano” nie obowiązuje. */
    val fingerprint: String,
)

/** Zapis tego, co już pokazano. Przechowuje go aplikacja (np. w bazie), planner jest bezstanowy. */
public data class DeliveredReminder(val key: String, val fingerprint: String, val lastNotifiedOn: LocalDate)

public data class ReminderPlan(
    /** Do pokazania teraz. Po pokazaniu aplikacja zapisuje DeliveredReminder z dzisiejszą datą. */
    val notifyNow: List<Reminder>,
    /** Klucze nieaktualnych przypomnień: usuń powiadomienie i zapis o dostarczeniu. */
    val cancel: Set<String>,
    /** Najbliższy dzień, w którym wynik może się zmienić; null = nic zaplanowanego. Wskazówka dla WorkManagera. */
    val nextCheckOn: LocalDate?,
    /**
     * Reguły (id → przyczyna), dla których termin jest zaległy lub bliski, ale przypomnienia celowo NIE ma,
     * bo wynik nie jest pewny. Pulpit musi pokazać przyczynę zamiast zalecenia.
     */
    val suppressed: Map<String, Reason> = emptyMap(),
)

/**
 * Decyduje, o czym przypomnieć. Czysta funkcja: bez zegara, bazy i Androida.
 * Ten sam stan wejściowy nigdy nie daje drugiego powiadomienia przed upływem [ReminderSettings.repeatEveryDays].
 */
public object ReminderPlanner {

    public fun plan(
        vehicleId: String,
        subjects: List<ReminderSubject>,
        today: LocalDate,
        settings: ReminderSettings = ReminderSettings(),
        delivered: List<DeliveredReminder> = emptyList(),
        engineSettings: EngineSettings = EngineSettings(),
    ): ReminderPlan {
        val prefix = "$vehicleId|"
        val mine = delivered.filter { it.key.startsWith(prefix) }
        if (!settings.enabled) return ReminderPlan(emptyList(), mine.map { it.key }.toSet(), null)

        val current = mutableListOf<Reminder>()
        val suppressed = sortedMapOf<String, Reason>()
        for (s in subjects) {
            val kind = when (s.category) {
                Category.OVERDUE -> ReminderKind.OVERDUE
                Category.DUE_SOON -> ReminderKind.DUE_SOON
                else -> continue // „wymaga danych” i „bez pilnej potrzeby” pokazuje pulpit, nie powiadomienie
            }
            if (s.doubt != null) { // nie wysyłamy pozornie pewnego zalecenia
                suppressed[s.id] = s.doubt
                continue
            }
            current += Reminder("$prefix$kind|${s.id}", kind, vehicleId, s, "$kind|${s.dueDate}|${s.dueKm}")
        }
        if (settings.odometerReminders && subjects.any { it.odometerNeedsAttention }) {
            // Jedno przypomnienie na pojazd. Odcisk zmienia się po każdym nowym odczycie.
            val reading = subjects.filter { it.odometerNeedsAttention }.firstNotNullOfOrNull { it.odometerUsed }
            current += Reminder(
                "$prefix${ReminderKind.UPDATE_ODOMETER}", ReminderKind.UPDATE_ODOMETER, vehicleId, null,
                "${ReminderKind.UPDATE_ODOMETER}|${reading?.date}|${reading?.km}",
            )
        }

        val byKey = mine.associateBy { it.key }
        val repeatDates = mutableListOf<LocalDate>()
        val notifyNow = current.filter { r ->
            val last = byKey[r.key]
            if (last == null || last.fingerprint != r.fingerprint) return@filter true
            val repeatOn = settings.repeatEveryDays?.let { last.lastNotifiedOn.plusDays(it.toLong()) } ?: return@filter false
            if (repeatOn.isAfter(today)) repeatDates += repeatOn
            !repeatOn.isAfter(today)
        }.sortedWith(compareBy<Reminder> { it.kind.ordinal }.thenBy { it.subject?.dueDate ?: LocalDate.MAX }.thenBy { it.key })

        val currentKeys = current.map { it.key }.toSet()
        return ReminderPlan(
            notifyNow = notifyNow,
            cancel = mine.map { it.key }.filterNot { it in currentKeys }.toSet(),
            nextCheckOn = (repeatDates + transitions(subjects, engineSettings, settings)).filter { it.isAfter(today) }.minOrNull(),
            suppressed = suppressed,
        )
    }

    /** Dni, w których wynik zmieni się sam z upływem czasu (progi km zmienia tylko nowy odczyt przebiegu). */
    private fun transitions(subjects: List<ReminderSubject>, engine: EngineSettings, settings: ReminderSettings): List<LocalDate> {
        val dates = mutableListOf<LocalDate>()
        for (s in subjects) {
            s.dueDate?.let {
                dates += it.minusDays(engine.dueSoonDays.toLong()) // wejście w „zbliża się”
                dates += it.plusDays(1) // pierwszy dzień „zaległe”
            }
            if (settings.odometerReminders && s.dueKm != null) {
                s.odometerUsed?.let { dates += it.date.plusDays(engine.odometerStaleAfterDays + 1L) }
            }
        }
        return dates
    }

    /** Liczba dni od ostatniego odczytu przebiegu — do treści przypomnienia UPDATE_ODOMETER. */
    public fun odometerAgeDays(subjects: List<ReminderSubject>, today: LocalDate): Long? =
        subjects.firstNotNullOfOrNull { it.odometerUsed }?.let { ChronoUnit.DAYS.between(it.date, today) }
}
