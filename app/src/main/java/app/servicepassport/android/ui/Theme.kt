package app.servicepassport.android.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.servicepassport.engine.Category

/** Colours of one urgency group: a strong accent, and a soft container with readable text on it. */
data class StatusPalette(val accent: Color, val container: Color, val onContainer: Color)

data class AppColors(
    val overdue: StatusPalette, val dueSoon: StatusPalette, val needsData: StatusPalette, val noUrgency: StatusPalette,
    /** The card that presents the car on the first screen. */
    val hero: Color, val onHero: Color, val onHeroMuted: Color,
) {
    fun status(category: Category): StatusPalette = when (category) {
        Category.OVERDUE -> overdue; Category.DUE_SOON -> dueSoon; Category.NEEDS_DATA -> needsData; Category.NO_URGENCY -> noUrgency
    }
}

// One dark scheme, built from the colours of the owner's design. The design has no light version.
private val CockpitScheme = darkColorScheme(
    primary = Color(0xFF3FD9F7), onPrimary = Color(0xFF00222B), primaryContainer = Color(0xFF0D3A47), onPrimaryContainer = Color(0xFFC8F4FD),
    secondary = Color(0xFF9FB4C9), onSecondary = Color(0xFF0E1C2A), secondaryContainer = Color(0xFF1A2B3D), onSecondaryContainer = Color(0xFFD5E3F1),
    tertiary = Color(0xFFFFA42B), onTertiary = Color(0xFF3A2200), tertiaryContainer = Color(0xFF4A2F06), onTertiaryContainer = Color(0xFFFFDFB3),
    background = Color(0xFF060A12), onBackground = Color(0xFFF3F6FA), surface = Color(0xFF060A12), onSurface = Color(0xFFF3F6FA),
    surfaceVariant = Color(0xFF1B2939), onSurfaceVariant = Color(0xFFA9B5C3),
    surfaceContainerLowest = Color(0xFF04070D), surfaceContainerLow = Color(0xFF0C1420), surfaceContainer = Color(0xFF101A28),
    surfaceContainerHigh = Color(0xFF162233), surfaceContainerHighest = Color(0xFF1D2B3F),
    outline = Color(0xFF6C7C8F), outlineVariant = Color(0xFF243347), error = Color(0xFFFF8A7A), onError = Color(0xFF3B0A05),
    errorContainer = Color(0xFF4A1510), onErrorContainer = Color(0xFFFFD9D4),
)
private val CockpitColors = AppColors(
    overdue = StatusPalette(Color(0xFFFF6B5E), Color(0xFF3A1512), Color(0xFFFFD9D4)),
    dueSoon = StatusPalette(Color(0xFFF5A742), Color(0xFF3A2A0E), Color(0xFFFFE3B8)),
    needsData = StatusPalette(Color(0xFF5AA2F0), Color(0xFF10243F), Color(0xFFD3E5FB)),
    noUrgency = StatusPalette(Color(0xFF45D08A), Color(0xFF0F3323), Color(0xFFC6F2DA)),
    hero = Color(0xFF0D3A47), onHero = Color(0xFFF3F6FA), onHeroMuted = Color(0xFFA9B5C3),
)

internal val LocalAppColors = staticCompositionLocalOf { CockpitColors }

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp), small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp), extraLarge = RoundedCornerShape(32.dp),
)

private val AppTypography = Typography().let { base ->
    base.copy(
        displaySmall = base.displaySmall.copy(fontWeight = FontWeight.SemiBold),
        headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
        headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}

@Composable internal fun AppTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalAppColors provides CockpitColors) {
        MaterialTheme(colorScheme = CockpitScheme, typography = AppTypography, shapes = AppShapes, content = content)
    }
}
