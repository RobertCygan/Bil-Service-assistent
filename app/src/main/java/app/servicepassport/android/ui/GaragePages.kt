package app.servicepassport.android.ui

import app.servicepassport.android.backup.DataFiles
import app.servicepassport.android.documents.DocumentStore
import app.servicepassport.android.reminders.ReminderStore
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Alignment
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.servicepassport.android.BuildConfig
import app.servicepassport.android.R
import app.servicepassport.android.data.*
import java.time.LocalDate

@Composable internal fun Overview(state: GarageState, today: LocalDate, retry: () -> Unit, go: (String) -> Unit) {
    val snapshot = state.snapshot
    if (!state.loading && state.problem != StorageProblem.READ && snapshot != null) { CockpitOverview(snapshot, state.dashboard, today, go); return }
    Page {
        when {
            state.loading -> CircularProgressIndicator(Modifier.testTag("loading"))
            state.problem == StorageProblem.READ -> { Text(stringResource(R.string.read_error)); PrimaryButton(onClick = retry) { Text(stringResource(R.string.retry)) } }
            else -> {
                Image(painterResource(R.drawable.logo), contentDescription = null, modifier = Modifier.size(112.dp).align(Alignment.CenterHorizontally))
                Text(stringResource(R.string.welcome_title), style = MaterialTheme.typography.headlineMedium)
                Text(stringResource(R.string.welcome_body)); LanguageSelector()
                PrimaryButton(onClick = { go("vehicle") }, modifier = Modifier.fillMaxWidth().testTag("add_car")) { Text(stringResource(R.string.add_car)) }
                Text(stringResource(R.string.local_storage_note), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable internal fun History(state: GarageState, go: (String) -> Unit) = Page {
    Text(stringResource(R.string.history), style = MaterialTheme.typography.headlineMedium)
    val snapshot = state.snapshot; val locale = uiLocale()
    if (snapshot == null) { Text(stringResource(R.string.no_history_without_car)); return@Page }
    val scheme = MaterialTheme.colorScheme
    Text(snapshot.vehicle.displayName, color = scheme.onSurfaceVariant)
    PrimaryButton(onClick = { go("service/new") }, modifier = Modifier.fillMaxWidth().testTag("add_service")) { Text(stringResource(R.string.add_service)) }
    if (snapshot.events.isEmpty()) { Text(stringResource(R.string.history_empty_title), style = MaterialTheme.typography.titleMedium); Text(stringResource(R.string.history_empty_body), color = scheme.onSurfaceVariant) }
    snapshot.history.forEach { record ->
        Panel(Modifier.fillMaxWidth().testTag("history_entry"), onClick = { go("service/${record.event.id}") }) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(LocalDates.text(record.event.performedDay, locale), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Surface(color = scheme.secondaryContainer, contentColor = scheme.onSecondaryContainer, shape = MaterialTheme.shapes.extraSmall) {
                        Text(record.reading?.let { formattedKm(it.km, locale) + " km" } ?: stringResource(R.string.odometer_unknown), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                    }
                }
                record.orderedActions.forEach { Text(workLabel(WorkInput(it.actionCode, it.operationCode, it.customLabel))) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.confirmed_by_you), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    record.event.costMinor?.let { Text(formattedMoney(it, locale), style = MaterialTheme.typography.titleSmall) }
                    if (record.documents.isNotEmpty()) Text("  " + stringResource(R.string.documents_count, record.documents.size), style = MaterialTheme.typography.labelMedium, color = scheme.primary)
                }
            }
        }
    }
}

@Composable internal fun ServiceDetails(state: GarageState, id: String, go: (String) -> Unit, store: DocumentStore? = null, removeDocument: (String) -> Unit = {}, delete: (String, Long) -> Unit) = Page {
    val record = state.snapshot?.events?.firstOrNull { it.event.id == id }; val locale = uiLocale()
    if (record == null) { Text(stringResource(R.string.entry_not_found)); return@Page }
    var askDelete by remember { mutableStateOf(false) }
    Text(stringResource(R.string.service_details), style = MaterialTheme.typography.headlineMedium)
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Figure(stringResource(R.string.service_date), LocalDates.text(record.event.performedDay, locale), Modifier.weight(1f).fillMaxHeight())
        Figure(stringResource(R.string.current_odometer_short), record.reading?.let { formattedKm(it.km, locale) + " km" } ?: stringResource(R.string.odometer_unknown), Modifier.weight(1f).fillMaxHeight(), accent = Cockpit.cyan)
    }
    Group {
        Text(stringResource(R.string.completed_work), style = MaterialTheme.typography.titleMedium)
        record.orderedActions.forEach { work ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(8.dp).background(Cockpit.cyan, CircleShape))
                Text(workLabel(WorkInput(work.actionCode, work.operationCode, work.customLabel)), Modifier.testTag("confirmed_work"))
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Text(record.event.costMinor?.let { formattedMoney(it, locale) } ?: stringResource(R.string.cost_unknown), style = MaterialTheme.typography.titleMedium)
        if (record.event.note.isNotEmpty()) Text(record.event.note, Modifier.testTag("service_note"))
        Text(stringResource(R.string.confirmed_by_you), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    record.reading?.takeIf { it.exceptionReason.isNotEmpty() }?.let { NoteCard(it.exceptionReason) }
    if (record.documents.isNotEmpty()) {
        var removing by remember { mutableStateOf<String?>(null) }
        var noViewer by remember { mutableStateOf(false) }
        Text(stringResource(R.string.documents_title), style = MaterialTheme.typography.titleMedium)
        record.documents.sortedWith(compareBy({ it.createdAtUtc }, { it.id })).forEach { document ->
            Panel(Modifier.fillMaxWidth().testTag("service_document")) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.document_added, LocalDates.text(document.addedDay, locale)))
                    val present = remember(document.id, state.snapshot) { store?.exists(document) == true }
                    if (present) PrimaryButton(onClick = { noViewer = store?.view(document) != true }, modifier = Modifier.testTag("open_document")) { Text(stringResource(R.string.document_open)) }
                    else Text(stringResource(R.string.document_missing), color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = { removing = document.id }, enabled = !state.saving, modifier = Modifier.testTag("remove_document")) { Text(stringResource(R.string.document_remove)) }
                }
            }
        }
        if (noViewer) Text(stringResource(R.string.document_no_viewer), color = MaterialTheme.colorScheme.error)
        removing?.let { documentId ->
            AlertDialog(onDismissRequest = { removing = null }, title = { Text(stringResource(R.string.document_remove_title)) }, text = { Text(stringResource(R.string.document_remove_body)) }, confirmButton = { TextButton(onClick = { removing = null; removeDocument(documentId) }, modifier = Modifier.testTag("confirm_remove_document")) { Text(stringResource(R.string.document_remove)) } }, dismissButton = { TextButton(onClick = { removing = null }) { Text(stringResource(R.string.cancel)) } })
        }
    }
    ErrorPanel(state)
    PrimaryButton(onClick = { go("service/edit/$id") }, modifier = Modifier.fillMaxWidth().testTag("edit_service")) { Text(stringResource(R.string.edit_service)) }
    QuietButton(onClick = { askDelete = true }, enabled = !state.saving, modifier = Modifier.fillMaxWidth().testTag("delete_service")) { Text(stringResource(R.string.delete)) }
    if (askDelete) AlertDialog(onDismissRequest = { askDelete = false }, title = { Text(stringResource(R.string.delete_service_title)) }, text = { Text(stringResource(R.string.delete_service_body)) }, confirmButton = { TextButton(onClick = { askDelete = false; delete(id, record.event.revision) }, modifier = Modifier.testTag("confirm_delete")) { Text(stringResource(R.string.delete)) } }, dismissButton = { TextButton(onClick = { askDelete = false }) { Text(stringResource(R.string.cancel)) } })
}

