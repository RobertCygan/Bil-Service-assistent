package app.servicepassport.extraction

import java.time.LocalDate

/** Ramka linii na stronie (piksele obrazu strony) — do pokazania fragmentu źródłowego. */
public data class Box(val left: Int, val top: Int, val right: Int, val bottom: Int)

public data class OcrLine(val text: String, val box: Box? = null)

/** Surowy wynik OCR jednej strony. Zapisywany bez zmian, niezależnie od szkicu i historii. */
public data class OcrPage(val index: Int, val lines: List<OcrLine>)

/** Skąd pochodzi odczytana wartość: strona, linia, ramka i oryginalny tekst. */
public data class Evidence(val page: Int, val line: Int, val box: Box?, val text: String)

/** Bez procentów pewności: rozpoznano / sprawdź / nie znaleziono. */
public enum class FieldState { RECOGNIZED, CHECK, NOT_FOUND }

public data class Candidate<T>(val value: T, val evidence: Evidence)

public data class Field<T>(val value: T?, val state: FieldState, val candidates: List<Candidate<T>>) {
    public val evidence: Evidence? get() = candidates.firstOrNull { it.value == value }?.evidence
}

/** Kwota w najmniejszej jednostce (øre/cent). [currency] = null, gdy dokument jej nie podaje. */
public data class Money(val minor: Long, val currency: String?)

public enum class ItemKind {
    /** Wykonana usługa — jedyny rodzaj zaznaczany domyślnie do zapisu. */
    PERFORMED,
    RECOMMENDED,
    QUOTE,
    /** Sama część/materiał bez śladu wykonania pracy. Zakup oleju to nie wymiana oleju. */
    PARTS_ONLY,
    UNCLEAR,
}

public data class DraftItem(
    /** Oryginalny tekst linii, nigdy nie tłumaczony ani nie poprawiany. */
    val originalText: String,
    /** Kod czynności ze wspólnego kontraktu, np. `engine_oil`. */
    val action: String,
    /** Operacja: `replace`, `inspect` albo `test`. Kontrola nie jest wymianą. */
    val operation: String,
    val kind: ItemKind,
    val evidence: Evidence,
) {
    /** Pełna para `czynność:operacja` — ten sam klucz co w regułach i w historii. */
    public val taskCode: String get() = "$action:$operation"

    public val selectedByDefault: Boolean get() = kind == ItemKind.PERFORMED
}

public enum class DraftWarning {
    /** VIN lub numer rejestracyjny w dokumencie nie zgadza się z pojazdem. */
    VEHICLE_MISMATCH,
    DOCUMENT_IS_QUOTE,
    /** Przebieg podany w milach; nie przeliczamy po cichu. */
    MILEAGE_IN_MILES,
    NO_READABLE_TEXT,
}

/** Szkic do sprawdzenia przez użytkownika. Sam w sobie niczego nie zmienia w historii. */
public data class DraftProposal(
    val serviceDate: Field<LocalDate>,
    val odometerKm: Field<Int>,
    val vin: Field<String>,
    val plate: Field<String>,
    val total: Field<Money>,
    val items: List<DraftItem>,
    val warnings: Set<DraftWarning>,
)

public data class VehicleIdentity(val vin: String? = null, val plate: String? = null)

/**
 * Jedna czynność słownika.
 *
 * [operationPhrases]: frazy, które same oznaczają wykonanie danej operacji („oljeskift” → replace,
 * „bremsekontroll” → inspect). [partPhrases]: nazwa części lub materiału — operację wskazuje dopiero
 * czasownik w tej samej linii („skiftet”, „kontrollert”, „testet”); sama nazwa to tylko zakup części.
 * [unless]: słowa, które wykluczają tę czynność w danej linii („olje” obok „girkasse” to nie olej silnikowy).
 * Pominięcie pozycji jest bezpieczniejsze niż przypisanie jej do złej czynności.
 */
public data class TaskTerm(
    val action: String,
    /** Operacje dozwolone dla tej czynności, w kolejności od domyślnej. */
    val operations: List<String>,
    val operationPhrases: Map<String, List<String>> = emptyMap(),
    val partPhrases: List<String> = emptyList(),
    val unless: List<String> = emptyList(),
) {
    init {
        require(operations.isNotEmpty()) { "Task needs at least one operation" }
        require(operationPhrases.keys.all { it in operations }) { "Phrases for an operation the task does not allow" }
    }
}
