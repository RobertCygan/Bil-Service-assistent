package app.servicepassport.android.ui

import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.compose.ui.geometry.Size
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.servicepassport.android.R
import app.servicepassport.android.dashboard.*
import app.servicepassport.android.data.WorkInput
import app.servicepassport.android.data.LocalDates
import app.servicepassport.engine.*
import java.time.format.DateTimeFormatter
import java.util.Locale

internal fun categoryResource(category: Category) = when (category) {
    Category.OVERDUE -> R.string.group_overdue
    Category.DUE_SOON -> R.string.group_due_soon
    Category.NEEDS_DATA -> R.string.group_needs_data
    Category.NO_URGENCY -> R.string.group_no_urgency
}

private fun reasonResource(reason: DashboardReason) = when (reason) {
    DashboardReason.CONFIRMED_ACTION_MISSING -> R.string.dashboard_missing_confirmation
    DashboardReason.ODOMETER_MISSING -> R.string.dashboard_odometer_missing
    DashboardReason.LAST_SERVICE_ODOMETER_MISSING -> R.string.dashboard_service_km_missing
    DashboardReason.IN_SERVICE_DATE_MISSING -> R.string.dashboard_registration_missing
    DashboardReason.VARIABLE_SCHEDULE -> R.string.dashboard_variable_schedule
    DashboardReason.ODOMETER_STALE -> R.string.dashboard_odometer_stale
    DashboardReason.ODOMETER_UPDATE_REQUIRED -> R.string.dashboard_odometer_update
    DashboardReason.ODOMETER_INCONSISTENT -> R.string.dashboard_odometer_inconsistent
    DashboardReason.NEWER_ODOMETER_IN_HISTORY -> R.string.dashboard_newer_reading
    DashboardReason.SOURCE_EXPIRED -> R.string.dashboard_source_expired
    DashboardReason.FUTURE_ENTRIES_IGNORED -> R.string.dashboard_future_ignored
    DashboardReason.CALCULATION_LIMIT -> R.string.dashboard_calculation_limit
}