@Composable internal fun Readings(state: GarageState, go: (String) -> Unit, select: (CurrentReadingInput, () -> Unit) -> Unit, delete: (String, Long) -> Unit) = Page {
    val snapshot = state.snapshot; val locale = uiLocale()
    var deleting by remember { mutableStateOf<OdometerReadingEntity?>(null) }
    var reviewingId by rememberSaveable { mutableStateOf<String?>(null) }
    Text(stringResource(R.string.reading_history), style = MaterialTheme.typography.headlineMedium)
    if (snapshot == null) { Text(stringResource(R.string.no_history_without_car)); return@Page }
    PrimaryButton(onClick = { go("reading/new") }, modifier = Modifier.testTag("add_reading")) { Text(stringResource(R.string.add_reading)) }
    if (snapshot.readings.isEmpty()) Text(stringResource(R.string.no_readings))
    ErrorPanel(state)
    snapshot.readings.sortedWith(compareByDescending<OdometerReadingEntity> { it.observedDay }.thenByDescending { it.createdAtUtc }).forEach { reading ->
        Panel(Modifier.fillMaxWidth().testTag("reading_entry")) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(formattedKm(reading.km, locale) + " km", style = MaterialTheme.typography.titleLarge)
            Text(LocalDates.text(reading.observedDay, locale))
            if (snapshot.currentReading?.id == reading.id) Text(stringResource(R.string.current_badge))
            if (reading.exceptionReason.isNotEmpty()) NoteCard(reading.exceptionReason)
            if (snapshot.currentReading?.id != reading.id) TextButton(onClick = {
                val warnings = ReadingConsistency.warnings(reading.observedDay, reading.km, snapshot.readings.filter { it.id != reading.id }, snapshot.currentReading, true)
                if (warnings.isEmpty()) select(CurrentReadingInput(reading.id, snapshot.vehicle.id, reading.revision)) {}
                else reviewingId = reading.id
            }, enabled = !state.saving, modifier = Modifier.testTag("select_current_${reading.id}")) { Text(stringResource(R.string.use_as_current)) }
            if (reading.eventId == null) {
                TextButton(onClick = { go("reading/${reading.id}") }) { Text(stringResource(R.string.edit)) }
                TextButton(onClick = { deleting = reading }, enabled = !state.saving) { Text(stringResource(R.string.delete)) }
            } else TextButton(onClick = { go("service/${reading.eventId}") }) { Text(stringResource(R.string.open_service)) }
        } }
    }
    deleting?.let { reading -> AlertDialog(onDismissRequest = { deleting = null }, title = { Text(stringResource(R.string.delete_reading_title)) }, text = { Text(stringResource(R.string.delete_reading_body)) }, confirmButton = { TextButton(onClick = { deleting = null; delete(reading.id, reading.revision) }, modifier = Modifier.testTag("confirm_delete")) { Text(stringResource(R.string.delete)) } }, dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.cancel)) } }) }
    snapshot.readings.firstOrNull { it.id == reviewingId }?.let { reading ->
        CurrentReadingReview(state, snapshot, reading, { reviewingId = null }) { input ->
            select(input) { reviewingId = null }
        }
    }
}

