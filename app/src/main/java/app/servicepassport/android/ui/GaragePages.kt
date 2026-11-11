package app.servicepassport.android.ui

import app.servicepassport.android.backup.DataFiles
import app.servicepassport.android.documents.DocumentStore
import app.servicepassport.android.reminders.ReminderStore
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.servicepassport.android.BuildConfig
import app.servicepassport.android.R
import app.servicepassport.android.dashboard.DashboardState
import app.servicepassport.android.data.*
import app.servicepassport.contract.AppPolicy
import java.time.LocalDate

@Composable internal fun Overview(state: GarageState, today: LocalDate, retry: () -> Unit, go: (String) -> Unit) = Page {
    when {
        state.loading -> CircularProgressIndicator(Modifier.testTag("loading"))
        state.problem == StorageProblem.READ -> { Text(stringResource(R.string.read_error)); Button(onClick = retry) { Text(stringResource(R.string.retry)) } }
        state.profile == null -> {
            Text(stringResource(R.string.welcome_title), style = MaterialTheme.typography.headlineMedium)
            Text(stringResource(R.string.welcome_body)); LanguageSelector()
            Button(onClick = { go("vehicle") }, modifier = Modifier.fillMaxWidth().testTag("add_car")) { Text(stringResource(R.string.add_car)) }
            Text(stringResource(R.string.local_storage_note), style = MaterialTheme.typography.bodySmall)
        }
        else -> {
            val snapshot = requireNotNull(state.snapshot)
            val dashboard = state.dashboard
            HeroCard(snapshot, dashboard, today, go)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.service_overview), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = { go("dashboard") }, modifier = Modifier.testTag("open_dashboard")) { Text(stringResource(R.string.see_all)) }
            }
            if (dashboard != null) {
                if (dashboard.calculationUnavailable) NoteCard(stringResource(R.string.dashboard_unavailable)) else {
                    DashboardCounts(dashboard)
                    if (dashboard.tasks.isEmpty()) NoteCard(stringResource(R.string.no_intervals_yet))
                    dashboard.tasks.sortedBy { it.category.ordinal }.take(3).forEach { task -> TaskCard(task, compact = true) { go("forecast/${android.net.Uri.encode(task.id)}") } }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ActionTile(stringResource(R.string.add_service), Icons.Default.Add, primary = true, modifier = Modifier.weight(1f).testTag("add_service")) { go("service/new") }
                ActionTile(stringResource(R.string.import_document), Icons.Default.Search, primary = false, modifier = Modifier.weight(1f).testTag("import_document")) { go("import") }
            }
            if (dashboard != null && !dashboard.calculationUnavailable) CatalogNotice(dashboard, go)
        }
    }
}

/**
 * The car and its mileage around the nearest service. Side by side when there is room; one above the other on a narrow
 * screen or with large text, so that no word or number is broken or cut off.
 */
