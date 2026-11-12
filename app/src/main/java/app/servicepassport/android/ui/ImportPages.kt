package app.servicepassport.android.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.servicepassport.android.R
import android.net.Uri
import app.servicepassport.android.data.LocalDates
import app.servicepassport.android.data.ServiceRecord
import app.servicepassport.android.data.WorkInput
import app.servicepassport.android.documents.*
import app.servicepassport.extraction.*
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Holds the draft being reviewed and the prefill handed to the service form. It is kept by the view model and reads
 * documents in the view model's scope, so turning the phone or switching the theme neither loses the review nor stops
 * a reading that is under way. Closing the app loses it; the private copy of the document stays and is offered again.
 */
class ImportSession(private val store: DocumentStore?, private val scope: CoroutineScope) {
    var analysis by mutableStateOf<DocumentAnalysis?>(null)
    var prefill by mutableStateOf<ServicePrefill?>(null)
    /** The private copy of the document under review; null when it could not be copied. */
    var staged by mutableStateOf<StagedDocument?>(null)
    /** The copy to save with the entry, when the driver chose to keep it. */
    var attachment by mutableStateOf<StagedDocument?>(null)
    /** The review screen as last seen, so that coming back to it (also after the app was closed) keeps the choices. */
    var savedReview by mutableStateOf<SavedReview?>(null)
    /** A document is being read. */
    var busy by mutableStateOf(false); private set
    /** A review interrupted by closing the app can be picked up again: the private copy is still there. */
    var pending by mutableStateOf<StagedDocument?>(null)

    private fun start(work: suspend () -> Unit) {
        if (busy) return
        busy = true; pending = null; reset()
        scope.launch { try { work() } finally { busy = false } }
    }

    fun read(analyzer: DocumentAnalyzer, uri: Uri, vehicle: VehicleIdentity, today: LocalDate) = start {
        val result = analyzer.analyse(uri, vehicle, today)
        // A private copy is made only for a document that could be read; it replaces an earlier unfinished one.
        // A file that could not be read changes nothing: a document that was waiting is offered again.
        if (result is DocumentAnalysis.Ready) staged = store?.stage(uri) else pending = store?.pending()
        analysis = result
    }

    fun resume(analyzer: DocumentAnalyzer, document: StagedDocument, vehicle: VehicleIdentity, today: LocalDate) = start {
        val files = requireNotNull(store)
        staged = document
        savedReview = files.loadReview(document)?.let(ReviewPersistence::decode)
        analysis = analyzer.analyse(files.stagedUri(document), vehicle, today)
    }

    /** Looks for a document whose review was not finished. */
    fun offerPending() {
        if (analysis != null || busy) return
        scope.launch { val waiting = store?.pending(); if (analysis == null && !busy) pending = waiting }
    }

    fun discardPending() { pending = null; store?.discardStaged() }

    /**
     * The service form keeps its fields when the app is closed in the background. The document that was to be saved
     * with the entry is found again by its id, so the entry is not saved without it.
     */
    fun reattach(id: String?) {
        if (id == null || attachment != null) return
        scope.launch { store?.pending()?.takeIf { it.id == id }?.let { if (attachment == null) attachment = it } }
    }
    /** Forgets what is on screen; the private copy stays until [clear]. */
    fun reset() { analysis = null; prefill = null; staged = null; attachment = null; savedReview = null }
    fun clear() { reset(); store?.discardStaged() }
}

private fun problemMessage(problem: AnalysisProblem): Int = when (problem) {
    AnalysisProblem.UNSUPPORTED_TYPE -> R.string.import_unsupported
    AnalysisProblem.EMPTY_FILE -> R.string.import_empty
    AnalysisProblem.TOO_LARGE -> R.string.import_too_large
    AnalysisProblem.TOO_MANY_PAGES -> R.string.import_too_many_pages
    AnalysisProblem.UNREADABLE -> R.string.import_unreadable
}
private fun warningMessage(warning: DraftWarning): Int = when (warning) {
    DraftWarning.VEHICLE_MISMATCH -> R.string.import_warning_other_vehicle
    DraftWarning.DOCUMENT_IS_QUOTE -> R.string.import_warning_quote
    DraftWarning.MILEAGE_IN_MILES -> R.string.import_warning_miles
    DraftWarning.NO_READABLE_TEXT -> R.string.import_warning_no_text
}
private fun kindMessage(kind: ItemKind): Int = when (kind) {
    ItemKind.PERFORMED -> R.string.import_kind_performed
    ItemKind.RECOMMENDED -> R.string.import_kind_recommended
    ItemKind.QUOTE -> R.string.import_kind_quote
    ItemKind.PARTS_ONLY -> R.string.import_kind_parts
    ItemKind.UNCLEAR -> R.string.import_kind_unclear
}
private fun stateMessage(state: FieldState): Int = when (state) {
    FieldState.RECOGNIZED -> R.string.import_state_recognized
    FieldState.CHECK -> R.string.import_state_check
    FieldState.NOT_FOUND -> R.string.import_state_not_found
}

