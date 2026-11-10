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
