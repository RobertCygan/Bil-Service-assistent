package app.servicepassport.android.ui

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.servicepassport.android.R
import app.servicepassport.android.dashboard.DashboardState
import app.servicepassport.android.dashboard.DashboardTask
import app.servicepassport.android.data.GarageSnapshot
import app.servicepassport.android.data.LocalDates
import app.servicepassport.android.data.WorkInput
import app.servicepassport.contract.AppPolicy
import app.servicepassport.engine.Category
import java.time.LocalDate
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Colours taken from the owner's design of the first screen. */
internal object Cockpit {
    val background = Color(0xFF060A12)
    val backgroundLow = Color(0xFF09131F)
    val cyan = Color(0xFF3FD9F7)
    val orange = Color(0xFFFFA42B)
    val red = Color(0xFFFF4B33)
    val steel = Color(0xFF5B7089)
    val track = Color(0xFF1B2939)
    val text = Color(0xFFF3F6FA)
    val muted = Color(0xFF93A0B0)
}

/** What the gauge says first. Something already overdue always comes before what is still ahead. */
internal enum class Headline { OVERDUE_KM, OVERDUE_DAYS, NEXT_KM, NONE }

/** The figures of the first screen, worked out from the same result the service overview shows. Nothing is estimated here. */
internal data class CockpitFigures(
    val currentKm: Long?,
    /** Mileage of the most recent service entry. */
    val lastServiceKm: Long?,
    /** The lowest distance limit that has already been passed. */
    val overdueLimitKm: Long?,
    /** The nearest distance limit still ahead. */
    val upcomingLimitKm: Long?,
    val headline: Headline,
    val headlineValue: Long?,
    val headlineCategory: Category?,
    /** How far round the gauge goes, 0..1; null when there is nothing to show. */
    val gaugeFraction: Float?,
    /** Where the current mileage sits on the arc between its two ends, 0..1; null when it cannot be placed. */
    val arcFraction: Float?,
) {
    /** The left end of the arc: an overdue limit when there is one, otherwise the last service. */
    val arcStartKm: Long? get() = overdueLimitKm ?: lastServiceKm?.takeIf { currentKm != null && it <= currentKm }
    val arcStartsOverdue: Boolean get() = overdueLimitKm != null
}

internal fun cockpitFigures(snapshot: GarageSnapshot, dashboard: DashboardState?): CockpitFigures {
    val current = snapshot.currentReading?.km
    val last = snapshot.history.firstOrNull()?.reading?.km
    val tasks = dashboard?.takeIf { !it.calculationUnavailable }?.tasks.orEmpty()
    val byDistance = tasks.filter { it.dueKm != null && it.remainingKm != null }
    val passed = byDistance.filter { it.remainingKm!! < 0 }
    val next = byDistance.filter { it.remainingKm!! >= 0 }.minByOrNull { it.remainingKm!! }
    val overdueLimit = passed.minOfOrNull { it.dueKm!! }?.toLong()
    val daysOver = tasks.filter { it.category == Category.OVERDUE }.mapNotNull { it.remainingDays }.filter { it < 0 }.minOrNull()

    val headline = when {
        passed.isNotEmpty() -> Headline.OVERDUE_KM
        daysOver != null -> Headline.OVERDUE_DAYS
        next != null -> Headline.NEXT_KM
        else -> Headline.NONE
    }
    val value = when (headline) {
        Headline.OVERDUE_KM -> -passed.minOf { it.remainingKm!! }.toLong()
        Headline.OVERDUE_DAYS -> -daysOver!!
        Headline.NEXT_KM -> next!!.remainingKm!!.toLong()
        Headline.NONE -> null
    }
    val upcoming = next?.dueKm?.toLong()
    val start = overdueLimit ?: last?.takeIf { current != null && it <= current }
    val arc = when {
        current == null -> null
        start != null && upcoming != null && upcoming > start -> ((current - start).toFloat() / (upcoming - start)).coerceIn(0f, 1f)
        overdueLimit != null && upcoming == null -> 1f
        start == null && upcoming != null -> 0f
        else -> null
    }
    val gauge = when (headline) {
        Headline.OVERDUE_KM, Headline.OVERDUE_DAYS -> 1f
        Headline.NEXT_KM -> usedFraction(next!!)
            ?: last?.takeIf { current != null && upcoming != null && upcoming > it }?.let { ((current!! - it).toFloat() / (upcoming!! - it)).coerceIn(0f, 1f) }
        Headline.NONE -> null
    }
    return CockpitFigures(
        currentKm = current, lastServiceKm = last, overdueLimitKm = overdueLimit, upcomingLimitKm = upcoming,
        headline = headline, headlineValue = value,
        headlineCategory = when (headline) { Headline.OVERDUE_KM, Headline.OVERDUE_DAYS -> Category.OVERDUE; Headline.NEXT_KM -> next!!.category; Headline.NONE -> null },
        gaugeFraction = gauge, arcFraction = arc,
    )
}

