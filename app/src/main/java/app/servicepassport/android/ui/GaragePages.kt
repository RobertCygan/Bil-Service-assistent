package app.servicepassport.android.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.servicepassport.android.R
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
            val snapshot = requireNotNull(state.snapshot); val locale = uiLocale()
            Text(snapshot.vehicle.displayName, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.testTag("profile_name"))
            snapshot.vehicle.modelYear?.let { Text(stringResource(R.string.model_year_value, it.toString())) }
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.current_odometer), style = MaterialTheme.typography.titleMedium)
                    val reading = snapshot.currentReading
                    if (reading == null) Text(stringResource(R.string.odometer_missing), Modifier.testTag("no_current_reading")) else {
                        Text(formattedKm(reading.km, locale) + " km", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.testTag("current_km"))
                        Text(LocalDates.text(reading.observedDay, locale), Modifier.testTag("current_reading_date"))
                        if (today.toEpochDay() - reading.observedDay > AppPolicy.ODOMETER_STALE_AFTER_DAYS) Text(stringResource(R.string.odometer_stale))
                        if (reading.exceptionReason.isNotEmpty()) Text(stringResource(R.string.exception_recorded))
                    }
                    Button(onClick = { go("reading/new") }, modifier = Modifier.testTag("add_reading")) { Text(stringResource(R.string.update_odometer)) }
                    TextButton(onClick = { go("readings") }) { Text(stringResource(R.string.reading_history)) }
                }
            }
            Button(onClick = { go("service/new") }, modifier = Modifier.fillMaxWidth().testTag("add_service")) { Text(stringResource(R.string.add_service)) }
            OutlinedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.no_verified_schedule), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.no_schedule_body))
            } }
            OutlinedButton(onClick = { go("vehicle") }, modifier = Modifier.fillMaxWidth().testTag("edit_car")) { Text(stringResource(R.string.edit_car)) }
        }
    }
}

@Composable internal fun History(state: GarageState, go: (String) -> Unit) = Page {
    Text(stringResource(R.string.history), style = MaterialTheme.typography.headlineMedium)
    val snapshot = state.snapshot; val locale = uiLocale()
    if (snapshot == null) { Text(stringResource(R.string.no_history_without_car)); return@Page }
    Text(snapshot.vehicle.displayName)
    Button(onClick = { go("service/new") }, modifier = Modifier.fillMaxWidth().testTag("add_service")) { Text(stringResource(R.string.add_service)) }
    if (snapshot.events.isEmpty()) { Text(stringResource(R.string.history_empty_title)); Text(stringResource(R.string.history_empty_body)) }
    snapshot.history.forEach { record ->
        OutlinedCard(onClick = { go("service/${record.event.id}") }, modifier = Modifier.fillMaxWidth().testTag("history_entry")) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(LocalDates.text(record.event.performedDay, locale), style = MaterialTheme.typography.titleMedium)
                Text(record.reading?.let { formattedKm(it.km, locale) + " km" } ?: stringResource(R.string.odometer_unknown))
                record.orderedActions.forEach { Text(workLabel(WorkInput(it.actionCode, it.operationCode, it.customLabel))) }
                record.event.costMinor?.let { Text(formattedMoney(it, locale)) }
                Text(stringResource(R.string.confirmed_by_you), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable internal fun ServiceDetails(state: GarageState, id: String, go: (String) -> Unit, delete: (String, Long) -> Unit) = Page {
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
    ErrorPanel(state)
    Button(onClick = { go("service/edit/$id") }, modifier = Modifier.fillMaxWidth().testTag("edit_service")) { Text(stringResource(R.string.edit_service)) }
    OutlinedButton(onClick = { askDelete = true }, enabled = !state.saving, modifier = Modifier.fillMaxWidth().testTag("delete_service")) { Text(stringResource(R.string.delete)) }
    if (askDelete) AlertDialog(onDismissRequest = { askDelete = false }, title = { Text(stringResource(R.string.delete_service_title)) }, text = { Text(stringResource(R.string.delete_service_body)) }, confirmButton = { TextButton(onClick = { askDelete = false; delete(id, record.event.revision) }, modifier = Modifier.testTag("confirm_delete")) { Text(stringResource(R.string.delete)) } }, dismissButton = { TextButton(onClick = { askDelete = false }) { Text(stringResource(R.string.cancel)) } })
}

@Composable internal fun Readings(state: GarageState, go: (String) -> Unit, select: (String, Long) -> Unit, delete: (String, Long) -> Unit) = Page {
    val snapshot = state.snapshot; val locale = uiLocale()
    var deleting by remember { mutableStateOf<OdometerReadingEntity?>(null) }
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
            if (snapshot.currentReading?.id != reading.id) TextButton(onClick = { select(reading.id, reading.revision) }, enabled = !state.saving) { Text(stringResource(R.string.use_as_current)) }
            if (reading.eventId == null) {
                TextButton(onClick = { go("reading/${reading.id}") }) { Text(stringResource(R.string.edit)) }
                TextButton(onClick = { deleting = reading }, enabled = !state.saving) { Text(stringResource(R.string.delete)) }
            } else TextButton(onClick = { go("service/${reading.eventId}") }) { Text(stringResource(R.string.open_service)) }
        } }
    }
    deleting?.let { reading -> AlertDialog(onDismissRequest = { deleting = null }, title = { Text(stringResource(R.string.delete_reading_title)) }, text = { Text(stringResource(R.string.delete_reading_body)) }, confirmButton = { TextButton(onClick = { deleting = null; delete(reading.id, reading.revision) }, modifier = Modifier.testTag("confirm_delete")) { Text(stringResource(R.string.delete)) } }, dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.cancel)) } }) }
}

@Composable internal fun Settings() = Page {
    Text(stringResource(R.string.settings), style = MaterialTheme.typography.headlineMedium)
    LanguageSelector(); HorizontalDivider()
    Text(stringResource(R.string.your_data), style = MaterialTheme.typography.titleMedium)
    Text(stringResource(R.string.local_storage_note))
}
