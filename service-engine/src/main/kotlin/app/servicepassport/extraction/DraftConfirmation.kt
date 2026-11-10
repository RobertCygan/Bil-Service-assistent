package app.servicepassport.extraction

import app.servicepassport.engine.PerformedTask
import java.time.LocalDate

/** Dane po sprawdzeniu i poprawkach użytkownika — jedyne wejście, z którego powstaje historia. */
public data class ReviewedDraft(val serviceDate: LocalDate?, val odometerKm: Int?, val taskCodes: List<String>)

public enum class ConfirmationError { DATE_MISSING, DATE_IN_FUTURE, ODOMETER_NEGATIVE, NO_TASKS }

public sealed interface ConfirmationResult {
    public data class Accepted(val tasks: List<PerformedTask>) : ConfirmationResult
    public data class Rejected(val errors: Set<ConfirmationError>) : ConfirmationResult
}

public object DraftConfirmation {
    /**
     * Zamienia zatwierdzony szkic na czynności dla silnika terminów. Zapis do bazy (jedna transakcja,
     * ochrona przed podwójnym zatwierdzeniem) należy do warstwy danych aplikacji.
     */
    public fun confirm(reviewed: ReviewedDraft, today: LocalDate): ConfirmationResult {
        val errors = sortedSetOf<ConfirmationError>()
        val date = reviewed.serviceDate
        if (date == null) errors += ConfirmationError.DATE_MISSING
        else if (date.isAfter(today)) errors += ConfirmationError.DATE_IN_FUTURE
        if (reviewed.odometerKm != null && reviewed.odometerKm < 0) errors += ConfirmationError.ODOMETER_NEGATIVE
        val codes = reviewed.taskCodes.filter { it.isNotBlank() }.distinct()
        if (codes.isEmpty()) errors += ConfirmationError.NO_TASKS
        if (errors.isNotEmpty() || date == null) return ConfirmationResult.Rejected(errors)
        return ConfirmationResult.Accepted(codes.map { PerformedTask(it, date, reviewed.odometerKm) })
    }
}

public enum class ImportRejection { EMPTY_FILE, TOO_LARGE, TOO_MANY_PAGES, UNSUPPORTED_TYPE }

/** Limity importu z Etapu 0: 20 MB i 20 stron na dokument. */
public object ImportLimits {
    public const val MAX_BYTES: Long = 20L * 1024 * 1024
    public const val MAX_PAGES: Int = 20
    public val MIME_TYPES: Set<String> = setOf("application/pdf", "image/jpeg", "image/png", "image/webp", "image/heic", "image/heif")

    public fun check(mimeType: String?, bytes: Long, pages: Int): ImportRejection? = when {
        mimeType?.lowercase() !in MIME_TYPES -> ImportRejection.UNSUPPORTED_TYPE
        bytes <= 0 || pages <= 0 -> ImportRejection.EMPTY_FILE
        bytes > MAX_BYTES -> ImportRejection.TOO_LARGE
        pages > MAX_PAGES -> ImportRejection.TOO_MANY_PAGES
        else -> null
    }
}

public enum class DuplicateKind {
    /** Ten sam plik (identyczny skrót SHA-256). */
    SAME_FILE,
    /** Inny plik, ale istnieje wpis z tą samą datą i przebiegiem — np. drugie zdjęcie tej samej faktury. */
    SAME_DATE_AND_ODOMETER,
}

public data class KnownEntry(val id: String, val sha256: String?, val date: LocalDate?, val odometerKm: Int?)

public data class Duplicate(val entryId: String, val kind: DuplicateKind)

public object DuplicateCheck {
    /** Ostrzeżenia, nie blokady: użytkownik decyduje, czy mimo to zapisać. */
    public fun find(sha256: String?, date: LocalDate?, odometerKm: Int?, known: List<KnownEntry>): List<Duplicate> =
        known.mapNotNull { e ->
            when {
                sha256 != null && e.sha256.equals(sha256, ignoreCase = true) -> Duplicate(e.id, DuplicateKind.SAME_FILE)
                date != null && odometerKm != null && e.date == date && e.odometerKm == odometerKm ->
                    Duplicate(e.id, DuplicateKind.SAME_DATE_AND_ODOMETER)
                else -> null
            }
        }
}