/** The short opening movement is shown once per start of the app, not every time the screen comes back. */
private var introPlayed = false

@Composable internal fun CockpitOverview(snapshot: GarageSnapshot, dashboard: DashboardState?, today: LocalDate, go: (String) -> Unit) {
    val figures = remember(snapshot, dashboard) { cockpitFigures(snapshot, dashboard) }
    // Numbers are final from the first frame; only the arc and the needle move. Android's "remove animations"
    // setting makes this instant.
    val reveal = remember { Animatable(if (introPlayed) 1f else 0f) }
    LaunchedEffect(Unit) { introPlayed = true; reveal.animateTo(1f, tween(900, easing = FastOutSlowInEasing)) }
    val locale = uiLocale(); val vehicle = snapshot.vehicle

    Box(Modifier.fillMaxSize().cockpitBackdrop()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            val car: @Composable (Modifier) -> Unit = { modifier ->
                Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    FittingText(vehicle.displayName, TextStyle(fontSize = 30.sp, fontWeight = FontWeight.Bold), Cockpit.text, 18.sp, Modifier.testTag("profile_name"))
                    val model = listOfNotNull(vehicle.make, vehicle.model).joinToString(" ")
                    if (model.isNotEmpty()) Text(model, color = Cockpit.text.copy(alpha = 0.85f), fontSize = 17.sp)
                    vehicle.modelYear?.let { Text(stringResource(R.string.model_year_value, it.toString()), color = Cockpit.muted, fontSize = 13.sp) }
                    Text(stringResource(R.string.edit_car), color = Cockpit.cyan, style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.testTag("edit_car").minimumInteractiveComponentSize().clip(MaterialTheme.shapes.extraSmall).clickable(role = Role.Button) { go("vehicle") }.padding(vertical = 10.dp))
                }
            }
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                // Room is judged in text units: large text needs more width for the same words.
                if (maxWidth / LocalDensity.current.fontScale >= 300.dp) Row(verticalAlignment = Alignment.Top) {
                    car(Modifier.weight(1f).padding(top = 20.dp))
                    ServiceGauge(figures, reveal.value, Modifier.width(176.dp))
                } else Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    car(Modifier.fillMaxWidth())
                    ServiceGauge(figures, reveal.value, Modifier.width(200.dp).align(Alignment.CenterHorizontally))
                }
            }

            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                // Every line about the mileage opens the camera for a photo of the odometer. Each line stays its own
                // element, so it can still be read and found on its own.
                val photo = stringResource(R.string.odometer_photo)
                val toCamera = Modifier.clip(MaterialTheme.shapes.extraSmall).clickable(onClickLabel = photo, role = Role.Button) { go("reading/photo") }
                Row(toCamera.testTag("odometer_label"), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(painterResource(R.drawable.ic_camera), contentDescription = null, tint = Cockpit.cyan, modifier = Modifier.size(14.dp))
                    Text(stringResource(R.string.current_odometer).uppercase(), color = Cockpit.muted, fontSize = 11.sp, letterSpacing = 2.sp)
                }
                val reading = snapshot.currentReading
                if (reading == null) Text(stringResource(R.string.odometer_missing), color = Cockpit.cyan, style = MaterialTheme.typography.titleMedium, modifier = toCamera.testTag("no_current_reading").padding(vertical = 10.dp, horizontal = 8.dp)) else {
                    BasicText(
                        buildAnnotatedString {
                            append(formattedKm(reading.km, locale))
                            withStyle(SpanStyle(fontSize = 16.sp, fontWeight = FontWeight.Normal, color = Cockpit.muted)) { append(" km") }
                        },
                        toCamera.testTag("current_km"), TextStyle(color = Cockpit.text, fontSize = 44.sp, fontWeight = FontWeight.Bold), maxLines = 1, softWrap = false,
                    )
                    Text(LocalDates.text(reading.observedDay, locale), color = Cockpit.muted, fontSize = 12.sp, modifier = toCamera.testTag("current_reading_date"))
                }
            }

            ServiceArc(figures, reveal.value, locale)

            snapshot.currentReading?.let { reading ->
                if (today.toEpochDay() - reading.observedDay > AppPolicy.ODOMETER_STALE_AFTER_DAYS) Text(stringResource(R.string.odometer_stale), color = Cockpit.orange, style = MaterialTheme.typography.bodySmall)
                if (reading.exceptionReason.isNotEmpty()) Text(stringResource(R.string.exception_recorded), color = Cockpit.muted, style = MaterialTheme.typography.bodySmall)
            }

            val tasks = dashboard?.takeIf { !it.calculationUnavailable }?.tasks.orEmpty()
            val urgent = tasks.filter { it.category == Category.OVERDUE || it.category == Category.DUE_SOON }.sortedBy { it.category.ordinal }.take(4)
            if (urgent.isNotEmpty()) Column(Modifier.fillMaxWidth().testTag("urgent_tasks")) {
                urgent.forEach { task -> UrgentRow(task) { go("forecast/${android.net.Uri.encode(task.id)}") } }
            }
            if (figures.lastServiceKm != null && figures.arcStartsOverdue) {
                Text(stringResource(R.string.last_service_km, formattedKm(figures.lastServiceKm, locale)), color = Cockpit.muted, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.service_overview), color = Cockpit.text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = { go("dashboard") }, modifier = Modifier.testTag("open_dashboard")) { Text(stringResource(R.string.see_all)) }
            }
            if (dashboard != null) {
                if (dashboard.calculationUnavailable) NoteCard(stringResource(R.string.dashboard_unavailable)) else {
                    DashboardCounts(dashboard) { go("dashboard") }
                    if (dashboard.tasks.isEmpty()) GettingStarted(go)
                }
            }

            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CockpitAction(stringResource(R.string.add_service), primary = true, Modifier.weight(1f).fillMaxHeight().testTag("add_service")) { go("service/new") }
                CockpitAction(stringResource(R.string.import_document), primary = false, Modifier.weight(1f).fillMaxHeight().testTag("import_document")) { go("import") }
            }
            val update = stringResource(R.string.update_odometer); val readings = stringResource(R.string.reading_history)
            val measurer = rememberTextMeasurer(); val labelStyle = MaterialTheme.typography.labelLarge
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                // Next to each other only when both labels fit in full; a label is never shortened.
                val widest = with(LocalDensity.current) { listOf(update, readings).maxOf { measurer.measure(it, labelStyle, maxLines = 1).size.width }.toDp() }
                val buttons: @Composable (Modifier) -> Unit = { modifier ->
                    QuietButton(onClick = { go("reading/new") }, modifier = modifier.testTag("add_reading")) { Text(update) }
                    QuietButton(onClick = { go("readings") }, modifier = modifier.testTag("reading_history")) { Text(readings) }
                }
                if (widest + 48.dp <= (maxWidth - 12.dp) / 2) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) { buttons(Modifier.weight(1f)) }
                else Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { buttons(Modifier.fillMaxWidth()) }
            }
            if (dashboard != null && !dashboard.calculationUnavailable) CatalogNotice(dashboard, go)
        }
    }
}