/** The four groups as tiles: the number in the group's colour, a coloured edge, brighter when there is something in it. */
@Composable internal fun DashboardCounts(dashboard: DashboardState, onClick: (() -> Unit)? = null) {
    Column(Modifier.fillMaxWidth().testTag("dashboard_counts"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf(listOf(Category.OVERDUE, Category.DUE_SOON), listOf(Category.NEEDS_DATA, Category.NO_URGENCY)).forEach { categories ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                categories.forEach { category ->
                    val count = dashboard.tasks.count { it.category == category }
                    val accent = LocalAppColors.current.status(category).accent; val shape = MaterialTheme.shapes.small
                    val glow = Brush.radialGradient(listOf(accent.copy(alpha = if (count > 0) 0.30f else 0.12f), accent.copy(alpha = 0.03f)), center = Offset(0f, 0f), radius = 600f)
                    Column(
                        // Read out as one item ("3, Overdue"), whether or not the tile can be tapped.
                        Modifier.weight(1f).testTag("count_${category.name}").semantics(mergeDescendants = true) {}.clip(shape).background(glow).border(1.dp, accent.copy(alpha = if (count > 0) 0.9f else 0.35f), shape)
                            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                            .heightIn(min = 86.dp).padding(horizontal = 10.dp, vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                    ) {
                        Text(count.toString(), color = accent, fontSize = 32.sp, fontWeight = FontWeight.Medium)
                        Text(stringResource(categoryResource(category)), color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }
}

@Composable internal fun CatalogNotice(state: DashboardState, go: (String) -> Unit) {
    if (state.catalogState == CatalogState.MATCHED) return
    val message = when (state.catalogState) {
        CatalogState.NOT_AVAILABLE -> R.string.catalog_not_available
        CatalogState.NO_MATCH -> R.string.catalog_no_match
        CatalogState.CONFIGURATION_MISSING -> R.string.catalog_configuration_missing
        CatalogState.AMBIGUOUS -> R.string.catalog_ambiguous
        CatalogState.SOURCE_UNAVAILABLE -> R.string.catalog_source_unavailable
        CatalogState.INVALID_CATALOG -> R.string.catalog_unavailable
        CatalogState.MATCHED -> R.string.catalog_not_available
    }
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(R.string.no_verified_schedule), style = MaterialTheme.typography.titleMedium)
            }
            Text(stringResource(message), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (state.missingConfiguration.isNotEmpty()) {
                state.missingConfiguration.forEach { field -> Text(stringResource(when (field) {
                    ConfigAttribute.MAKE -> R.string.make; ConfigAttribute.MODEL -> R.string.model
                    ConfigAttribute.GENERATION -> R.string.generation; ConfigAttribute.MODEL_YEAR -> R.string.configuration_model_year
                    ConfigAttribute.POWERTRAIN -> R.string.powertrain; ConfigAttribute.ENGINE_CODE -> R.string.engine_code
                    ConfigAttribute.GEARBOX_TYPE -> R.string.gearbox_type; ConfigAttribute.GEARBOX_CODE -> R.string.gearbox_code
                    ConfigAttribute.MARKET -> R.string.market; ConfigAttribute.SERVICE_REGIME -> R.string.service_regime
                    ConfigAttribute.USAGE_PROFILE -> R.string.usage_profile
                })) }
                TextButton(onClick = { go("vehicle") }) { Text(stringResource(R.string.edit_car)) }
            }
        }
    }
}

private fun originResource(origin: TaskOrigin): Int = when (origin) {
    TaskOrigin.USER -> R.string.user_interval_source
    TaskOrigin.MANUFACTURER -> R.string.manufacturer_interval_source
    TaskOrigin.VEHICLE_DISPLAY -> R.string.indicator_source
}

@Composable internal fun DashboardPage(state: GarageState, go: (String) -> Unit, retry: () -> Unit) = Page {
    Text(stringResource(R.string.service_overview), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.testTag("dashboard_title"))
    val dashboard = state.dashboard
    if (state.loading) { CircularProgressIndicator(); return@Page }
    if (state.problem == StorageProblem.READ) {
        Text(stringResource(R.string.read_error))
        Button(onClick = retry) { Text(stringResource(R.string.retry)) }
        return@Page
    }
    if (state.profile == null || dashboard == null) { Text(stringResource(R.string.no_history_without_car)); return@Page }
    Text(requireNotNull(state.profile).displayName, color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (dashboard.calculationUnavailable) {
        NoteCard(stringResource(R.string.dashboard_unavailable))
        Button(onClick = retry) { Text(stringResource(R.string.retry)) }
        return@Page
    }
    // The missing manufacturer schedule is the first thing to know about every figure below.
    CatalogNotice(dashboard, go)
    DashboardCounts(dashboard)
    FilledTonalButton(onClick = { go("intervals") }, modifier = Modifier.fillMaxWidth().testTag("manage_intervals")) { Text(stringResource(R.string.your_intervals)) }
    OutlinedButton(onClick = { go("indicator") }, modifier = Modifier.fillMaxWidth().testTag("open_indicator")) { Text(stringResource(R.string.indicator_title)) }
    if (dashboard.tasks.isEmpty()) NoteCard(stringResource(R.string.no_intervals_yet))
    Category.entries.forEach { category ->
        val tasks = dashboard.tasks.filter { it.category == category }
        if (tasks.isNotEmpty()) {
            Text(stringResource(categoryResource(category)), style = MaterialTheme.typography.titleLarge, color = LocalAppColors.current.status(category).accent)
            tasks.forEach { task -> TaskCard(task, compact = false, modifier = Modifier.testTag("forecast_${task.id}")) { go("forecast/${Uri.encode(task.id)}") } }
        }
    }
}

/** How far the interval has been used, from the engine's own figures. Null when the engine could not calculate. */
internal fun usedFraction(task: DashboardTask): Float? {
    val interval = task.basis?.interval ?: return null
    val byDistance = interval.km?.takeIf { it > 0 }?.let { km -> task.remainingKm?.let { 1f - it.toFloat() / km } }
    val byTime = interval.months?.takeIf { it > 0 }?.let { months -> task.remainingDays?.let { 1f - it.toFloat() / (months * 30.44f) } }
    return listOfNotNull(byDistance, byTime).maxOrNull()?.coerceIn(0f, 1f)
}

@Composable internal fun remainingText(task: DashboardTask): String? {
    val locale = uiLocale()
    val parts = listOfNotNull(
        task.remainingKm?.let { stringResource(if (it >= 0) R.string.km_left else R.string.km_over, formattedKm(kotlin.math.abs(it.toLong()), locale)) },
        task.remainingDays?.let { stringResource(if (it >= 0) R.string.days_left else R.string.days_over, kotlin.math.abs(it).toString()) },
    )
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

/** One service task: what it is, how urgent, and how much of the interval is used. */
@Composable internal fun TaskCard(task: DashboardTask, compact: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val palette = LocalAppColors.current.status(task.category); val scheme = MaterialTheme.colorScheme
    Surface(onClick = onClick, color = scheme.surfaceContainerLow, shape = MaterialTheme.shapes.medium, border = BorderStroke(1.dp, scheme.outlineVariant), modifier = modifier.fillMaxWidth()) {
        Column(
            Modifier.drawBehind { drawRect(palette.accent, size = Size(6.dp.toPx(), size.height)) }.padding(start = 20.dp, end = 16.dp, top = 14.dp, bottom = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(workLabel(WorkInput(task.task.action.code, task.task.operation.code)), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (compact) Surface(color = palette.container, contentColor = palette.onContainer, shape = MaterialTheme.shapes.extraSmall) {
                    Text(stringResource(categoryResource(task.category)), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                }
            }
            remainingText(task)?.let { Text(it, style = MaterialTheme.typography.bodyLarge, color = palette.accent) }
            usedFraction(task)?.let { LinearProgressIndicator(progress = { it }, color = palette.accent, trackColor = palette.container, drawStopIndicator = {}, modifier = Modifier.fillMaxWidth().height(6.dp)) }
            if (!compact) {
                Text(stringResource(originResource(task.origin)), style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
                DueValues(task)
            } else if (task.dueKm == null && task.dueDate == null) Text(stringResource(R.string.no_confirmed_due_value), style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
            task.reasons.forEach { Text(stringResource(reasonResource(it)), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant) }
        }
    }
}

@Composable private fun DueValues(task: DashboardTask) {
    val locale = uiLocale()
    task.dueKm?.let { Text(stringResource(R.string.due_at_km, formattedKm(it.toLong(), locale))) }
    task.dueDate?.let { Text(stringResource(R.string.due_on_date, LocalDates.text(it.toEpochDay(), locale))) }
    if (task.dueKm == null && task.dueDate == null) Text(stringResource(R.string.no_confirmed_due_value))
}

@Composable internal fun ForecastDetails(state: GarageState, id: String, go: (String) -> Unit) = Page {
    if (state.loading) { CircularProgressIndicator(); return@Page }
    if (state.problem == StorageProblem.READ) { Text(stringResource(R.string.read_error)); return@Page }
    val task = state.dashboard?.tasks?.firstOrNull { it.id == id }
    if (task == null) { Text(stringResource(R.string.entry_not_found)); return@Page }
    val locale = uiLocale()
    Text(workLabel(WorkInput(task.task.action.code, task.task.operation.code)), style = MaterialTheme.typography.headlineMedium)
    Text(stringResource(categoryResource(task.category)), style = MaterialTheme.typography.titleLarge, modifier = Modifier.testTag("forecast_category"))
    Text(stringResource(originResource(task.origin)))
    when (task.origin) {
        TaskOrigin.USER -> NoteCard(stringResource(R.string.user_interval_explanation))
        TaskOrigin.VEHICLE_DISPLAY -> NoteCard(stringResource(R.string.indicator_explanation))
        TaskOrigin.MANUFACTURER -> Text(stringResource(R.string.source_version, task.source.title, task.source.version, LocalDates.text(task.source.verifiedOn.toEpochDay(), locale)))
    }
    DueValues(task)
    task.remainingKm?.let { Text(stringResource(R.string.distance_to_due, formattedKm(it.toLong(), locale))) }
    task.remainingDays?.let { Text(stringResource(R.string.days_to_due, it.toString())) }
    task.reasons.forEach { NoteCard(stringResource(reasonResource(it))) }
    Text(stringResource(R.string.calculation_basis), style = MaterialTheme.typography.titleLarge)
    val basis = task.basis
    if (basis == null) Text(stringResource(R.string.no_confirmed_due_value)) else {
        Text(stringResource(when (basis.anchorKind) { AnchorKind.LAST_CONFIRMED_TASK -> R.string.basis_confirmed_action; AnchorKind.VEHICLE_START -> R.string.basis_vehicle_start; AnchorKind.VEHICLE_INDICATOR -> R.string.basis_indicator }))
        basis.anchorDate?.let { Text(LocalDates.text(it.toEpochDay(), locale)) }
        basis.anchorKm?.let { Text(stringResource(R.string.basis_km, formattedKm(it.toLong(), locale))) }
        Text(stringResource(when (basis.intervalKind) {
            IntervalKind.FIRST -> R.string.interval_kind_first
            IntervalKind.REPEAT -> R.string.interval_kind_repeat
            IntervalKind.UPPER_LIMIT -> R.string.interval_kind_upper_limit
            IntervalKind.INDICATOR -> R.string.interval_kind_indicator
        }))
        basis.interval?.let { interval ->
            interval.km?.let { Text(stringResource(R.string.interval_km_value, formattedKm(it.toLong(), locale))) }
            interval.months?.let { Text(stringResource(R.string.interval_months_value, it.toString())) }
            if (interval.km != null && interval.months != null) Text(stringResource(R.string.whichever_first))
        }
        basis.odometerUsed?.let { Text(stringResource(R.string.basis_current_reading, formattedKm(it.km.toLong(), locale), LocalDates.text(it.date.toEpochDay(), locale))) }
    }
    task.estimatedMonth?.let { month ->
        NoteCard(stringResource(R.string.estimated_month, month.format(DateTimeFormatter.ofPattern("LLLL uuuu", Locale.forLanguageTag(locale)))))
    }
    Button(onClick = { go("service/new") }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.add_service)) }
    OutlinedButton(onClick = { go("reading/new") }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.update_odometer)) }
    if (task.origin == TaskOrigin.USER) TextButton(onClick = { go("interval/edit/${task.id.removePrefix("user:")}") }) { Text(stringResource(R.string.edit_interval)) }
    if (task.origin == TaskOrigin.VEHICLE_DISPLAY || task.task.taskCode == "inspection_service:inspect") TextButton(onClick = { go("indicator") }) { Text(stringResource(R.string.indicator_title)) }
}
