package com.euptdicio.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.euptdicio.data.ThemeMode

/** Colours outside the Material scheme: region badges and quiet ink tones. */
@Immutable
data class DicioColors(
    val portugal: Color,
    val portugalBg: Color,
    val brazil: Color,
    val brazilBg: Color,
    val africa: Color,
    val africaBg: Color,
    val faint: Color,
    val rule: Color,
    val quote: Color,
)

val LocalDicio = staticCompositionLocalOf {
    DicioColors(Color.Unspecified, Color.Unspecified, Color.Unspecified, Color.Unspecified, Color.Unspecified,
        Color.Unspecified, Color.Unspecified, Color.Unspecified, Color.Unspecified)
}

// Azulejo blue on warm paper; ink-dark text.
private val Light = lightColorScheme(
    primary = Color(0xFF1D4E9E),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE6F8),
    onPrimaryContainer = Color(0xFF0B2A5C),
    secondary = Color(0xFF5B6475),
    secondaryContainer = Color(0xFFDCE6F8),
    onSecondaryContainer = Color(0xFF0B2A5C),
    background = Color(0xFFFBF9F4),
    onBackground = Color(0xFF1B1C1F),
    surface = Color(0xFFFBF9F4),
    onSurface = Color(0xFF1B1C1F),
    surfaceVariant = Color(0xFFF1EEE6),
    onSurfaceVariant = Color(0xFF55565C),
    surfaceContainer = Color(0xFFF4F1EA),
    surfaceContainerHigh = Color(0xFFEDE9E0),
    surfaceContainerHighest = Color(0xFFE7E3D9),
    surfaceContainerLow = Color(0xFFF7F5EF),
    outline = Color(0xFFC9C5BB),
    outlineVariant = Color(0xFFE2DED4),
    error = Color(0xFFB3261E),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFA8C3F5),
    onPrimary = Color(0xFF0B2A5C),
    primaryContainer = Color(0xFF203A66),
    onPrimaryContainer = Color(0xFFDCE6F8),
    secondary = Color(0xFFB6BDCB),
    secondaryContainer = Color(0xFF203A66),
    onSecondaryContainer = Color(0xFFDCE6F8),
    background = Color(0xFF121417),
    onBackground = Color(0xFFE6E5E2),
    surface = Color(0xFF121417),
    onSurface = Color(0xFFE6E5E2),
    surfaceVariant = Color(0xFF1E2126),
    onSurfaceVariant = Color(0xFFA9ABB1),
    surfaceContainer = Color(0xFF1A1D21),
    surfaceContainerHigh = Color(0xFF22262B),
    surfaceContainerHighest = Color(0xFF2A2E34),
    surfaceContainerLow = Color(0xFF16181C),
    outline = Color(0xFF3F434A),
    outlineVariant = Color(0xFF2C3036),
    error = Color(0xFFF2B8B5),
)

private val LightExtra = DicioColors(
    portugal = Color(0xFF0F6B3C), portugalBg = Color(0xFFDDEFE3),
    brazil = Color(0xFF8A5A00), brazilBg = Color(0xFFF8EACB),
    africa = Color(0xFF8E3B2E), africaBg = Color(0xFFF6E0DA),
    faint = Color(0xFF7A7B80), rule = Color(0xFFE6E2D8), quote = Color(0xFF1D4E9E),
)

private val DarkExtra = DicioColors(
    portugal = Color(0xFF8FD4A8), portugalBg = Color(0xFF173726),
    brazil = Color(0xFFF2C978), brazilBg = Color(0xFF3A2D10),
    africa = Color(0xFFF0A898), africaBg = Color(0xFF3D211C),
    faint = Color(0xFF8D9096), rule = Color(0xFF2A2E34), quote = Color(0xFFA8C3F5),
)

val Serif = FontFamily.Serif

private val Type = Typography().let { t ->
    t.copy(
        displaySmall = TextStyle(fontFamily = Serif, fontWeight = FontWeight.SemiBold, fontSize = 38.sp, lineHeight = 44.sp),
        headlineSmall = TextStyle(fontFamily = Serif, fontWeight = FontWeight.SemiBold, fontSize = 26.sp, lineHeight = 32.sp),
        titleLarge = TextStyle(fontFamily = Serif, fontWeight = FontWeight.SemiBold, fontSize = 21.sp, lineHeight = 27.sp),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        bodyLarge = t.bodyLarge.copy(fontSize = 17.sp, lineHeight = 25.sp),
        labelSmall = t.labelSmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp),
    )
}

@Composable
fun DicioTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    androidx.compose.runtime.CompositionLocalProvider(LocalDicio provides if (dark) DarkExtra else LightExtra) {
        MaterialTheme(colorScheme = if (dark) Dark else Light, typography = Type, content = content)
    }
}