/** One line that shrinks before it would wrap: a mileage or a name must not be broken in the middle. */
@Composable internal fun FittingText(text: String, style: TextStyle, color: Color, smallest: TextUnit, modifier: Modifier = Modifier) {
    BasicText(text, modifier, style.copy(color = color), maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis,
        autoSize = TextAutoSize.StepBased(minFontSize = smallest, maxFontSize = style.fontSize, stepSize = 1.sp))
}

/** A new car has nothing to calculate from. Both ways of getting a first result are one tap away. */
@Composable private fun GettingStarted(go: (String) -> Unit) {
    Panel(Modifier.fillMaxWidth().testTag("getting_started")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.getting_started), style = MaterialTheme.typography.bodyMedium)
            PrimaryButton(onClick = { go("indicator") }, modifier = Modifier.fillMaxWidth().testTag("start_indicator")) { Text(stringResource(R.string.indicator_title)) }
            QuietButton(onClick = { go("interval/new") }, modifier = Modifier.fillMaxWidth().testTag("start_interval")) { Text(stringResource(R.string.add_interval)) }
        }
    }
}

@Composable private fun CockpitAction(label: String, primary: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val accent = if (primary) Cockpit.cyan else Cockpit.steel
    Surface(onClick = onClick, color = accent.copy(alpha = if (primary) 0.14f else 0.10f), contentColor = Cockpit.text, shape = MaterialTheme.shapes.small,
        border = androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = 0.7f)), modifier = modifier) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 14.dp).heightIn(min = 28.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(if (primary) Icons.Default.Add else Icons.Default.Search, contentDescription = null, tint = if (primary) Cockpit.cyan else Cockpit.text)
            Text(label, style = MaterialTheme.typography.titleSmall)
        }
    }
}

