package app.servicepassport.android.documents

import app.servicepassport.android.data.ServiceRecord
import app.servicepassport.android.data.WorkInput
import app.servicepassport.contract.ServiceTask
import app.servicepassport.extraction.*
import java.time.LocalDate

/** Values carried from a reviewed document into the ordinary service form. Nothing here is saved yet. */
data class ServicePrefill(val day: Long?, val km: Long?, val work: List<WorkInput>, val costMinor: Long?)

/** What the driver chose on the review screen. */
data class ReviewChoice(
    val date: LocalDate?,
    val odometerKm: Int?,
    /** Indices into [DraftProposal.items]. */
    val selectedItems: Set<Int>,
    val includeCost: Boolean,
    val acknowledged: Set<DraftWarning>,
)

sealed interface ReviewResult {
    data class Ready(val prefill: ServicePrefill) : ReviewResult
    data class Blocked(val errors: Set<ConfirmationError>) : ReviewResult
}

/**
 * Review rules between the extracted draft and the service form.
 * The draft never becomes history here: the result only fills the form, where the driver saves explicitly.
 */
object ImportReview {
    /** Starting point: only what was recognised with confidence is preselected. */
    fun initialChoice(draft: DraftProposal): ReviewChoice {
        val seen = mutableSetOf<String>()
        val selected = draft.items.withIndex().filter { (_, item) -> item.selectedByDefault && contractTask(item) != null && seen.add(item.taskCode) }.map { it.index }.toSet()
        return ReviewChoice(
            date = draft.serviceDate.value.takeIf { draft.serviceDate.state == FieldState.RECOGNIZED },
            odometerKm = draft.odometerKm.value.takeIf { draft.odometerKm.state == FieldState.RECOGNIZED },
            selectedItems = selected,
            includeCost = costAllowed(draft) && draft.total.state == FieldState.RECOGNIZED,
            acknowledged = emptySet(),
        )
    }

    /** History stores NOK only. Another or unknown currency can never be carried over. */
    fun costAllowed(draft: DraftProposal): Boolean = draft.total.value?.currency == "NOK"

    /** Only tasks of the shared contract can be offered; anything else stays visible but cannot be selected. */
    fun contractTask(item: DraftItem): ServiceTask? = ServiceTask.fromTaskCode(item.taskCode)

    /**
     * Signs that this document may already be in the history. A warning only: two visits on one day are possible,
     * so the driver decides.
     */
    fun duplicates(history: List<ServiceRecord>, sha256: String?, choice: ReviewChoice): Set<DuplicateKind> {
        val known = history.flatMap { record ->
            val date = LocalDate.ofEpochDay(record.event.performedDay)
            val km = record.reading?.km?.toInt()
            listOf(KnownEntry(record.event.id, null, date, km)) + record.documents.map { KnownEntry(record.event.id, it.sha256, date, km) }
        }
        return DuplicateCheck.find(sha256, choice.date, choice.odometerKm, known).map { it.kind }.toSet()
    }

    fun prefill(draft: DraftProposal, choice: ReviewChoice, today: LocalDate): ReviewResult {
        val tasks = choice.selectedItems.sorted().mapNotNull { draft.items.getOrNull(it) }.mapNotNull(::contractTask).distinct()
        val cost = draft.total.value?.takeIf { choice.includeCost && costAllowed(draft) }
        val reviewed = ReviewedDraft(choice.date, choice.odometerKm, tasks.map { it.taskCode }, cost)
        val errors = when (val result = DraftConfirmation.confirm(reviewed, today, draft.warnings, choice.acknowledged)) {
            is ConfirmationResult.Accepted -> emptySet()
            // A missing date is allowed here: the form shows an empty date field that must be filled in before saving.
            is ConfirmationResult.Rejected -> result.errors - ConfirmationError.DATE_MISSING
        }
        if (errors.isNotEmpty()) return ReviewResult.Blocked(errors)
        return ReviewResult.Ready(ServicePrefill(
            day = choice.date?.toEpochDay(),
            km = choice.odometerKm?.toLong(),
            work = tasks.map { WorkInput(it.action.code, it.operation.code) },
            costMinor = cost?.minor,
        ))
    }
}

/** The review screen as the driver left it, so that closing the app does not lose the choices. */
data class SavedReview(val fingerprint: String, val choice: ReviewChoice, val keepDocument: Boolean)

/**
 * Saved choices point into one particular draft (item numbers, candidate values). They are used again only when the
 * document reads exactly the same way; otherwise the review starts from the cautious defaults.
 */
object ReviewPersistence {
    private const val VERSION = "1"

    fun fingerprint(draft: DraftProposal): String {
        val text = buildString {
            draft.serviceDate.candidates.forEach { append("d:").append(it.value).append('\n') }
            draft.odometerKm.candidates.forEach { append("k:").append(it.value).append('\n') }
            draft.items.forEach { append("i:").append(it.action).append(':').append(it.operation).append(':').append(it.kind).append(':').append(it.originalText).append('\n') }
            append("t:").append(draft.total.value?.minor).append(':').append(draft.total.value?.currency).append('\n')
            draft.warnings.map { it.name }.sorted().forEach { append("w:").append(it).append('\n') }
        }
        return java.security.MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    fun encode(saved: SavedReview): String = listOf(
        VERSION, saved.fingerprint, saved.choice.date?.toString().orEmpty(), saved.choice.odometerKm?.toString().orEmpty(),
        saved.choice.selectedItems.sorted().joinToString(","), if (saved.choice.includeCost) "1" else "0",
        saved.choice.acknowledged.map { it.name }.sorted().joinToString(","), if (saved.keepDocument) "1" else "0",
    ).joinToString("|")

    /** Null for anything that is not exactly what [encode] writes. */
    fun decode(text: String): SavedReview? = try {
        val parts = text.trim().split('|')
        fun flag(value: String) = when (value) { "1" -> true; "0" -> false; else -> throw IllegalArgumentException() }
        require(parts.size == 8 && parts[0] == VERSION && Regex("[0-9a-f]{64}").matches(parts[1]))
        SavedReview(
            parts[1],
            ReviewChoice(
                date = parts[2].takeIf { it.isNotEmpty() }?.let(LocalDate::parse),
                odometerKm = parts[3].takeIf { it.isNotEmpty() }?.toInt(),
                selectedItems = parts[4].split(',').filter { it.isNotEmpty() }.map { it.toInt().also { index -> require(index >= 0) } }.toSet(),
                includeCost = flag(parts[5]),
                acknowledged = parts[6].split(',').filter { it.isNotEmpty() }.map { DraftWarning.valueOf(it) }.toSet(),
            ),
            flag(parts[7]),
        )
    } catch (_: Exception) { null }

    /** The saved choice, if it belongs to this draft and every part of it is still something the draft offers. */
    fun restore(draft: DraftProposal, saved: SavedReview): ReviewChoice? {
        val choice = saved.choice
        val valid = saved.fingerprint == fingerprint(draft) &&
            (choice.date == null || draft.serviceDate.candidates.any { it.value == choice.date }) &&
            (choice.odometerKm == null || draft.odometerKm.candidates.any { it.value == choice.odometerKm }) &&
            choice.selectedItems.all { index -> draft.items.getOrNull(index)?.let(ImportReview::contractTask) != null } &&
            (!choice.includeCost || ImportReview.costAllowed(draft)) &&
            draft.warnings.containsAll(choice.acknowledged)
        return choice.takeIf { valid }
    }
}