@Composable private fun CurrentReadingReview(
    state: GarageState,
    snapshot: GarageSnapshot,
    reading: OdometerReadingEntity,
    dismiss: () -> Unit,
    select: (CurrentReadingInput) -> Unit,
) {
    var reason by rememberSaveable(reading.id, reading.revision) { mutableStateOf(reading.exceptionReason) }
    var reviewedToken by rememberSaveable(reading.id, reading.revision) { mutableStateOf<String?>(null) }
    val warnings = ReadingConsistency.warnings(reading.observedDay, reading.km, snapshot.readings.filter { it.id != reading.id }, snapshot.currentReading, true)
    val token = ReadingConsistency.token(snapshot, reading.id, reading.observedDay, reading.km, true)
    AlertDialog(
        modifier = Modifier.testTag("current_reading_review"),
        onDismissRequest = { if (!state.saving) dismiss() },
        title = { Text(stringResource(R.string.use_as_current)) },
        text = {
            Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(LocalDates.text(reading.observedDay, uiLocale()) + " · " + formattedKm(reading.km, uiLocale()) + " km")
                OdometerReview(warnings, token, reason, reviewedToken, { reason = it; reviewedToken = null }, { reviewedToken = it })
                ErrorPanel(state)
            }
        },
        confirmButton = {
            TextButton(enabled = !state.saving, modifier = Modifier.testTag("confirm_current_reading"), onClick = {
                select(CurrentReadingInput(reading.id, snapshot.vehicle.id, reading.revision, reason, reviewedToken != null, reviewedToken))
            }) { Text(stringResource(if (state.saving) R.string.saving else R.string.use_as_current)) }
        },
        dismissButton = { TextButton(enabled = !state.saving, onClick = dismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable internal fun Settings(reminderStore: ReminderStore? = null, onRemindersChanged: () -> Unit = {}, dataFiles: DataFiles? = null, hasVehicle: Boolean = false) = Page {
    Text(stringResource(R.string.settings), style = MaterialTheme.typography.headlineMedium)
    Group { LanguageSelector() }
    if (reminderStore != null) Group { ReminderSettingsSection(reminderStore, onRemindersChanged) }
    Group {
        Text(stringResource(R.string.your_data), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.local_storage_note), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (dataFiles != null) DataSection(dataFiles, hasVehicle)
    }
    Group {
        Text(stringResource(R.string.data_sources_title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.data_sources_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    // Lets a tester say exactly which build a remark is about.
    Text(stringResource(R.string.app_version, BuildConfig.VERSION_NAME), style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("app_version"))
}
