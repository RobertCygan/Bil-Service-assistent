package app.servicepassport.android.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.servicepassport.android.R
import app.servicepassport.android.data.*
import app.servicepassport.contract.ServiceAction
import java.util.UUID

@Composable internal fun IntervalsPage(state: GarageState, go: (String) -> Unit, delete: (String, Long) -> Unit) = Page {
    Text(stringResource(R.string.your_intervals), style = MaterialTheme.typography.headlineMedium)
    NoteCard(stringResource(R.string.user_interval_explanation))
    if (state.loading) { CircularProgressIndicator(); return@Page }
    if (state.problem == StorageProblem.READ) { Text(stringResource(R.string.read_error)); return@Page }
    val snapshot = state.snapshot ?: return@Page
    Button(onClick = { go("interval/new") }, enabled = !state.saving, modifier = Modifier.fillMaxWidth().testTag("add_interval")) { Text(stringResource(R.string.add_interval)) }
    TextButton(onClick = { go("dashboard") }, modifier = Modifier.testTag("view_dashboard")) { Text(stringResource(R.string.service_overview)) }
    ErrorPanel(state)
    if (snapshot.intervals.isEmpty()) Text(stringResource(R.string.no_intervals_yet))
    var deleting by remember { mutableStateOf<UserIntervalEntity?>(null) }
    snapshot.intervals.sortedBy { it.actionCode + ":" + it.operationCode }.forEach { interval ->
        OutlinedCard(Modifier.fillMaxWidth().testTag("interval_entry")) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(workLabel(WorkInput(interval.actionCode, interval.operationCode)), style = MaterialTheme.typography.titleMedium)
                interval.distanceKm?.let { Text(stringResource(R.string.interval_km_value, formattedKm(it.toLong(), uiLocale()))) }
                interval.months?.let { Text(stringResource(R.string.interval_months_value, it.toString())) }
                if (interval.distanceKm != null && interval.months != null) Text(stringResource(R.string.whichever_first))
                Row {
                    TextButton(onClick = { go("interval/edit/${interval.id}") }, enabled = !state.saving) { Text(stringResource(R.string.edit)) }
                    TextButton(onClick = { deleting = interval }, enabled = !state.saving) { Text(stringResource(R.string.delete)) }
                }
            }
        }
    }
    deleting?.let { interval ->
        AlertDialog(onDismissRequest = { deleting = null }, title = { Text(stringResource(R.string.delete_interval_title)) }, text = { Text(stringResource(R.string.delete_interval_body)) },
            confirmButton = { TextButton(onClick = { deleting = null; delete(interval.id, interval.revision) }, modifier = Modifier.testTag("confirm_delete_interval")) { Text(stringResource(R.string.delete)) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.cancel)) } })
    }
}

@Composable internal fun IntervalEditor(state: GarageState, interval: UserIntervalEntity?, onDirty: (Boolean) -> Unit, save: (UserIntervalInput) -> Unit) = Page {
    if (state.loading) { CircularProgressIndicator(); return@Page }
    if (state.problem == StorageProblem.READ) { Text(stringResource(R.string.read_error)); return@Page }
    val snapshot = state.snapshot ?: return@Page
    val locale = uiLocale()
    val id = rememberSaveable { interval?.id ?: UUID.randomUUID().toString() }
    val revision = rememberSaveable { interval?.revision ?: -1L }.takeIf { it >= 0 }
    var action by rememberSaveable { mutableStateOf(interval?.actionCode) }
    var operation by rememberSaveable { mutableStateOf(interval?.operationCode) }
    var distance by rememberSaveable { mutableStateOf(interval?.distanceKm?.toString().orEmpty()) }
    var months by rememberSaveable { mutableStateOf(interval?.months?.toString().orEmpty()) }
    var sourceLocale by rememberSaveable { mutableStateOf(locale) }
    var issue by remember { mutableStateOf<InputProblem?>(null) }
    val dirty = action != interval?.actionCode || operation != interval?.operationCode || distance != interval?.distanceKm?.toString().orEmpty() || months != interval?.months?.toString().orEmpty()
    LaunchedEffect(dirty) { onDirty(dirty) }
    DisposableEffect(Unit) { onDispose { onDirty(false) } }
    LaunchedEffect(locale) {
        if (locale != sourceLocale) {
            runCatching { LocalNumbers.kilometers(distance, sourceLocale) }.getOrNull()?.let { distance = it.toString() }
            runCatching { LocalNumbers.kilometers(months, sourceLocale) }.getOrNull()?.let { months = it.toString() }
            sourceLocale = locale
        }
    }
    Text(stringResource(if (interval == null) R.string.add_interval else R.string.edit_interval), style = MaterialTheme.typography.headlineMedium)
    NoteCard(stringResource(R.string.user_interval_explanation))
    Choice(stringResource(R.string.choose_work), action, ServiceAction.entries.map { it.code to stringResource(actionResource(it.code)) }, "interval_action") { code ->
        action = code; operation = ServiceAction.entries.first { it.code == code }.operations.singleOrNull()?.code; issue = null
    }
    val selected = ServiceAction.entries.firstOrNull { it.code == action }
    Choice(stringResource(R.string.operation), operation, selected?.operations?.map { it.code to stringResource(operationResource(it.code)) }.orEmpty(), "interval_operation") { operation = it; issue = null }
    OutlinedTextField(value = distance, onValueChange = { distance = it; issue = null }, label = { Text(stringResource(R.string.interval_distance_optional)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth().testTag("interval_km"))
    OutlinedTextField(value = months, onValueChange = { months = it; issue = null }, label = { Text(stringResource(R.string.interval_months_optional)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth().testTag("interval_months"))
    Text(stringResource(R.string.interval_input_help), style = MaterialTheme.typography.bodySmall)
    Text(stringResource(R.string.interval_anchor_help), style = MaterialTheme.typography.bodySmall)
    ErrorPanel(state, issue)
    Button(enabled = !state.saving, onClick = {
        try {
            val km = try { LocalNumbers.kilometers(distance, locale)?.toInt() } catch (_: InputRejected) { throw InputRejected(InputProblem.INVALID_INTERVAL) }
            val time = try { LocalNumbers.kilometers(months, locale)?.toInt() } catch (_: InputRejected) { throw InputRejected(InputProblem.INVALID_INTERVAL) }
            val input = UserIntervalInput(id, snapshot.vehicle.id, revision, action, operation, km, time)
            UserIntervalValidation.validate(input)
            issue = null; save(input)
        } catch (rejected: InputRejected) { issue = rejected.problem }
    }, modifier = Modifier.fillMaxWidth().testTag("save_interval")) { Text(stringResource(if (state.saving) R.string.saving else R.string.save_interval)) }
}
