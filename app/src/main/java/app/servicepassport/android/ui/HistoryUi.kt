package app.servicepassport.android.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.servicepassport.android.R
import app.servicepassport.android.data.*
import app.servicepassport.contract.*
import java.math.BigDecimal
import java.text.NumberFormat
import java.time.LocalDate
import java.util.Currency
import java.util.Locale

@Composable internal fun uiLocale(): String = if (LocalConfiguration.current.locales[0].language in setOf("nb", "no")) "nb-NO" else "en"
internal fun formattedKm(km: Long, locale: String): String = NumberFormat.getIntegerInstance(Locale.forLanguageTag(locale)).format(km)
internal fun formattedMoney(minor: Long, locale: String): String = NumberFormat.getCurrencyInstance(Locale.forLanguageTag(locale)).apply { currency = Currency.getInstance("NOK") }.format(BigDecimal.valueOf(minor, 2))
internal fun amountInput(minor: Long?, locale: String): String = minor?.let { BigDecimal.valueOf(it, 2).toPlainString().let { text -> if (locale == "nb-NO") text.replace('.', ',') else text } }.orEmpty()

internal fun actionResource(code: String?): Int = when (code) {
    "engine_oil" -> R.string.action_engine_oil; "oil_filter" -> R.string.action_oil_filter
    "gearbox_oil" -> R.string.action_gearbox_oil; "brake_fluid" -> R.string.action_brake_fluid
    "coolant" -> R.string.action_coolant; "spark_plugs" -> R.string.action_spark_plugs
    "timing_belt" -> R.string.action_timing_belt; "air_filter" -> R.string.action_air_filter
    "cabin_filter" -> R.string.action_cabin_filter; "fuel_filter" -> R.string.action_fuel_filter
    "brake_pads" -> R.string.action_brake_pads; "brake_discs" -> R.string.action_brake_discs
    "inspection_service" -> R.string.action_inspection_service; "brakes" -> R.string.action_brakes
    "battery_12v" -> R.string.action_battery_12v; else -> R.string.custom_work
}
internal fun operationResource(code: String?): Int = when (code) { "replace" -> R.string.operation_replace; "inspect" -> R.string.operation_inspect; "test" -> R.string.operation_test; else -> R.string.choose_operation }
@Composable internal fun workLabel(work: WorkInput): String = work.customLabel ?: stringResource(actionResource(work.actionCode)) + " · " + stringResource(operationResource(work.operationCode))

internal fun inputMessage(problem: InputProblem): Int = when (problem) {
    InputProblem.DATE_IN_FUTURE -> R.string.date_in_future; InputProblem.INVALID_DATE -> R.string.invalid_service_date
    InputProblem.INVALID_KM -> R.string.invalid_kilometers; InputProblem.NO_ACTIONS -> R.string.no_work_selected
    InputProblem.INVALID_ACTION -> R.string.invalid_work; InputProblem.DUPLICATE_ACTION -> R.string.duplicate_work
    InputProblem.INVALID_COST -> R.string.invalid_cost; InputProblem.INVALID_PROFILE -> R.string.profile_details_invalid
    InputProblem.NOTE_TOO_LONG -> R.string.note_too_long; InputProblem.WARNINGS_NEED_REVIEW -> R.string.warning_needs_review
    InputProblem.NOT_FOUND -> R.string.entry_not_found; InputProblem.CONFLICT -> R.string.entry_conflict
}

@Composable internal fun Page(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
}
@Composable internal fun ErrorPanel(state: GarageState, local: InputProblem? = null) {
    val issue = local ?: state.inputProblem
    if (issue != null) Text(stringResource(inputMessage(issue)), color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("input_error"))
    if (state.problem == StorageProblem.WRITE) Text(stringResource(R.string.write_error), color = MaterialTheme.colorScheme.error)
}
@Composable internal fun NoteCard(text: String) {
    OutlinedCard(Modifier.fillMaxWidth()) { Text(text, Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun Choice(label: String, selected: String?, choices: List<Pair<String, String>>, tag: String, onChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
        OutlinedTextField(value = choices.firstOrNull { it.first == selected }?.second.orEmpty(), onValueChange = {}, readOnly = true, label = { Text(label) }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) }, modifier = Modifier.fillMaxWidth().menuAnchor().testTag(tag))
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            choices.forEach { (key, text) -> DropdownMenuItem(text = { Text(text) }, onClick = { expanded = false; onChange(key) }, modifier = Modifier.testTag("${tag}_$key")) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun DateField(label: String, value: String, tag: String, today: LocalDate, optional: Boolean = false, onChange: (String) -> Unit) {
    val locale = uiLocale(); var calendar by remember { mutableStateOf(false) }
    OutlinedTextField(value = value, onValueChange = onChange, singleLine = true, label = { Text(label) }, supportingText = { Text(stringResource(R.string.date_format_hint)) }, trailingIcon = { TextButton(onClick = { calendar = true }) { Text(stringResource(R.string.choose_date)) } }, modifier = Modifier.fillMaxWidth().testTag(tag))
    if (optional && value.isNotEmpty()) TextButton(onClick = { onChange("") }) { Text(stringResource(R.string.clear_date)) }
    if (calendar) {
        val initial = runCatching { LocalDates.day(value, locale, today) }.getOrNull() ?: today.toEpochDay()
        val picker = rememberDatePickerState(initialSelectedDateMillis = initial * 86_400_000, selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis in LocalDate.of(1900, 1, 1).toEpochDay() * 86_400_000..today.toEpochDay() * 86_400_000
        })
        DatePickerDialog(onDismissRequest = { calendar = false }, confirmButton = { TextButton(onClick = { picker.selectedDateMillis?.let { onChange(LocalDates.text(it / 86_400_000, locale)) }; calendar = false }) { Text(stringResource(android.R.string.ok)) } }, dismissButton = { TextButton(onClick = { calendar = false }) { Text(stringResource(R.string.cancel)) } }) { DatePicker(picker) }
    }
}

@Composable internal fun OdometerReview(warnings: Set<ReadingWarning>, token: String?, reason: String, reviewedToken: String?, onReason: (String) -> Unit, onReview: (String?) -> Unit) {
    if (warnings.isEmpty()) return
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.warning_title), style = MaterialTheme.typography.titleMedium)
            warnings.forEach { warning -> Text(stringResource(when (warning) {
                ReadingWarning.INCONSISTENT_VALUE -> R.string.warning_inconsistent
                ReadingWarning.SAME_DAY_DIFFERENT -> R.string.warning_same_day
                ReadingWarning.OLDER_CURRENT_SELECTION -> R.string.warning_old_current
            })) }
            OutlinedTextField(value = reason, onValueChange = { onReason(it); onReview(null) }, label = { Text(stringResource(R.string.exception_reason)) }, modifier = Modifier.fillMaxWidth().testTag("exception_reason"))
            Row(Modifier.fillMaxWidth()) {
                Checkbox(checked = reviewedToken != null && reviewedToken == token, onCheckedChange = { onReview(if (it) token else null) }, modifier = Modifier.testTag("reading_reviewed"))
                Text(stringResource(R.string.warning_acknowledged), Modifier.padding(top = 12.dp))
            }
            Text(stringResource(R.string.exception_not_conversion), style = MaterialTheme.typography.bodySmall)
        }
    }
}
