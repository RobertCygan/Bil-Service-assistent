package app.servicepassport.android.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.random.Random

// The look of the first screen, as building blocks for every other screen: the same backdrop, panels lit in the
// colour of what they are about, and buttons drawn as lit outlines instead of filled shapes.

/** The dark backdrop: a soft gradient, a faint floor grid running to a horizon, and a few specks of light. It stays put while the page scrolls. */
internal fun Modifier.cockpitBackdrop(): Modifier = drawBehind { cockpitBackground() }

private fun DrawScope.cockpitBackground() {
    drawRect(Brush.verticalGradient(listOf(Cockpit.background, Cockpit.backgroundLow, Cockpit.background)))
    val horizon = size.height * 0.40f
    drawCircle(Brush.radialGradient(listOf(Cockpit.red.copy(alpha = 0.07f), Cockpit.red.copy(alpha = 0f)), Offset(0f, horizon), size.width * 0.7f), size.width * 0.7f, Offset(0f, horizon))
    drawCircle(Brush.radialGradient(listOf(Cockpit.cyan.copy(alpha = 0.06f), Cockpit.cyan.copy(alpha = 0f)), Offset(size.width, horizon), size.width * 0.7f), size.width * 0.7f, Offset(size.width, horizon))
    val line = Cockpit.cyan.copy(alpha = 0.05f); val vanish = Offset(size.width / 2, horizon)
    for (i in -12..12) drawLine(line, vanish, Offset(size.width / 2 + i * size.width * 0.22f, size.height), strokeWidth = 1f)
    var y = horizon + 6.dp.toPx(); var step = 6.dp.toPx()
    while (y < size.height) { drawLine(line, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f); step *= 1.22f; y += step }
    // The same specks every time: a fixed seed, so nothing jumps between frames or screenshots.
    val random = Random(7)
    repeat(46) {
        val colour = when (it % 5) { 0 -> Cockpit.orange; 1, 2 -> Cockpit.cyan; else -> Cockpit.text }
        drawCircle(colour.copy(alpha = 0.18f + random.nextFloat() * 0.35f), (0.5f + random.nextFloat()).dp.toPx(), Offset(random.nextFloat() * size.width, random.nextFloat() * size.height))
    }
}

/**
 * A panel of dark glass with a thin lit edge, like the count tiles of the first screen. The edge and the faint light
 * inside take [accent]: the colour of a service group, or a quiet steel for neutral content. [lit] makes it brighter
 * for something that needs attention.
 */
@Composable internal fun Panel(modifier: Modifier = Modifier, accent: Color = Cockpit.steel, lit: Boolean = false, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val shape = MaterialTheme.shapes.small
    val glass = Brush.linearGradient(listOf(accent.copy(alpha = if (lit) 0.24f else 0.13f), accent.copy(alpha = 0.04f)))
    Column(
        modifier.clip(shape).background(Cockpit.background.copy(alpha = 0.55f)).background(glass).border(1.dp, accent.copy(alpha = if (lit) 0.85f else 0.45f), shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        content = content,
    )
}

/** A small label in capitals, like the one above the mileage on the first screen. */
@Composable internal fun SectionLabel(text: String, modifier: Modifier = Modifier, colour: Color = Cockpit.muted) {
    Text(text.uppercase(), modifier, color = colour, fontSize = 12.sp, letterSpacing = 1.6.sp, fontWeight = FontWeight.Medium)
}

/** One figure in its own tile: a small label and the value in large type. */
@Composable internal fun Figure(label: String, value: String, modifier: Modifier = Modifier, accent: Color = Cockpit.steel, lit: Boolean = false) {
    Panel(modifier, accent, lit) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            SectionLabel(label)
            FittingText(value, TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold), if (accent == Cockpit.steel) Cockpit.text else accent, 13.sp)
        }
    }
}

/** A group of related lines in one panel, with the usual space inside. */
@Composable internal fun Group(modifier: Modifier = Modifier, accent: Color = Cockpit.steel, lit: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    Panel(modifier.fillMaxWidth(), accent, lit) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content) }
}

/** The main action of a screen: a lit cyan outline with a faint fill. */
@Composable internal fun PrimaryButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable RowScope.() -> Unit) {
    Button(
        onClick, modifier, enabled, shape = MaterialTheme.shapes.small, border = BorderStroke(1.dp, if (enabled) Cockpit.cyan.copy(alpha = 0.85f) else Cockpit.steel.copy(alpha = 0.4f)),
        colors = ButtonDefaults.buttonColors(containerColor = Cockpit.cyan.copy(alpha = 0.16f), contentColor = Cockpit.text, disabledContainerColor = Cockpit.steel.copy(alpha = 0.08f), disabledContentColor = Cockpit.muted.copy(alpha = 0.6f)),
        content = content,
    )
}

/** Any other action: a quiet steel outline with cyan lettering. */
@Composable internal fun QuietButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable RowScope.() -> Unit) {
    OutlinedButton(
        onClick, modifier, enabled, shape = MaterialTheme.shapes.small, border = BorderStroke(1.dp, Cockpit.steel.copy(alpha = if (enabled) 0.8f else 0.35f)),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Cockpit.cyan, disabledContentColor = Cockpit.muted.copy(alpha = 0.6f)),
        content = content,
    )
}