/** Pick a document, read it on the phone, review what was found, then continue to the ordinary service form. */
@Composable internal fun ImportPage(state: GarageState, session: ImportSession, analyzer: DocumentAnalyzer?, store: DocumentStore?, today: LocalDate, go: (String) -> Unit) = Page {
    Text(stringResource(R.string.import_document), style = MaterialTheme.typography.headlineMedium)
    val snapshot = state.snapshot
    if (snapshot == null || analyzer == null) { Text(stringResource(R.string.no_history_without_car)); return@Page }
    val scope = rememberCoroutineScope()
    val busy = session.busy
    val vehicle = VehicleIdentity(snapshot.vehicle.vin, snapshot.vehicle.registrationNumber)
    fun read(uri: Uri) = session.read(analyzer, uri, vehicle, today)
    LaunchedEffect(Unit) { session.offerPending() }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) read(uri) }
    var noCamera by remember { mutableStateOf(false) }
    // The photo always lands in the same private file. Nothing has to be remembered while the camera is open, so the
    // photo is read even when Android closed this app in the meantime and has just started it again.
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { taken -> if (taken && store != null) read(store.cameraUri()) }
    NoteCard(stringResource(R.string.import_explanation))
    session.pending?.takeIf { !busy && session.analysis !is DocumentAnalysis.Ready }?.let { document ->
        Panel(Modifier.fillMaxWidth().testTag("import_pending")) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.import_pending))
                PrimaryButton(onClick = { session.resume(analyzer, document, vehicle, today) }, modifier = Modifier.testTag("import_resume")) { Text(stringResource(R.string.import_resume)) }
                TextButton(onClick = { session.discardPending() }) { Text(stringResource(R.string.import_discard)) }
            }
        }
    }
    PrimaryButton(onClick = { pick.launch(arrayOf("image/*", "application/pdf")) }, enabled = !busy, modifier = Modifier.fillMaxWidth().testTag("pick_document")) { Text(stringResource(R.string.import_pick)) }
    if (store != null) QuietButton(enabled = !busy, modifier = Modifier.fillMaxWidth().testTag("take_photo"), onClick = {
        // Nothing is given up until a photo has actually been taken and read.
        noCamera = false
        try { camera.launch(store.cameraTarget()) } catch (_: Exception) { noCamera = true }
    }) { Text(stringResource(R.string.import_take_photo)) }
    if (noCamera) Text(stringResource(R.string.import_no_camera), color = MaterialTheme.colorScheme.error)
    if (busy) { CircularProgressIndicator(); Text(stringResource(R.string.import_reading)) }
    when (val analysis = session.analysis) {
        null -> Unit
        is DocumentAnalysis.Failed -> Text(stringResource(problemMessage(analysis.problem)), color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("import_problem"))
        is DocumentAnalysis.Ready -> {
            val staged = session.staged
            val fingerprint = remember(analysis) { ReviewPersistence.fingerprint(analysis.draft) }
            val restored = remember(analysis) { session.savedReview?.let { saved -> ReviewPersistence.restore(analysis.draft, saved)?.let { it to saved.keepDocument } } }
            DraftReview(analysis.draft, today, snapshot.events, staged, store != null, restored?.first, restored?.second ?: true, onChanged = { choice, keep ->
                val current = SavedReview(fingerprint, choice, keep)
                session.savedReview = current
                if (staged != null && store != null) scope.launch { store.saveReview(staged, ReviewPersistence.encode(current)) }
            }) { prefill, keep ->
                session.prefill = prefill; session.attachment = staged.takeIf { keep }; go("service/new")
            }
        }
    }
}

