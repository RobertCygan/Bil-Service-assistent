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
    val taskCode: String,
    val kind: ItemKind,
    val evidence: Evidence,
) {
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
 * Jedna czynność słownika. [servicePhrases] same oznaczają wykonanie pracy („oljeskift”),
 * [partPhrases] to nazwa części/materiału — wykonanie wymaga dodatkowo czasownika w linii.
 */
public data class TaskTerm(
    val taskCode: String,
    val servicePhrases: List<String>,
    val partPhrases: List<String> = emptyList(),
)