/** An item that needs attention: what it is and by how much, large enough to read at a glance. */
@Composable private fun UrgentRow(task: DashboardTask, onClick: () -> Unit) {
    val accent = LocalAppColors.current.status(task.category).accent
    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.extraSmall).clickable(role = Role.Button, onClick = onClick).heightIn(min = 48.dp).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(9.dp).background(accent, CircleShape))
        Column(Modifier.weight(1f)) {
            Text(workLabel(WorkInput(task.task.action.code, task.task.operation.code)), color = Cockpit.text, style = MaterialTheme.typography.bodyLarge)
            Text(remainingText(task) ?: stringResource(categoryResource(task.category)), color = accent, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}

private fun statusColour(category: Category?): Color = when (category) {
    Category.OVERDUE -> Cockpit.red
    Category.DUE_SOON -> Cockpit.orange
    else -> Cockpit.cyan
}

private const val GAUGE_START = 120f
private const val GAUGE_SWEEP = 300f

/** The round gauge: how much is left to the nearest limit, or by how much a limit has been passed. */
@Composable private fun ServiceGauge(figures: CockpitFigures, reveal: Float, modifier: Modifier) {
    val locale = uiLocale()
    val colour = statusColour(figures.headlineCategory)
    val label = stringResource(when (figures.headline) { Headline.OVERDUE_KM, Headline.OVERDUE_DAYS -> R.string.overdue_by; else -> R.string.to_next_service })
    val value = figures.headlineValue?.let { formattedKm(it, locale) } ?: stringResource(R.string.no_value)
    val unit = if (figures.headline == Headline.OVERDUE_DAYS) pluralStringResource(R.plurals.unit_days, figures.headlineValue!!.toInt()) else "km"
    val lastService = figures.lastServiceKm?.let { stringResource(R.string.last_service_km, formattedKm(it, locale)) }
    val spoken = listOfNotNull("$label $value $unit", lastService).joinToString(". ")
    Box(modifier.aspectRatio(1f).semantics(mergeDescendants = true) { contentDescription = spoken }.testTag("service_gauge"), contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val radius = size.minDimension / 2 - 22.dp.toPx()
            val box = Rect(center.x - radius, center.y - radius, center.x + radius, center.y + radius)
            drawArc(Cockpit.track, GAUGE_START, GAUGE_SWEEP, false, box.topLeft, box.size, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
            // Scale marks inside the ring.
            for (step in 0..50) {
                val angle = Math.toRadians((GAUGE_START + GAUGE_SWEEP * step / 50).toDouble())
                val major = step % 5 == 0
                val outer = radius - 7.dp.toPx(); val inner = outer - (if (major) 8 else 4).dp.toPx()
                drawLine(Cockpit.steel.copy(alpha = if (major) 0.9f else 0.45f),
                    Offset(center.x + inner * cos(angle).toFloat(), center.y + inner * sin(angle).toFloat()),
                    Offset(center.x + outer * cos(angle).toFloat(), center.y + outer * sin(angle).toFloat()), strokeWidth = 1.dp.toPx())
            }
            val fraction = figures.gaugeFraction
            if (fraction != null) {
                val sweep = GAUGE_SWEEP * fraction * reveal
                listOf(20.dp to 0.06f, 13.dp to 0.12f, 8.dp to 0.24f, 4.5.dp to 1f).forEach { (width, alpha) ->
                    drawArc(colour.copy(alpha = alpha), GAUGE_START, sweep, false, box.topLeft, box.size, style = Stroke(width.toPx(), cap = StrokeCap.Round))
                }
                val angle = Math.toRadians((GAUGE_START + sweep).toDouble())
                val tip = Offset(center.x + (radius - 3.dp.toPx()) * cos(angle).toFloat(), center.y + (radius - 3.dp.toPx()) * sin(angle).toFloat())
                // The pointer starts outside the figures, so it never crosses them wherever it points.
                val from = Offset(center.x + radius * 0.62f * cos(angle).toFloat(), center.y + radius * 0.62f * sin(angle).toFloat())
                drawLine(Cockpit.text, from, tip, strokeWidth = 2.5.dp.toPx(), cap = StrokeCap.Round)
                drawCircle(Cockpit.text, 2.5.dp.toPx(), tip)
                drawCircle(Cockpit.text, 4.dp.toPx(), center, style = Stroke(1.5.dp.toPx()))
            }
            if (lastService != null) {
                // Written round the top of the ring, as on the design.
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Cockpit.text.copy(alpha = 0.85f).toArgb(); textSize = min(11.sp.toPx(), 15.dp.toPx()); textAlign = Paint.Align.CENTER; letterSpacing = 0.06f }
                val r = radius + 9.dp.toPx()
                val path = android.graphics.Path().apply { addArc(center.x - r, center.y - r, center.x + r, center.y + r, 200f, 140f) }
                val room = (Math.toRadians(140.0) * r).toFloat()
                val width = paint.measureText(lastService); if (width > room) paint.textSize *= room / width
                drawIntoCanvas { it.nativeCanvas.drawTextOnPath(lastService, path, 0f, 0f, paint) }
            }
        }
        // The caption sits above the centre and the figure below it, with the hub between them.
        Column(Modifier.matchParentSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.weight(1f).fillMaxWidth(0.5f), contentAlignment = Alignment.BottomCenter) {
                FittingText(label.uppercase(), TextStyle(fontSize = 9.sp, letterSpacing = 1.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center), Cockpit.text.copy(alpha = 0.8f), 6.sp, Modifier.padding(bottom = 12.dp))
            }
            Column(Modifier.weight(1f).fillMaxWidth(0.56f), horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(8.dp))
                FittingText(value, TextStyle(fontSize = 25.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center), if (figures.headlineCategory == Category.OVERDUE) Cockpit.red else Cockpit.text, 13.sp, Modifier.testTag("gauge_value"))
                Text(unit, color = Cockpit.muted, fontSize = 11.sp)
            }
        }
    }
}

