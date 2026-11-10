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
import java.time.LocalDate
import java.util.UUID

/** Whole number, optionally negative ("-500" when the car shows the service as overdue). Empty means not shown. */
internal fun signedWhole(text: String): Int? {
    val value = text.trim().replace("−", "-")
    if (value.isEmpty()) return null
    if (!Regex("-?[0-9]{1,7}").matches(value)) throw InputRejected(InputProblem.INVALID_INDICATOR)
    return value.toInt()
}

/** What the car's own service display shows. The app stores it as entered and never turns it into an interval. */
@Composable internal fun IndicatorPage(state: GarageState, today: LocalDate, go: (String) -> Unit, save: (IndicatorInput) -> Unit, delete: (String) -> Unit) = Page {
    Text(stringResource(R.string.indicator_title), style = MaterialTheme.typography.headlineMedium)
    NoteCard(stringResource(R.string.indicator_explanation))
    if (state.loading) { CircularProgressIndicator(); return@Page }
    if (state.problem == StorageProblem.READ) { Text(stringResource(R.string.read_error)); return@Page }
    val snapshot = state.snapshot
    if (snapshot == null) { Text(stringResource(R.string.no_history_without_car)); return@Page }
    val locale = uiLocale()
    val latest = snapshot.latestIndicator
    if (latest == null) Text(stringResource(R.string.indicator_none)) else OutlinedCard(Modifier.fillMaxWidth().testTag("indicator_latest")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.indicator_latest, LocalDates.text(latest.observedDay, locale), formattedKm(latest.odometerKm, locale)), style = MaterialTheme.typography.titleMedium)
            latest.remainingKm?.let { Text(stringResource(R.string.indicator_km_value, formattedKm(it.toLong(), locale))) }
            latest.remainingDays?.let { Text(stringResource(R.string.indicator_days_value, it)) }
            TextButton(onClick = { delete(latest.id) }, enabled = !state.saving, modifier = Modifier.testTag("delete_indicator")) { Text(stringResource(R.string.delete)) }
        }
    }
    HorizontalDivider()
    Text(stringResource(R.string.indicator_add), style = MaterialTheme.typography.titleMedium)
    var id by rememberSaveable { mutableStateOf(UUID.randomUUID().toString()) }
    var date by rememberSaveable { mutableStateOf(LocalDates.text(today.toEpochDay(), locale)) }
    var odometer by rememberSaveable { mutableStateOf(snapshot.currentReading?.km?.toString().orEmpty()) }
    var km by rememberSaveable { mutableStateOf("") }
    var days by rememberSaveable { mutableStateOf("") }
    var issue by remember { mutableStateOf<InputProblem?>(null) }
    OutlinedTextField(date, { date = it; issue = null }, Modifier.fillMaxWidth().testTag("indicator_date"), label = { Text(stringResource(R.string.indicator_date)) }, singleLine = true)
    OutlinedTextField(odometer, { odometer = it; issue = null }, Modifier.fillMaxWidth().testTag("indicator_odometer"), label = { Text(stringResource(R.string.indicator_odometer)) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
    OutlinedTextField(km, { km = it; issue = null }, Modifier.fillMaxWidth().testTag("indicator_km"), label = { Text(stringResource(R.string.indicator_km)) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text))
    OutlinedTextField(days, { days = it; issue = null }, Modifier.fillMaxWidth().testTag("indicator_days"), label = { Text(stringResource(R.string.indicator_days)) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text))
    Text(stringResource(R.string.indicator_input_help), style = MaterialTheme.typography.bodySmall)
    ErrorPanel(state, issue)
    Button(enabled = !state.saving, modifier = Modifier.fillMaxWidth().testTag("save_indicator"), onClick = {
        try {
            val day = requireNotNull(LocalDates.day(date, locale, today))
            val reading = LocalNumbers.kilometers(odometer, locale) ?: throw InputRejected(InputProblem.INVALID_KM)
            val input = IndicatorInput(id, snapshot.vehicle.id, day, reading, signedWhole(km), signedWhole(days))
            IndicatorValidation.validate(input, today)
            issue = null; save(input)
            id = UUID.randomUUID().toString(); km = ""; days = ""
        } catch (rejected: InputRejected) { issue = rejected.problem }
    }) { Text(stringResource(if (state.saving) R.string.saving else R.string.indicator_save)) }
    TextButton(onClick = { go("dashboard") }) { Text(stringResource(R.string.service_overview)) }
}