@Composable private fun DraftReview(
    draft: DraftProposal, today: LocalDate, history: List<ServiceRecord>, staged: StagedDocument?, canKeep: Boolean,
    startChoice: ReviewChoice?, startKeep: Boolean, onChanged: (ReviewChoice, Boolean) -> Unit, onContinue: (ServicePrefill, Boolean) -> Unit,
) {
    var keep by remember(draft) { mutableStateOf(startKeep) }
    val locale = uiLocale()
    var choice by remember(draft) { mutableStateOf(startChoice ?: ImportReview.initialChoice(draft)) }
    LaunchedEffect(choice, keep) { onChanged(choice, keep) }
    var errors by remember(draft) { mutableStateOf<Set<ConfirmationError>>(emptySet()) }
    HorizontalDivider()
    Text(stringResource(R.string.import_review_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.testTag("import_review"))
    Text(stringResource(R.string.import_review_help), style = MaterialTheme.typography.bodySmall)

    draft.warnings.forEach { warning ->
        NoteCard(stringResource(warningMessage(warning)))
        if (warning in DraftConfirmation.BLOCKING_WARNINGS) Row {
            Checkbox(checked = warning in choice.acknowledged, modifier = Modifier.testTag("ack_${warning.name}"), onCheckedChange = { on -> choice = choice.copy(acknowledged = if (on) choice.acknowledged + warning else choice.acknowledged - warning) })
            Text(stringResource(R.string.import_acknowledge), Modifier.padding(top = 12.dp))
        }
    }

    Text(stringResource(R.string.service_date), style = MaterialTheme.typography.titleMedium)
    Text(stringResource(stateMessage(draft.serviceDate.state)), style = MaterialTheme.typography.labelMedium)
    draft.serviceDate.candidates.forEach { candidate ->
        Row {
            RadioButton(selected = choice.date == candidate.value, onClick = { choice = choice.copy(date = candidate.value) })
            Column(Modifier.padding(top = 12.dp)) {
                Text(LocalDates.text(candidate.value.toEpochDay(), locale))
                Text(stringResource(R.string.import_source_line, candidate.evidence.page + 1, candidate.evidence.text.trim()), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
    if (draft.serviceDate.candidates.isNotEmpty()) TextButton(onClick = { choice = choice.copy(date = null) }) { Text(stringResource(R.string.import_enter_later)) }

    Text(stringResource(R.string.import_odometer), style = MaterialTheme.typography.titleMedium)
    Text(stringResource(stateMessage(draft.odometerKm.state)), style = MaterialTheme.typography.labelMedium)
    draft.odometerKm.candidates.forEach { candidate ->
        Row {
            RadioButton(selected = choice.odometerKm == candidate.value, onClick = { choice = choice.copy(odometerKm = candidate.value) })
            Column(Modifier.padding(top = 12.dp)) {
                Text(formattedKm(candidate.value.toLong(), locale) + " km")
                Text(stringResource(R.string.import_source_line, candidate.evidence.page + 1, candidate.evidence.text.trim()), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
    if (draft.odometerKm.candidates.isNotEmpty()) TextButton(onClick = { choice = choice.copy(odometerKm = null) }) { Text(stringResource(R.string.import_enter_later)) }

    Text(stringResource(R.string.completed_work), style = MaterialTheme.typography.titleMedium)
    if (draft.items.isEmpty()) Text(stringResource(R.string.import_no_items))
    draft.items.forEachIndexed { index, item ->
        val task = ImportReview.contractTask(item)
        Panel(Modifier.fillMaxWidth().testTag("import_item")) {
            Row(Modifier.padding(8.dp)) {
                Checkbox(checked = index in choice.selectedItems, enabled = task != null, onCheckedChange = { on -> choice = choice.copy(selectedItems = if (on) choice.selectedItems + index else choice.selectedItems - index) })
                Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(workLabel(WorkInput(item.action, item.operation)), style = MaterialTheme.typography.titleSmall)
                    Text(stringResource(kindMessage(item.kind)), style = MaterialTheme.typography.labelMedium)
                    Text(stringResource(R.string.import_source_line, item.evidence.page + 1, item.originalText.trim()), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }

    draft.total.value?.let { total ->
        Text(stringResource(R.string.cost_nok), style = MaterialTheme.typography.titleMedium)
        if (ImportReview.costAllowed(draft)) Row {
            Checkbox(checked = choice.includeCost, onCheckedChange = { choice = choice.copy(includeCost = it) }, modifier = Modifier.testTag("import_cost"))
            Text(formattedMoney(total.minor, locale), Modifier.padding(top = 12.dp))
        } else NoteCard(stringResource(R.string.import_cost_other_currency))
    }

    val duplicates = ImportReview.duplicates(history, staged?.sha256, choice)
    if (DuplicateKind.SAME_FILE in duplicates) NoteCard(stringResource(R.string.import_duplicate_file))
    if (DuplicateKind.SAME_DATE_AND_ODOMETER in duplicates) NoteCard(stringResource(R.string.import_duplicate_entry))

    if (canKeep) {
        if (staged == null) NoteCard(stringResource(R.string.import_copy_failed))
        else Row {
            Checkbox(checked = keep, onCheckedChange = { keep = it }, modifier = Modifier.testTag("import_keep"))
            Column(Modifier.padding(top = 12.dp)) {
                Text(stringResource(R.string.import_keep_document))
                Text(stringResource(R.string.import_keep_help), style = MaterialTheme.typography.bodySmall)
            }
        }
    }

    if (ConfirmationError.WARNING_NOT_ACKNOWLEDGED in errors) Text(stringResource(R.string.import_error_acknowledge), color = MaterialTheme.colorScheme.error)
    if (ConfirmationError.NO_TASKS in errors) Text(stringResource(R.string.import_error_no_items), color = MaterialTheme.colorScheme.error)
    if ((errors - ConfirmationError.WARNING_NOT_ACKNOWLEDGED - ConfirmationError.NO_TASKS).isNotEmpty()) Text(stringResource(R.string.import_error_values), color = MaterialTheme.colorScheme.error)
    PrimaryButton(modifier = Modifier.fillMaxWidth().testTag("import_continue"), onClick = {
        when (val result = ImportReview.prefill(draft, choice, today)) {
            is ReviewResult.Ready -> { errors = emptySet(); onContinue(result.prefill, keep && staged != null) }
            is ReviewResult.Blocked -> errors = result.errors
        }
    }) { Text(stringResource(R.string.import_continue)) }
    Text(stringResource(R.string.import_continue_help), style = MaterialTheme.typography.bodySmall)
}
