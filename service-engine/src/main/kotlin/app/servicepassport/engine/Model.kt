package app.servicepassport.engine

import java.time.LocalDate
import java.time.YearMonth

/** Pochodzenie reguły. UI musi je pokazywać; DEMO nigdy nie trafia do katalogu produkcyjnego. */
public enum class DataOrigin { VERIFIED_SOURCE, USER_DEFINED, DEMO }

/** Źródło reguły lub planu: skąd pochodzi, w jakiej wersji i kiedy je sprawdzono. */
public data class SourceRef(
    val id: String,
    val title: String,
    val version: String,
    val verifiedOn: LocalDate,
    /** Po tej dacie wynik dostaje [Reason.SOURCE_EXPIRED]; null = brak ustalonego terminu. */
    val validUntil: LocalDate?,
    val origin: DataOrigin,
    /** Podstawa prawna użycia (umowa/licencja) albo opis, że to ustawienie użytkownika. */
    val usageRights: String,
)

/** Cechy konfiguracji, od których może zależeć harmonogram. Wartości porównujemy bez wielkości liter. */
public enum class ConfigAttribute {
    MAKE, MODEL, GENERATION, MODEL_YEAR, POWERTRAIN, ENGINE_CODE,
    GEARBOX_TYPE, GEARBOX_CODE, MARKET, SERVICE_REGIME, USAGE_PROFILE,
}

/** Interwał: km i/lub miesiące. Gdy podano oba, obowiązuje ten, który nastąpi wcześniej. */
public data class Interval(val km: Int? = null, val months: Int? = null) {
    init {
        require(km != null || months != null) { "Interval needs km or months" }
        require(km == null || km > 0) { "km must be positive" }
        require(months == null || months > 0) { "months must be positive" }
    }
}

public sealed interface Schedule {
    /**
     * Stały harmonogram. [first] liczony od rozpoczęcia eksploatacji (data, 0 km), gdy brak
     * potwierdzonej czynności; null oznacza, że pierwszy termin jest równy [repeat].
     */
    public data class Fixed(val repeat: Interval, val first: Interval? = null) : Schedule

    /**
     * Harmonogram zmienny (termin wyznacza pojazd). Nie da się go sprowadzić do stałego interwału.
     * [upperLimit] to górna granica, tylko jeśli podaje ją źródło.
     */
    public data class Variable(val upperLimit: Interval? = null) : Schedule
}

public data class ServiceRule(
    val id: String,
    /** Kod czynności ze wspólnego słownika (ten sam, na który mapowane są wpisy historii i OCR). */
    val taskCode: String,
    val schedule: Schedule,
    val source: SourceRef,
)

public data class ServicePlan(
    val id: String,
    val source: SourceRef,
    /** Zakres zastosowania: wszystkie wymienione cechy muszą się zgadzać. */
    val scope: Map<ConfigAttribute, Set<String>>,
    val rules: List<ServiceRule>,
)

/** Czynność zatwierdzona przez użytkownika. Szkice OCR nie mogą tu trafiać. */
public data class PerformedTask(val taskCode: String, val date: LocalDate, val odometerKm: Int?)

public data class OdometerReading(val date: LocalDate, val km: Int)

/** Odczyt wskaźnika serwisowego z wyświetlacza auta — jedyne wiarygodne wejście dla planów zmiennych. */
public data class IndicatorReading(
    val taskCode: String,
    val date: LocalDate,
    val odometerKm: Int,
    val remainingKm: Int? = null,
    val remainingDays: Int? = null,
) {
    init {
        require(remainingKm != null || remainingDays != null) { "Indicator needs km or days" }
    }
}

/** Progi aplikacji (nie zalecenia producenta) — do pokazania w ustawieniach jako ustawienia aplikacji. */
public data class EngineSettings(
    val dueSoonKm: Int = 1500,
    val dueSoonDays: Int = 30,
    val odometerStaleAfterDays: Int = 90,
    val estimationMinSpanDays: Int = 30,
    val estimationWindowDays: Int = 365,
)

public data class EvaluationInput(
    val today: LocalDate,
    /** Data rozpoczęcia eksploatacji (pierwsza rejestracja). To nie jest rok modelowy. */
    val inServiceDate: LocalDate?,
    val rules: List<ServiceRule>,
    val history: List<PerformedTask> = emptyList(),
    val odometer: List<OdometerReading> = emptyList(),
    val indicators: List<IndicatorReading> = emptyList(),
    val settings: EngineSettings = EngineSettings(),
)

/** Cztery grupy pulpitu. */
public enum class Category { OVERDUE, DUE_SOON, NEEDS_DATA, NO_URGENCY }

/** Kody przyczyn i zastrzeżeń; UI tłumaczy je z zasobów (nb-NO/en). Silnik nie zawiera tekstów. */
public enum class Reason {
    /** „Brak potwierdzenia w dodanej dokumentacji” — termin wg planu minął, ale to nie dowód zaniedbania. */
    NO_CONFIRMATION_IN_DOCUMENTS,
    ODOMETER_MISSING,
    LAST_SERVICE_ODOMETER_MISSING,
    IN_SERVICE_DATE_MISSING,
    /** Termin wyznacza pojazd; potrzebny odczyt wskaźnika serwisowego. */
    VARIABLE_SCHEDULE,
    ODOMETER_STALE,
    /** Szacunek wskazuje, że próg km mógł już zostać przekroczony od ostatniego odczytu. */
    ODOMETER_UPDATE_REQUIRED,
    ODOMETER_INCONSISTENT,
    SOURCE_EXPIRED,
    FUTURE_ENTRIES_IGNORED,
}

public enum class Dimension { DISTANCE, TIME }

public enum class AnchorKind { LAST_CONFIRMED_TASK, VEHICLE_START, VEHICLE_INDICATOR }

public enum class IntervalKind { FIRST, REPEAT, UPPER_LIMIT, INDICATOR }

/** Podstawa obliczenia — wszystko, czego UI potrzebuje, by pokazać „z czego wynika ten termin”. */
public data class Basis(
    val anchorKind: AnchorKind,
    val anchorDate: LocalDate?,
    val anchorKm: Int?,
    val intervalKind: IntervalKind,
    val interval: Interval?,
    /** Odczyt przebiegu użyty jako „stan obecny” wraz z datą. */
    val odometerUsed: OdometerReading?,
    /** Wymiar, który zdecydował o grupie OVERDUE/DUE_SOON. */
    val decidedBy: Dimension?,
)

public data class TaskStatus(
    val rule: ServiceRule,
    val category: Category,
    val dueKm: Int?,
    val dueDate: LocalDate?,
    val remainingKm: Int?,
    val remainingDays: Long?,
    /**
     * SZACUNEK miesiąca osiągnięcia progu km wg średniego przebiegu. Celowo bez dnia;
     * UI musi oznaczać go jako szacunek.
     */
    val estimatedKmDueMonth: YearMonth?,
    val basis: Basis,
    val reasons: Set<Reason>,
)
