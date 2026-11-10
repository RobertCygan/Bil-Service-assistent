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
import kotlinx.coroutines.launch

/** Holds the draft being reviewed and the prefill handed to the service form. Lives only while the app is open. */
class ImportSession(private val store: DocumentStore? = null) {
    var analysis by mutableStateOf<DocumentAnalysis?>(null)
    var prefill by mutableStateOf<ServicePrefill?>(null)
    /** The private copy of the document under review; null when it could not be copied. */
    var staged by mutableStateOf<StagedDocument?>(null)
    /** The copy to save with the entry, when the driver chose to keep it. */
    var attachment by mutableStateOf<StagedDocument?>(null)
    fun clear() { analysis = null; prefill = null; staged = null; attachment = null; store?.discardStaged() }
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
    var busy by remember { mutableStateOf(false) }
    fun read(uri: Uri, fresh: Boolean) {
        busy = true
        if (fresh) session.clear()
        scope.launch {
            try {
                val analysis = analyzer.analyse(uri, VehicleIdentity(snapshot.vehicle.vin, snapshot.vehicle.registrationNumber), today)
                // The private copy is made only for a document that could be read; it is discarded unless an entry is saved with it.
                session.staged = if (analysis is DocumentAnalysis.Ready) store?.stage(uri) else null
                session.analysis = analysis
            } finally { busy = false }
        }
    }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) read(uri, fresh = true) }
    var photo by remember { mutableStateOf<Uri?>(null) }
    var noCamera by remember { mutableStateOf(false) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { taken -> photo?.takeIf { taken }?.let { read(it, fresh = false) } }
    NoteCard(stringResource(R.string.import_explanation))
    Button(onClick = { pick.launch(arrayOf("image/*", "application/pdf")) }, enabled = !busy, modifier = Modifier.fillMaxWidth().testTag("pick_document")) { Text(stringResource(R.string.import_pick)) }
    if (store != null) OutlinedButton(enabled = !busy, modifier = Modifier.fillMaxWidth().testTag("take_photo"), onClick = {
        session.clear(); noCamera = false
        try { store.cameraTarget().also { photo = it; camera.launch(it) } } catch (_: Exception) { noCamera = true }
    }) { Text(stringResource(R.string.import_take_photo)) }
    if (noCamera) Text(stringResource(R.string.import_no_camera), color = MaterialTheme.colorScheme.error)
    if (busy) { CircularProgressIndicator(); Text(stringResource(R.string.import_reading)) }
    when (val analysis = session.analysis) {
        null -> Unit
        is DocumentAnalysis.Failed -> Text(stringResource(problemMessage(analysis.problem)), color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("import_problem"))
        is DocumentAnalysis.Ready -> DraftReview(analysis.draft, today, snapshot.events, session.staged, store != null) { prefill, keep ->
            session.prefill = prefill; session.attachment = session.staged.takeIf { keep }; go("service/new")
        }
    }
}

@Composable private fun DraftReview(draft: DraftProposal, today: LocalDate, history: List<ServiceRecord>, staged: StagedDocument?, canKeep: Boolean, onContinue: (ServicePrefill, Boolean) -> Unit) {
    var keep by remember(draft) { mutableStateOf(true) }
    val locale = uiLocale()
    var choice by remember(draft) { mutableStateOf(ImportReview.initialChoice(draft)) }
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
        OutlinedCard(Modifier.fillMaxWidth().testTag("import_item")) {
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
    Button(modifier = Modifier.fillMaxWidth().testTag("import_continue"), onClick = {
        when (val result = ImportReview.prefill(draft, choice, today)) {
            is ReviewResult.Ready -> { errors = emptySet(); onContinue(result.prefill, keep && staged != null) }
            is ReviewResult.Blocked -> errors = result.errors
        }
    }) { Text(stringResource(R.string.import_continue)) }
    Text(stringResource(R.string.import_continue_help), style = MaterialTheme.typography.bodySmall)
}