@Composable private fun HeroCard(snapshot: GarageSnapshot, dashboard: DashboardState?, today: LocalDate, go: (String) -> Unit) {
    val colors = LocalAppColors.current; val scheme = MaterialTheme.colorScheme; val locale = uiLocale(); val vehicle = snapshot.vehicle
    val carTile: @Composable (Modifier) -> Unit = { modifier ->
        Surface(color = colors.hero, contentColor = colors.onHero, shape = MaterialTheme.shapes.medium, modifier = modifier) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                FittingText(vehicle.displayName, MaterialTheme.typography.titleLarge, colors.onHero, 16.sp, Modifier.testTag("profile_name"))
                val model = listOfNotNull(vehicle.make, vehicle.model).joinToString(" ")
                if (model.isNotEmpty()) Text(model, color = colors.onHeroMuted)
                vehicle.modelYear?.let { Text(stringResource(R.string.model_year_value, it.toString()), color = colors.onHeroMuted, style = MaterialTheme.typography.bodyMedium) }
                vehicle.registrationNumber?.let { Text(it, style = MaterialTheme.typography.labelLarge) }
                Spacer(Modifier.weight(1f, fill = false).heightIn(min = 8.dp))
                Text(stringResource(R.string.edit_car), style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.testTag("edit_car").minimumInteractiveComponentSize().clip(MaterialTheme.shapes.extraSmall).clickable(role = Role.Button) { go("vehicle") }.padding(vertical = 10.dp))
            }
        }
    }
    val odometerTile: @Composable (Modifier) -> Unit = { modifier ->
        Surface(color = scheme.surfaceContainerLow, shape = MaterialTheme.shapes.medium, border = BorderStroke(1.dp, scheme.outlineVariant), modifier = modifier) {
            // Three separate groups: the most recent recorded service, the selected current reading, the nearest distance limit.
            Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // The mileage of the most recent entry only; an older entry's mileage would not be the "last service".
                val previous = snapshot.history.firstOrNull()?.reading
                OdometerGroup(stringResource(R.string.previous_service_km)) {
                    FittingText(previous?.let { formattedKm(it.km, locale) + " km" } ?: stringResource(R.string.no_value), MaterialTheme.typography.titleMedium, scheme.onSurface, 12.sp)
                }
                HorizontalDivider(color = scheme.outlineVariant)
                OdometerGroup(stringResource(R.string.current_odometer)) {
                    val reading = snapshot.currentReading
                    if (reading == null) Text(stringResource(R.string.odometer_missing), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("no_current_reading")) else {
                        FittingText(formattedKm(reading.km, locale) + " km", MaterialTheme.typography.headlineSmall, scheme.onSurface, 14.sp, Modifier.testTag("current_km"))
                        Text(LocalDates.text(reading.observedDay, locale), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, modifier = Modifier.testTag("current_reading_date"))
                    }
                }
                HorizontalDivider(color = scheme.outlineVariant)
                val next = dashboard?.takeIf { !it.calculationUnavailable }?.tasks?.filter { it.dueKm != null }?.minByOrNull { it.dueKm!! }
                OdometerGroup(stringResource(R.string.next_limit_km)) {
                    FittingText(next?.dueKm?.let { formattedKm(it.toLong(), locale) + " km" } ?: stringResource(R.string.no_value), MaterialTheme.typography.titleMedium, next?.let { colors.status(it.category).accent } ?: scheme.onSurface, 12.sp)
                    next?.let { Text(workLabel(WorkInput(it.task.action.code, it.task.operation.code)), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                }
            }
        }
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        // Room is judged in text units: large text needs more width for the same words.
        if (maxWidth / LocalDensity.current.fontScale >= 300.dp) {
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                carTile(Modifier.weight(1f).fillMaxHeight()); odometerTile(Modifier.weight(1f).fillMaxHeight())
            }
        } else Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { carTile(Modifier.fillMaxWidth()); odometerTile(Modifier.fillMaxWidth()) }
    }
    snapshot.currentReading?.let { reading ->
        if (today.toEpochDay() - reading.observedDay > AppPolicy.ODOMETER_STALE_AFTER_DAYS) Text(stringResource(R.string.odometer_stale), style = MaterialTheme.typography.bodySmall)
        if (reading.exceptionReason.isNotEmpty()) Text(stringResource(R.string.exception_recorded), style = MaterialTheme.typography.bodySmall)
    }
    val update = stringResource(R.string.update_odometer); val readings = stringResource(R.string.reading_history)
    val measurer = rememberTextMeasurer(); val labelStyle = MaterialTheme.typography.labelLarge
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        // Next to each other only when both labels fit in full; a label is never shortened.
        val widest = with(LocalDensity.current) { listOf(update, readings).maxOf { measurer.measure(it, labelStyle, maxLines = 1).size.width }.toDp() }
        val buttons: @Composable (Modifier) -> Unit = { modifier ->
            FilledTonalButton(onClick = { go("reading/new") }, modifier = modifier.testTag("add_reading")) { Text(update) }
            FilledTonalButton(onClick = { go("readings") }, modifier = modifier.testTag("reading_history")) { Text(readings) }
        }
        if (widest + 48.dp <= (maxWidth - 12.dp) / 2) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) { buttons(Modifier.weight(1f)) }
        else Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { buttons(Modifier.fillMaxWidth()) }
    }
}

/** One line that shrinks before it would wrap: a mileage or a name must not be broken in the middle. */
@Composable private fun FittingText(text: String, style: TextStyle, color: Color, smallest: TextUnit, modifier: Modifier = Modifier) {
    BasicText(text, modifier, style.copy(color = color), maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis,
        autoSize = TextAutoSize.StepBased(minFontSize = smallest, maxFontSize = style.fontSize, stepSize = 1.sp))
}

@Composable private fun OdometerGroup(label: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        content()
    }
}

