@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package app.servicepassport.android.ui

import kotlinx.coroutines.launch
import app.servicepassport.extraction.OdometerCandidate
import app.servicepassport.android.documents.DocumentStore
import app.servicepassport.android.documents.DocumentAnalyzer
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import app.servicepassport.android.documents.ServicePrefill
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.KeyboardType
import app.servicepassport.android.R
import app.servicepassport.android.data.*
import app.servicepassport.contract.*
import java.time.LocalDate
import java.util.UUID
import org.json.JSONArray

private fun encodeWork(work: List<WorkInput>): String = JSONArray().also { list -> work.forEach { list.put(JSONArray().put(it.actionCode).put(it.operationCode).put(it.customLabel)) } }.toString()
private fun decodeWork(raw: String): List<WorkInput> {
    val array = JSONArray(raw)
    return (0 until array.length()).map { index ->
        val row = array.getJSONArray(index)
        WorkInput(if (row.isNull(0)) null else row.getString(0), if (row.isNull(1)) null else row.getString(1), if (row.isNull(2)) null else row.getString(2))
    }
}

@Composable internal fun ServiceEditor(state: GarageState, record: ServiceRecord?, today: LocalDate, onDirty: (Boolean) -> Unit, prefill: ServicePrefill? = null, keepsDocument: Boolean = false, save: (ServiceInput) -> Unit) = Page {
    val snapshot = state.snapshot ?: return@Page; val locale = uiLocale()
    val id = rememberSaveable { record?.event?.id ?: UUID.randomUUID().toString() }
    val expectedRevision = rememberSaveable { record?.event?.revision ?: -1L }.takeIf { it >= 0 }
    val initialWork = remember { encodeWork(record?.orderedActions?.map { WorkInput(it.actionCode, it.operationCode, it.customLabel) } ?: prefill?.work.orEmpty()) }
    var workJson by rememberSaveable { mutableStateOf(initialWork) }
    val work = remember(workJson) { decodeWork(workJson) }
    var sourceLocale by rememberSaveable { mutableStateOf(locale) }
    // A document without a recognised date leaves the field empty: the driver must enter it, today is never assumed.
    var date by rememberSaveable { mutableStateOf(if (record == null && prefill != null) prefill.day?.let { LocalDates.text(it, locale) }.orEmpty() else LocalDates.text(record?.event?.performedDay ?: today.toEpochDay(), locale)) }
    var km by rememberSaveable { mutableStateOf((record?.reading?.km ?: prefill?.km)?.toString().orEmpty()) }
    var cost by rememberSaveable { mutableStateOf(amountInput(record?.event?.costMinor ?: prefill?.costMinor, locale)) }
    var note by rememberSaveable { mutableStateOf(record?.event?.note.orEmpty()) }
    var reason by rememberSaveable { mutableStateOf(record?.reading?.exceptionReason.orEmpty()) }
    var current by rememberSaveable { mutableStateOf(false) }
    var reviewedToken by rememberSaveable { mutableStateOf<String?>(null) }
    var issue by remember { mutableStateOf<InputProblem?>(null) }
    LaunchedEffect(locale) {
        if (sourceLocale != locale) {
            runCatching { LocalDates.day(date, sourceLocale, today) }.getOrNull()?.let { date = LocalDates.text(it, locale) }
            runCatching { LocalNumbers.kilometers(km, sourceLocale) }.getOrNull()?.let { km = it.toString() }
            runCatching { LocalNumbers.moneyMinor(cost, sourceLocale) }.onSuccess { cost = amountInput(it, locale) }
            sourceLocale = locale
        }
    }
    val dayValue = runCatching { LocalDates.day(date, locale, today) }.getOrNull()
    val kmValue = runCatching { LocalNumbers.kilometers(km, locale) }.getOrNull()
    val warnings = if (dayValue != null && kmValue != null) ReadingConsistency.warnings(dayValue, kmValue, snapshot.readings.filter { it.id != record?.reading?.id }, snapshot.currentReading, current) else emptySet()
    val token = if (dayValue != null && kmValue != null) ReadingConsistency.token(snapshot, record?.reading?.id, dayValue, kmValue, current) else null
    val dirty = date != LocalDates.text(record?.event?.performedDay ?: today.toEpochDay(), locale) || km != record?.reading?.km?.toString().orEmpty() || cost != amountInput(record?.event?.costMinor, locale) || note != record?.event?.note.orEmpty() || workJson != initialWork || current || reason != record?.reading?.exceptionReason.orEmpty()
    LaunchedEffect(dirty) { onDirty(dirty) }; DisposableEffect(Unit) { onDispose { onDirty(false) } }
    Text(stringResource(if (record == null) R.string.add_service else R.string.edit_service), style = MaterialTheme.typography.headlineMedium)
    if (keepsDocument) NoteCard(stringResource(R.string.service_document_attached))
    if (record == null && prefill != null) NoteCard(stringResource(R.string.import_prefilled_note))
    Text(snapshot.vehicle.displayName)
    DateField(stringResource(R.string.service_date), date, "service_date", today) { date = it; reviewedToken = null; issue = null }
    OutlinedTextField(value = km, onValueChange = { km = it; reviewedToken = null; issue = null }, singleLine = true, label = { Text(stringResource(R.string.optional_odometer)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth().testTag("service_km"))
    Row { Checkbox(checked = current, onCheckedChange = { current = it; reviewedToken = null }, modifier = Modifier.testTag("use_current")); Text(stringResource(R.string.use_as_current), Modifier.padding(top = 12.dp)) }
    Text(stringResource(R.string.selection_help), style = MaterialTheme.typography.bodySmall)
    if (record?.reading?.id != null && snapshot.currentReading?.id == record.reading?.id) NoteCard(stringResource(R.string.selection_cleared_help))
    OdometerReview(warnings, token, reason, reviewedToken, { reason = it }, { reviewedToken = it })
    if (warnings.isEmpty() && reason.isNotBlank()) OutlinedTextField(value = reason, onValueChange = { reason = it; reviewedToken = null }, label = { Text(stringResource(R.string.exception_reason)) }, modifier = Modifier.fillMaxWidth())
    Text(stringResource(R.string.completed_work), style = MaterialTheme.typography.titleMedium)
    work.forEachIndexed { index, item ->
        Panel(Modifier.fillMaxWidth().testTag("work_row")) { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val choices = ServiceAction.entries.map { it.code to stringResource(actionResource(it.code)) } + ("custom" to stringResource(R.string.custom_work))
            Choice(stringResource(R.string.choose_work), if (item.customLabel != null) "custom" else item.actionCode, choices, "work_action_$index") { code ->
                val updated = if (code == "custom") WorkInput(null, null, "") else {
                    val action = ServiceAction.entries.first { it.code == code }
                    WorkInput(code, action.operations.singleOrNull()?.code)
                }
                workJson = encodeWork(work.toMutableList().also { it[index] = updated }); issue = null
            }
            if (item.customLabel != null) {
                OutlinedTextField(value = item.customLabel, onValueChange = { value -> workJson = encodeWork(work.toMutableList().also { it[index] = item.copy(customLabel = value) }) }, label = { Text(stringResource(R.string.custom_work_label)) }, modifier = Modifier.fillMaxWidth().testTag("custom_work_$index"))
            } else {
                val action = ServiceAction.entries.firstOrNull { it.code == item.actionCode }
                Choice(stringResource(R.string.operation), item.operationCode, action?.operations?.map { it.code to stringResource(operationResource(it.code)) }.orEmpty(), "work_operation_$index") { value -> workJson = encodeWork(work.toMutableList().also { it[index] = item.copy(operationCode = value) }); issue = null }
            }
            TextButton(onClick = { workJson = encodeWork(work.toMutableList().also { it.removeAt(index) }) }, modifier = Modifier.testTag("remove_work_$index")) { Text(stringResource(R.string.remove_work)) }
        } }
    }
    QuietButton(onClick = { workJson = encodeWork(work + WorkInput(null, null)) }, enabled = work.size < 30, modifier = Modifier.fillMaxWidth().testTag("add_work")) { Text(stringResource(R.string.add_work)) }
    OutlinedTextField(value = cost, onValueChange = { cost = it; issue = null }, singleLine = true, label = { Text(stringResource(R.string.cost_nok)) }, supportingText = { Text(stringResource(R.string.cost_example)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth().testTag("service_cost"))
    OutlinedTextField(value = note, onValueChange = { note = it; issue = null }, label = { Text(stringResource(R.string.notes_optional)) }, minLines = 2, modifier = Modifier.fillMaxWidth().testTag("service_notes"))
    ErrorPanel(state, issue)
    PrimaryButton(enabled = !state.saving, onClick = {
        try {
            val input = ServiceInput(id, snapshot.vehicle.id, expectedRevision, requireNotNull(LocalDates.day(date, locale, today)), LocalNumbers.kilometers(km, locale), work, LocalNumbers.moneyMinor(cost, locale), note, current, reason, reviewedToken != null, reviewedToken)
            HistoryValidation.service(input, today); issue = null; save(input)
        } catch (e: InputRejected) { issue = e.problem }
    }, modifier = Modifier.fillMaxWidth().testTag("save_service")) { Text(stringResource(if (state.saving) R.string.saving else R.string.save_service)) }
}

@Composable internal fun ReadingEditor(state: GarageState, reading: OdometerReadingEntity?, today: LocalDate, onDirty: (Boolean) -> Unit, reader: DocumentAnalyzer? = null, store: DocumentStore? = null, startWithPhoto: Boolean = false, save: (ReadingInput) -> Unit) = Page {
    val snapshot = state.snapshot ?: return@Page; val locale = uiLocale()
    val id = rememberSaveable { reading?.id ?: UUID.randomUUID().toString() }
    val expectedRevision = rememberSaveable { reading?.revision ?: -1L }.takeIf { it >= 0 }
    var date by rememberSaveable { mutableStateOf(LocalDates.text(reading?.observedDay ?: today.toEpochDay(), locale)) }
    var km by rememberSaveable { mutableStateOf(reading?.km?.toString().orEmpty()) }
    var sourceLocale by rememberSaveable { mutableStateOf(locale) }
    var current by rememberSaveable { mutableStateOf(reading == null || snapshot.currentReading?.id == reading.id) }
    val initialCurrent = rememberSaveable { reading == null || snapshot.currentReading?.id == reading.id }
    var reason by rememberSaveable { mutableStateOf(reading?.exceptionReason.orEmpty()) }
    var reviewedToken by rememberSaveable { mutableStateOf<String?>(null) }
    var issue by remember { mutableStateOf<InputProblem?>(null) }
    LaunchedEffect(locale) {
        if (sourceLocale != locale) {
            runCatching { LocalDates.day(date, sourceLocale, today) }.getOrNull()?.let { date = LocalDates.text(it, locale) }
            runCatching { LocalNumbers.kilometers(km, sourceLocale) }.getOrNull()?.let { km = it.toString() }
            sourceLocale = locale
        }
    }
    val dayValue = runCatching { LocalDates.day(date, locale, today) }.getOrNull(); val kmValue = runCatching { LocalNumbers.kilometers(km, locale) }.getOrNull()
    val warnings = if (dayValue != null && kmValue != null) ReadingConsistency.warnings(dayValue, kmValue, snapshot.readings.filter { it.id != reading?.id }, snapshot.currentReading, current) else emptySet()
    val token = if (dayValue != null && kmValue != null) ReadingConsistency.token(snapshot, reading?.id, dayValue, kmValue, current) else null
    val dirty = km != reading?.km?.toString().orEmpty() || date != LocalDates.text(reading?.observedDay ?: today.toEpochDay(), locale) || current != initialCurrent || reason != reading?.exceptionReason.orEmpty()
    LaunchedEffect(dirty) { onDirty(dirty) }; DisposableEffect(Unit) { onDispose { onDirty(false) } }
    Text(stringResource(if (reading == null) R.string.add_reading else R.string.edit_reading), style = MaterialTheme.typography.headlineMedium)
    Text(snapshot.vehicle.displayName)
    DateField(stringResource(R.string.reading_date), date, "reading_date", today) { date = it; reviewedToken = null; if (reading != null) current = false; issue = null }
    OutlinedTextField(value = km, onValueChange = { km = it; reviewedToken = null; if (reading != null) current = false; issue = null }, singleLine = true, label = { Text(stringResource(R.string.odometer_km)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth().testTag("reading_km"))
    if (reading == null && reader != null && store != null) OdometerFromPhoto(reader, store, snapshot.currentReading?.km?.toInt(), locale, startWithPhoto) { km = it.toString(); reviewedToken = null; issue = null }
    Row { Checkbox(checked = current, onCheckedChange = { current = it; reviewedToken = null }, modifier = Modifier.testTag("use_current")); Text(stringResource(R.string.use_as_current), Modifier.padding(top = 12.dp)) }
    Text(stringResource(R.string.selection_help), style = MaterialTheme.typography.bodySmall)
    if (reading?.id == snapshot.currentReading?.id && reading != null) NoteCard(stringResource(R.string.selection_cleared_help))
    OdometerReview(warnings, token, reason, reviewedToken, { reason = it }, { reviewedToken = it })
    if (warnings.isEmpty() && reason.isNotBlank()) OutlinedTextField(value = reason, onValueChange = { reason = it; reviewedToken = null }, label = { Text(stringResource(R.string.exception_reason)) }, modifier = Modifier.fillMaxWidth())
    ErrorPanel(state, issue)
    PrimaryButton(enabled = !state.saving, onClick = {
        try {
            val day = requireNotNull(LocalDates.day(date, locale, today))
            val value = LocalNumbers.kilometers(km, locale) ?: throw InputRejected(InputProblem.INVALID_KM)
            issue = null; save(ReadingInput(id, snapshot.vehicle.id, expectedRevision, day, value, current, reason, reviewedToken != null, reviewedToken))
        } catch (e: InputRejected) { issue = e.problem }
    }, modifier = Modifier.fillMaxWidth().testTag("save_reading")) { Text(stringResource(if (state.saving) R.string.saving else R.string.save_reading)) }
}

@Composable internal fun ProfileEditor(state: GarageState, today: LocalDate, onDirty: (Boolean) -> Unit, save: (ProfileInput) -> Unit) = Page {
    val profile = state.profile; val locale = uiLocale()
    var name by rememberSaveable(profile?.id) { mutableStateOf(profile?.displayName.orEmpty()) }
    var year by rememberSaveable(profile?.id) { mutableStateOf(profile?.modelYear?.toString().orEmpty()) }
    val initialDetails = remember(profile?.id) { arrayOf(profile?.make.orEmpty(), profile?.model.orEmpty(), profile?.generation.orEmpty(), profile?.vin.orEmpty(), profile?.registrationNumber.orEmpty(), profile?.engineCode.orEmpty(), profile?.gearboxType.orEmpty(), profile?.gearboxCode.orEmpty(), profile?.powertrain.orEmpty(), profile?.market.orEmpty(), profile?.serviceRegime.orEmpty(), profile?.usageProfile.orEmpty()) }
    var details by rememberSaveable(profile?.id) { mutableStateOf(initialDetails.copyOf()) }
    var registrationDate by rememberSaveable(profile?.id) { mutableStateOf(profile?.firstRegistrationDay?.let { LocalDates.text(it, locale) }.orEmpty()) }
    var sourceLocale by rememberSaveable { mutableStateOf(locale) }
    var expanded by rememberSaveable { mutableStateOf(false) }
    var problem by remember { mutableStateOf<ProfileProblem?>(null) }; var issue by remember { mutableStateOf<InputProblem?>(null) }
    val dirty = name != profile?.displayName.orEmpty() || year != profile?.modelYear?.toString().orEmpty() || !details.contentEquals(initialDetails) || registrationDate != profile?.firstRegistrationDay?.let { LocalDates.text(it, locale) }.orEmpty()
    LaunchedEffect(dirty) { onDirty(dirty) }; DisposableEffect(Unit) { onDispose { onDirty(false) } }
    LaunchedEffect(locale) { if (sourceLocale != locale) { runCatching { LocalDates.day(registrationDate, sourceLocale, today, true) }.getOrNull()?.let { registrationDate = LocalDates.text(it, locale) }; sourceLocale = locale } }
    fun detail(index: Int, value: String) { details = details.copyOf().also { it[index] = value }; issue = null }
    Text(stringResource(R.string.car_details), style = MaterialTheme.typography.headlineMedium)
    OutlinedTextField(value = name, onValueChange = { name = it; problem = null }, label = { Text(stringResource(R.string.car_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("car_name"))
    OutlinedTextField(value = year, onValueChange = { year = it; problem = null }, label = { Text(stringResource(R.string.model_year_optional)) }, supportingText = { Text(stringResource(R.string.model_year_hint)) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth().testTag("model_year"))
    TextButton(onClick = { expanded = !expanded }, modifier = Modifier.testTag("profile_optional")) { Text(stringResource(R.string.optional_configuration)) }
    if (expanded) {
        Text(stringResource(R.string.optional_configuration_help), style = MaterialTheme.typography.bodySmall)
        DateField(stringResource(R.string.first_registration), registrationDate, "first_registration", today, true) { registrationDate = it; issue = null }
        Text(stringResource(R.string.first_registration_help), style = MaterialTheme.typography.bodySmall)
        listOf(R.string.make, R.string.model, R.string.generation, R.string.vin, R.string.registration_number, R.string.engine_code).forEachIndexed { index, label -> OutlinedTextField(value = details[index], onValueChange = { detail(index, it) }, label = { Text(stringResource(label)) }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
        VehicleHints(details[0], details[1], details[3], details[4]) { index, value -> detail(index, value) }
        val unknown = "" to stringResource(R.string.unknown_value)
        Choice(stringResource(R.string.gearbox_type), details[6], listOf(unknown, "MANUAL" to stringResource(R.string.gearbox_manual), "AUTOMATIC" to stringResource(R.string.gearbox_automatic), "CVT" to stringResource(R.string.gearbox_cvt), "DCT" to stringResource(R.string.gearbox_dct), "OTHER" to stringResource(R.string.other_value)), "gearbox_type") { detail(6, it) }
        OutlinedTextField(value = details[7], onValueChange = { detail(7, it) }, label = { Text(stringResource(R.string.gearbox_code)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Choice(stringResource(R.string.powertrain), details[8], listOf(unknown, "PETROL" to stringResource(R.string.powertrain_petrol), "DIESEL" to stringResource(R.string.powertrain_diesel), "HYBRID" to stringResource(R.string.powertrain_hybrid), "PHEV" to stringResource(R.string.powertrain_phev), "EV" to stringResource(R.string.powertrain_ev), "OTHER" to stringResource(R.string.other_value)), "powertrain") { detail(8, it) }
        OutlinedTextField(value = details[9], onValueChange = { detail(9, it) }, label = { Text(stringResource(R.string.market)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Choice(stringResource(R.string.service_regime), details[10], listOf(unknown, "FIXED" to stringResource(R.string.regime_fixed), "VARIABLE" to stringResource(R.string.regime_variable)), "service_regime") { detail(10, it) }
        Choice(stringResource(R.string.usage_profile), details[11], listOf(unknown, "NORMAL" to stringResource(R.string.usage_normal), "SEVERE" to stringResource(R.string.usage_severe)), "usage_profile") { detail(11, it) }
    }
    problem?.let { Text(stringResource(when (it) { ProfileProblem.NAME_REQUIRED -> R.string.name_required; ProfileProblem.NAME_TOO_LONG -> R.string.name_too_long; ProfileProblem.INVALID_YEAR -> R.string.invalid_year }), color = MaterialTheme.colorScheme.error) }
    ErrorPanel(state, issue)
    PrimaryButton(enabled = !state.saving, onClick = {
        problem = ProfileValidation.problem(name, year, today.year)
        if (problem == null) try {
            val firstDate = LocalDates.day(registrationDate, locale, today, true)
            save(ProfileInput(name, year.toIntOrNull(), firstDate, details[0], details[1], details[2], details[3], details[4], details[5], details[6], details[7], details[8], details[9], details[10], details[11]))
        } catch (e: InputRejected) { issue = e.problem }
    }, modifier = Modifier.fillMaxWidth().testTag("save_car")) { Text(stringResource(if (state.saving) R.string.saving else R.string.save_car)) }
}

/**
 * A photo of the instrument cluster instead of typing: the number read from it is put into the mileage field, where
 * the driver sees it and saves it like a typed one. Nothing is saved from the photo alone, and the photo is not kept.
 */
@Composable private fun OdometerFromPhoto(reader: DocumentAnalyzer, store: DocumentStore, lastKnownKm: Int?, locale: String, openCameraAtOnce: Boolean, onRead: (Int) -> Unit) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var noCamera by remember { mutableStateOf(false) }
    // What the last photo gave: null before any photo, an empty list when nothing could be read.
    var offered by remember { mutableStateOf<List<OdometerCandidate>?>(null) }
    var chosen by rememberSaveable { mutableStateOf<Int?>(null) }
    // The photo always lands in the same private file, so it is read even if Android restarted the app meanwhile.
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { taken ->
        if (taken) { busy = true; offered = null
            scope.launch {
                try {
                    val found = reader.odometer(store.odometerPhotoUri(), lastKnownKm)
                    offered = found; chosen = found.firstOrNull()?.km
                    found.firstOrNull()?.let { onRead(it.km) }
                } finally { store.discardOdometerPhoto(); busy = false }
            }
        }
    }
    fun openCamera() { noCamera = false; try { camera.launch(store.odometerPhotoTarget()) } catch (_: Exception) { noCamera = true } }
    // Coming from the camera symbol or the mileage on the first screen: the camera opens by itself, once. Rebuilding
    // the screen (the phone is turned, the app comes back) must not open it again.
    var openedAtOnce by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) { if (openCameraAtOnce && !openedAtOnce) { openedAtOnce = true; openCamera() } }
    QuietButton(enabled = !busy, modifier = Modifier.fillMaxWidth().testTag("odometer_photo"), onClick = { openCamera() }) { Text(stringResource(R.string.odometer_photo)) }
    if (noCamera) Text(stringResource(R.string.import_no_camera), color = MaterialTheme.colorScheme.error)
    if (busy) { CircularProgressIndicator(); Text(stringResource(R.string.odometer_photo_reading)) }
    offered?.let { found ->
        if (found.isEmpty()) Text(stringResource(R.string.odometer_photo_nothing), color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("odometer_photo_nothing"))
        else Group(Modifier.testTag("odometer_photo_result"), accent = Cockpit.cyan) {
            Text(stringResource(R.string.odometer_photo_found, formattedKm(found.first { it.km == (chosen ?: found.first().km) }.km.toLong(), locale)), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.odometer_photo_check), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            // Other numbers from the same photo, in case the first one was not the mileage.
            if (found.size > 1) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                found.forEach { candidate ->
                    FilterChip(selected = candidate.km == chosen, onClick = { chosen = candidate.km; onRead(candidate.km) }, label = { Text(formattedKm(candidate.km.toLong(), locale) + " km") }, modifier = Modifier.testTag("odometer_photo_choice"))
                }
            }
        }
    }
}
