package com.example.travelledger.ui

import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class ThemeMode(val label: String) { SYSTEM("跟隨系統"), LIGHT("淺色"), DARK("深色") }

object ThemePrefs {
    private const val FILE = "settings"
    private const val KEY = "theme_mode"

    fun load(context: Context): ThemeMode =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY, null)
            ?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM

    fun save(context: Context, mode: ThemeMode) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString(KEY, mode.name).apply()
    }
}

/** Colors outside the Material scheme. */
@Immutable
data class LedgerColors(
    val dark: Boolean,
    val success: Color,
    val warning: Color,
    val danger: Color,
    val field: Color,
    val hairline: Color,
    val textMuted: Color,
)

val LocalLedgerColors = staticCompositionLocalOf {
    LedgerColors(false, Color.Green, Color.Yellow, Color.Red, Color.LightGray, Color.LightGray, Color.Gray)
}

private val Indigo = Color(0xFF5B57F2)

private val LightScheme: ColorScheme = lightColorScheme(
    primary = Indigo,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFECEBFF),
    onPrimaryContainer = Color(0xFF2A2690),
    secondary = Color(0xFF6B6E80),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEEEFF5),
    onSecondaryContainer = Color(0xFF2A2C38),
    background = Color(0xFFF5F6FA),
    onBackground = Color(0xFF15161C),
    surface = Color(0xFFF5F6FA),
    onSurface = Color(0xFF15161C),
    surfaceVariant = Color(0xFFEEEFF5),
    onSurfaceVariant = Color(0xFF6B6E80),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color.White,
    surfaceContainerHighest = Color(0xFFEEEFF5),
    outline = Color(0xFFD9DBE5),
    outlineVariant = Color(0xFFE9EAF1),
    error = Color(0xFFE5484D),
    onError = Color.White,
    errorContainer = Color(0xFFFFE5E6),
    onErrorContainer = Color(0xFF8A1C20),
    inverseSurface = Color(0xFF22232B),
    inverseOnSurface = Color(0xFFF2F2F7),
)

private val DarkScheme: ColorScheme = darkColorScheme(
    primary = Color(0xFF8D8AFF),
    onPrimary = Color(0xFF16134A),
    primaryContainer = Color(0xFF2B2967),
    onPrimaryContainer = Color(0xFFE2E1FF),
    secondary = Color(0xFF9EA1B2),
    onSecondary = Color(0xFF15161C),
    secondaryContainer = Color(0xFF25262E),
    onSecondaryContainer = Color(0xFFE6E7EE),
    background = Color(0xFF0C0D11),
    onBackground = Color(0xFFEDEEF3),
    surface = Color(0xFF0C0D11),
    onSurface = Color(0xFFEDEEF3),
    surfaceVariant = Color(0xFF1F2027),
    onSurfaceVariant = Color(0xFF9A9DAD),
    surfaceContainerLowest = Color(0xFF16171C),
    surfaceContainerLow = Color(0xFF16171C),
    surfaceContainer = Color(0xFF16171C),
    surfaceContainerHigh = Color(0xFF1C1D23),
    surfaceContainerHighest = Color(0xFF24252D),
    outline = Color(0xFF34353F),
    outlineVariant = Color(0xFF24252D),
    error = Color(0xFFFF6B70),
    onError = Color(0xFF3A0B0D),
    errorContainer = Color(0xFF4A1518),
    onErrorContainer = Color(0xFFFFD9DA),
    inverseSurface = Color(0xFFEDEEF3),
    inverseOnSurface = Color(0xFF15161C),
)

private val LightExtra = LedgerColors(
    dark = false,
    success = Color(0xFF1DB37A),
    warning = Color(0xFFF2A10C),
    danger = Color(0xFFE5484D),
    field = Color(0xFFF3F4F8),
    hairline = Color(0xFFECEDF3),
    textMuted = Color(0xFF9A9DAD),
)

private val DarkExtra = LedgerColors(
    dark = true,
    success = Color(0xFF34D399),
    warning = Color(0xFFFBBF24),
    danger = Color(0xFFFF6B70),
    field = Color(0xFF1F2027),
    hairline = Color(0xFF23242B),
    textMuted = Color(0xFF6E7182),
)

/** Tabular figures keep amounts aligned in lists. */
val Tnum = "tnum"

private val AppTypography = Typography().let { t ->
    t.copy(
        displayMedium = t.displayMedium.copy(fontWeight = FontWeight.SemiBold, fontFeatureSettings = Tnum, letterSpacing = (-0.5).sp),
        displaySmall = t.displaySmall.copy(fontWeight = FontWeight.SemiBold, fontFeatureSettings = Tnum, letterSpacing = (-0.5).sp),
        headlineMedium = t.headlineMedium.copy(fontWeight = FontWeight.Bold, fontFeatureSettings = Tnum, letterSpacing = (-0.3).sp),
        headlineSmall = t.headlineSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.2).sp),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.Bold, fontSize = 20.sp),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold, fontFeatureSettings = Tnum),
        titleSmall = t.titleSmall.copy(fontWeight = FontWeight.SemiBold, fontFeatureSettings = Tnum),
        bodyLarge = t.bodyLarge.copy(fontFeatureSettings = Tnum),
        bodyMedium = t.bodyMedium.copy(fontFeatureSettings = Tnum),
        bodySmall = t.bodySmall.copy(fontFeatureSettings = Tnum),
        labelLarge = t.labelLarge.copy(fontWeight = FontWeight.SemiBold),
        labelMedium = t.labelMedium.copy(fontWeight = FontWeight.Medium),
    )
}

/** Small uppercase-ish caption used above fields and sections. */
val CaptionStyle = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.2.sp)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun isDarkTheme(mode: ThemeMode): Boolean = when (mode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

@Composable
fun AppTheme(mode: ThemeMode = ThemeMode.SYSTEM, content: @Composable () -> Unit) {
    val dark = isDarkTheme(mode)
    CompositionLocalProvider(LocalLedgerColors provides if (dark) DarkExtra else LightExtra) {
        MaterialTheme(
            colorScheme = if (dark) DarkScheme else LightScheme,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}

val ledger: LedgerColors
    @Composable get() = LocalLedgerColors.current