@Composable private fun ActionTile(label: String, icon: ImageVector, primary: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(onClick = onClick, color = if (primary) scheme.primaryContainer else scheme.surfaceContainerLow, contentColor = if (primary) scheme.onPrimaryContainer else scheme.onSurface, shape = MaterialTheme.shapes.medium, border = if (primary) null else BorderStroke(1.dp, scheme.outlineVariant), modifier = modifier) {
        Column(Modifier.padding(16.dp).heightIn(min = 72.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(icon, contentDescription = null)
            Text(label, style = MaterialTheme.typography.titleSmall)
        }
    }
}

@Composable internal fun History(state: GarageState, go: (String) -> Unit) = Page {
    Text(stringResource(R.string.history), style = MaterialTheme.typography.headlineMedium)
    val snapshot = state.snapshot; val locale = uiLocale()
    if (snapshot == null) { Text(stringResource(R.string.no_history_without_car)); return@Page }
    val scheme = MaterialTheme.colorScheme
    Text(snapshot.vehicle.displayName, color = scheme.onSurfaceVariant)
    Button(onClick = { go("service/new") }, modifier = Modifier.fillMaxWidth().testTag("add_service")) { Text(stringResource(R.string.add_service)) }
    if (snapshot.events.isEmpty()) { Text(stringResource(R.string.history_empty_title), style = MaterialTheme.typography.titleMedium); Text(stringResource(R.string.history_empty_body), color = scheme.onSurfaceVariant) }
    snapshot.history.forEach { record ->
        Surface(onClick = { go("service/${record.event.id}") }, color = scheme.surfaceContainerLow, shape = MaterialTheme.shapes.medium, border = BorderStroke(1.dp, scheme.outlineVariant), modifier = Modifier.fillMaxWidth().testTag("history_entry")) {
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
    Text(LocalDates.text(record.event.performedDay, locale), style = MaterialTheme.typography.titleMedium)
    Text(record.reading?.let { formattedKm(it.km, locale) + " km" } ?: stringResource(R.string.odometer_unknown))
    Text(stringResource(R.string.completed_work), style = MaterialTheme.typography.titleMedium)
    record.orderedActions.forEach { Text(workLabel(WorkInput(it.actionCode, it.operationCode, it.customLabel)), Modifier.testTag("confirmed_work")) }
    Text(record.event.costMinor?.let { formattedMoney(it, locale) } ?: stringResource(R.string.cost_unknown))
    if (record.event.note.isNotEmpty()) Text(record.event.note, Modifier.testTag("service_note"))
    record.reading?.takeIf { it.exceptionReason.isNotEmpty() }?.let { NoteCard(it.exceptionReason) }
    Text(stringResource(R.string.confirmed_by_you), style = MaterialTheme.typography.bodySmall)
    if (record.documents.isNotEmpty()) {
        var removing by remember { mutableStateOf<String?>(null) }
        var noViewer by remember { mutableStateOf(false) }
        Text(stringResource(R.string.documents_title), style = MaterialTheme.typography.titleMedium)
        record.documents.sortedWith(compareBy({ it.createdAtUtc }, { it.id })).forEach { document ->
            OutlinedCard(Modifier.fillMaxWidth().testTag("service_document")) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.document_added, LocalDates.text(document.addedDay, locale)))
                    val present = remember(document.id, state.snapshot) { store?.exists(document) == true }
                    if (present) Button(onClick = { noViewer = store?.view(document) != true }, modifier = Modifier.testTag("open_document")) { Text(stringResource(R.string.document_open)) }
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
    Button(onClick = { go("service/edit/$id") }, modifier = Modifier.fillMaxWidth().testTag("edit_service")) { Text(stringResource(R.string.edit_service)) }
    OutlinedButton(onClick = { askDelete = true }, enabled = !state.saving, modifier = Modifier.fillMaxWidth().testTag("delete_service")) { Text(stringResource(R.string.delete)) }
    if (askDelete) AlertDialog(onDismissRequest = { askDelete = false }, title = { Text(stringResource(R.string.delete_service_title)) }, text = { Text(stringResource(R.string.delete_service_body)) }, confirmButton = { TextButton(onClick = { askDelete = false; delete(id, record.event.revision) }, modifier = Modifier.testTag("confirm_delete")) { Text(stringResource(R.string.delete)) } }, dismissButton = { TextButton(onClick = { askDelete = false }) { Text(stringResource(R.string.cancel)) } })
}

@Composable internal fun Readings(state: GarageState, go: (String) -> Unit, select: (CurrentReadingInput, () -> Unit) -> Unit, delete: (String, Long) -> Unit) = Page {
    val snapshot = state.snapshot; val locale = uiLocale()
    var deleting by remember { mutableStateOf<OdometerReadingEntity?>(null) }
    var reviewingId by rememberSaveable { mutableStateOf<String?>(null) }
    Text(stringResource(R.string.reading_history), style = MaterialTheme.typography.headlineMedium)
    if (snapshot == null) { Text(stringResource(R.string.no_history_without_car)); return@Page }
    Button(onClick = { go("reading/new") }, modifier = Modifier.testTag("add_reading")) { Text(stringResource(R.string.add_reading)) }
    if (snapshot.readings.isEmpty()) Text(stringResource(R.string.no_readings))
    ErrorPanel(state)
    snapshot.readings.sortedWith(compareByDescending<OdometerReadingEntity> { it.observedDay }.thenByDescending { it.createdAtUtc }).forEach { reading ->
        OutlinedCard(Modifier.fillMaxWidth().testTag("reading_entry")) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
    LanguageSelector(); HorizontalDivider()
    if (reminderStore != null) { ReminderSettingsSection(reminderStore, onRemindersChanged); HorizontalDivider() }
    Text(stringResource(R.string.your_data), style = MaterialTheme.typography.titleMedium)
    Text(stringResource(R.string.local_storage_note))
    if (dataFiles != null) DataSection(dataFiles, hasVehicle)
    HorizontalDivider()
    Text(stringResource(R.string.data_sources_title), style = MaterialTheme.typography.titleMedium)
    Text(stringResource(R.string.data_sources_note), style = MaterialTheme.typography.bodySmall)
    // Lets a tester say exactly which build a remark is about.
    Text(stringResource(R.string.app_version, BuildConfig.VERSION_NAME), style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("app_version"))
}
