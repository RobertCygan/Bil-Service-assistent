package app.servicepassport.android.ui

import androidx.compose.foundation.isSystemInDarkTheme
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

private val LightScheme = lightColorScheme(
    primary = Color(0xFF1F5C4A), onPrimary = Color.White, primaryContainer = Color(0xFFCDE9DC), onPrimaryContainer = Color(0xFF072019),
    secondary = Color(0xFF4D6359), onSecondary = Color.White, secondaryContainer = Color(0xFFDCE8E0), onSecondaryContainer = Color(0xFF16211C),
    tertiary = Color(0xFF8A5A1C), onTertiary = Color.White, tertiaryContainer = Color(0xFFFCEBCB), onTertiaryContainer = Color(0xFF2E1B00),
    background = Color(0xFFF4F7F3), onBackground = Color(0xFF171D1A), surface = Color(0xFFF4F7F3), onSurface = Color(0xFF171D1A),
    surfaceVariant = Color(0xFFE1E7E1), onSurfaceVariant = Color(0xFF454B47),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color.White, surfaceContainer = Color(0xFFECF1EB),
    surfaceContainerHigh = Color(0xFFE6ECE6), surfaceContainerHighest = Color(0xFFE0E7E0),
    outline = Color(0xFF727A74), outlineVariant = Color(0xFFCDD5CE), error = Color(0xFFB3261E),
)
private val DarkScheme = darkColorScheme(
    primary = Color(0xFF8FD3B8), onPrimary = Color(0xFF00382A), primaryContainer = Color(0xFF14493A), onPrimaryContainer = Color(0xFFCDE9DC),
    secondary = Color(0xFFB4CCBF), onSecondary = Color(0xFF20352C), secondaryContainer = Color(0xFF2C4238), onSecondaryContainer = Color(0xFFD0E8DB),
    tertiary = Color(0xFFF5BD6B), onTertiary = Color(0xFF452B00), tertiaryContainer = Color(0xFF4F3200), onTertiaryContainer = Color(0xFFFFDDB0),
    background = Color(0xFF0F1412), onBackground = Color(0xFFE0E4DF), surface = Color(0xFF0F1412), onSurface = Color(0xFFE0E4DF),
    surfaceVariant = Color(0xFF414943), onSurfaceVariant = Color(0xFFC1C8C2),
    surfaceContainerLowest = Color(0xFF0A0F0D), surfaceContainerLow = Color(0xFF191E1B), surfaceContainer = Color(0xFF1D2220),
    surfaceContainerHigh = Color(0xFF272D2A), surfaceContainerHighest = Color(0xFF323835),
    outline = Color(0xFF8B928C), outlineVariant = Color(0xFF414943), error = Color(0xFFFFB4AB),
)
private val LightColors = AppColors(
    overdue = StatusPalette(Color(0xFFB3261E), Color(0xFFFBE4E1), Color(0xFF5C100B)),
    dueSoon = StatusPalette(Color(0xFFA86400), Color(0xFFFCEBCB), Color(0xFF4A2C00)),
    needsData = StatusPalette(Color(0xFF3F5F8A), Color(0xFFDFE8F5), Color(0xFF14263F)),
    noUrgency = StatusPalette(Color(0xFF1F7A4D), Color(0xFFD8F0E0), Color(0xFF0B3520)),
    hero = Color(0xFF1F5C4A), onHero = Color.White, onHeroMuted = Color(0xFFBFE0D2),
)
private val DarkColors = AppColors(
    overdue = StatusPalette(Color(0xFFFFB4AB), Color(0xFF5C1A15), Color(0xFFFFDAD6)),
    dueSoon = StatusPalette(Color(0xFFF5BD6B), Color(0xFF4F3200), Color(0xFFFFDDB0)),
    needsData = StatusPalette(Color(0xFFA9C7F2), Color(0xFF22364F), Color(0xFFD5E3F8)),
    noUrgency = StatusPalette(Color(0xFF8FD6AA), Color(0xFF123D27), Color(0xFFC4EFD2)),
    hero = Color(0xFF14493A), onHero = Color(0xFFE3F4EC), onHeroMuted = Color(0xFF9CC8B6),
)

internal val LocalAppColors = staticCompositionLocalOf { LightColors }

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
    val dark = isSystemInDarkTheme()
    CompositionLocalProvider(LocalAppColors provides if (dark) DarkColors else LightColors) {
        MaterialTheme(colorScheme = if (dark) DarkScheme else LightScheme, typography = AppTypography, shapes = AppShapes, content = content)
    }
}