/** Height of the arc relative to the distance between its ends, measured on the design. */
private const val ARC_RISE = 0.335f

/**
 * The wide arc: the current mileage between the limit behind it and the limit ahead. The colour runs from the left
 * end to the marker and on to the right end, so the marker is always where the warm colour is.
 */
@Composable private fun ServiceArc(figures: CockpitFigures, reveal: Float, locale: String) {
    val start = figures.arcStartKm
    val startCaption = start?.let { stringResource(if (figures.arcStartsOverdue) R.string.arc_overdue_limit else R.string.previous_service_km) }
    val endCaption = figures.upcomingLimitKm?.let { stringResource(R.string.arc_upcoming_limit) }
    val startValue = start?.let { formattedKm(it, locale) + " km" }
    val endValue = figures.upcomingLimitKm?.let { formattedKm(it, locale) + " km" }
    val spoken = listOfNotNull(startCaption?.let { "$it $startValue" }, endCaption?.let { "$it $endValue" }).joinToString(". ")
    val startColour = if (figures.arcStartsOverdue) Cockpit.red else Cockpit.steel
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val inset = 24.dp; val top = 22.dp; val bottom = 10.dp
        val chord = maxWidth - inset * 2
        Canvas(Modifier.fillMaxWidth().height(top + chord * ARC_RISE + bottom).semantics { contentDescription = spoken }.testTag("service_arc")) {
            val c = chord.toPx(); val rise = c * ARC_RISE
            val radius = (c * c / 4 + rise * rise) / (2 * rise)
            val half = Math.toDegrees(asin((c / 2 / radius).toDouble())).toFloat()
            val from = 270f - half; val sweep = half * 2
            val middle = Offset(size.width / 2, top.toPx() + radius)
            val box = Rect(middle.x - radius, middle.y - radius, middle.x + radius, middle.y + radius)
            fun point(fraction: Float, r: Float = radius): Offset {
                val angle = Math.toRadians((from + sweep * fraction).toDouble())
                return Offset(middle.x + r * cos(angle).toFloat(), middle.y + r * sin(angle).toFloat())
            }
            val at = figures.arcFraction
            if (at == null) {
                drawArc(Cockpit.track, from, sweep, false, box.topLeft, box.size, style = Stroke(4.dp.toPx(), cap = StrokeCap.Round))
            } else {
                val marker = at.coerceIn(0.03f, 0.97f)
                fun stop(fraction: Float) = (from + sweep * fraction) / 360f
                val brush = Brush.sweepGradient(
                    0f to startColour, stop(0f) to startColour, stop(marker) to Cockpit.orange, stop(1f) to Cockpit.cyan, 1f to Cockpit.cyan, center = middle,
                )
                listOf(30.dp to 0.05f, 20.dp to 0.09f, 11.dp to 0.20f, 5.dp to 1f).forEach { (width, alpha) ->
                    drawArc(brush, from, sweep * reveal, false, box.topLeft, box.size, alpha = alpha, style = Stroke(width.toPx(), cap = StrokeCap.Round))
                }
                val spot = point(at * reveal)
                drawCircle(Brush.radialGradient(listOf(Cockpit.orange.copy(alpha = 0.40f), Cockpit.orange.copy(alpha = 0.12f), Cockpit.orange.copy(alpha = 0f)), spot, 46.dp.toPx()), 46.dp.toPx(), spot)
                drawCircle(Cockpit.background, 6.dp.toPx(), spot)
                drawCircle(Color(0xFFFFE2B0), 6.dp.toPx(), spot, style = Stroke(1.5.dp.toPx()))
                drawCircle(Cockpit.orange, 2.5.dp.toPx(), spot)
            }
            // The two ends are named along the outside of the arc, as on the design.
            fun label(caption: String, value: String, colour: Color, atStart: Boolean) {
                val small = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colour.copy(alpha = 0.9f).toArgb(); textSize = min(11.sp.toPx(), 13.dp.toPx()) }
                val large = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colour.toArgb(); textSize = min(15.sp.toPx(), 17.dp.toPx()); typeface = Typeface.DEFAULT_BOLD }
                val inner = radius + 11.dp.toPx(); val outer = inner + large.textSize + 2.dp.toPx()
                listOf(Triple(value, large, inner), Triple(caption, small, outer)).forEach { (text, paint, r) ->
                    val room = (Math.toRadians(sweep * 0.36) * r).toFloat()
                    val width = paint.measureText(text); if (width > room) paint.textSize *= room / width
                    val path = android.graphics.Path().apply { addArc(middle.x - r, middle.y - r, middle.x + r, middle.y + r, from, sweep) }
                    val gap = (Math.toRadians(sweep * 0.11) * r).toFloat()
                    paint.textAlign = if (atStart) Paint.Align.LEFT else Paint.Align.RIGHT
                    drawIntoCanvas { it.nativeCanvas.drawTextOnPath(text, path, if (atStart) gap else -gap, 0f, paint) }
                }
            }
            if (startCaption != null && startValue != null) label(startCaption, startValue, if (figures.arcStartsOverdue) Cockpit.red else Cockpit.muted, atStart = true)
            if (endCaption != null && endValue != null) label(endCaption, endValue, Cockpit.cyan, atStart = false)
        }
    }
}
