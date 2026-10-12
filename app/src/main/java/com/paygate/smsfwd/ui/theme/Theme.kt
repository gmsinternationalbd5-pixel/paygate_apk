package com.paygate.smsfwd.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

/**
 * Gate UI Material 3 Color Schemes derived from tokens.json / DESIGN.md
 * Fixed dynamic colors so brand integrity is preserved across devices and OEMs.
 */
private val DarkGateColorScheme: ColorScheme = darkColorScheme(
    primary = GateAccentDark,
    onPrimary = Color.Black,
    primaryContainer = GateAccentSoftDark,
    onPrimaryContainer = GateAccentDark,
    secondary = GateAccentDark,
    onSecondary = Color.Black,
    secondaryContainer = GateSurface2Dark,
    onSecondaryContainer = GateInkDark,
    tertiary = GateWarningDark,
    onTertiary = Color.Black,
    tertiaryContainer = GateWarningDark.copy(alpha = 0.2f),
    onTertiaryContainer = GateWarningDark,
    background = GateBgDark,
    onBackground = GateInkDark,
    surface = GateSurfaceDark,
    onSurface = GateInkDark,
    surfaceVariant = GateSurface2Dark,
    onSurfaceVariant = GateInk2Dark,
    surfaceTint = GateAccentDark,
    outline = GateBorderDark,
    outlineVariant = GateBorderDark,
    error = GateDangerDark,
    onError = Color.Black,
    errorContainer = GateDangerDark.copy(alpha = 0.2f),
    onErrorContainer = GateDangerDark
)

private val LightGateColorScheme: ColorScheme = lightColorScheme(
    primary = GateAccentLight,
    onPrimary = Color.White,
    primaryContainer = GateAccentSoftLight,
    onPrimaryContainer = GateAccentLight,
    secondary = GateAccentLight,
    onSecondary = Color.White,
    secondaryContainer = GateSurface2Light,
    onSecondaryContainer = GateInkLight,
    tertiary = GateWarningLight,
    onTertiary = Color.White,
    tertiaryContainer = GateWarningLight.copy(alpha = 0.15f),
    onTertiaryContainer = GateWarningLight,
    background = GateBgLight,
    onBackground = GateInkLight,
    surface = GateSurfaceLight,
    onSurface = GateInkLight,
    surfaceVariant = GateSurface2Light,
    onSurfaceVariant = GateInk2Light,
    surfaceTint = GateAccentLight,
    outline = GateBorderLight,
    outlineVariant = GateBorderLight,
    error = GateDangerLight,
    onError = Color.White,
    errorContainer = GateDangerLight.copy(alpha = 0.12f),
    onErrorContainer = GateDangerLight
)

private val LightGateColors = GateColors(
    bg = GateBgLight,
    surface = GateSurfaceLight,
    surface2 = GateSurface2Light,
    border = GateBorderLight,
    ink = GateInkLight,
    ink2 = GateInk2Light,
    ink3 = GateInk3Light,
    accent = GateAccentLight,
    accentSoft = GateAccentSoftLight,
    success = GateSuccessLight,
    warning = GateWarningLight,
    danger = GateDangerLight,
    bkash = ProviderBkash,
    nagad = ProviderNagad,
    rocket = ProviderRocket
)

private val DarkGateColors = GateColors(
    bg = GateBgDark,
    surface = GateSurfaceDark,
    surface2 = GateSurface2Dark,
    border = GateBorderDark,
    ink = GateInkDark,
    ink2 = GateInk2Dark,
    ink3 = GateInk3Dark,
    accent = GateAccentDark,
    accentSoft = GateAccentSoftDark,
    success = GateSuccessDark,
    warning = GateWarningDark,
    danger = GateDangerDark,
    bkash = ProviderBkash,
    nagad = ProviderNagad,
    rocket = ProviderRocket
)

/**
 * Accessor for Gate UI custom semantic tokens in Composables:
 * e.g. GateTheme.colors.bkash, GateTheme.colors.surface2, GateTheme.colors.border
 */
object GateTheme {
    val colors: GateColors
        @Composable
        @ReadOnlyComposable
        get() = LocalGateColors.current
}

/**
 * PayGateTheme wraps MaterialTheme with Gate UI color scheme and typography.
 * Dynamic color is deliberately false by default to ensure uniform Gate UI branding.
 */
@Composable
fun PayGateTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkGateColorScheme else LightGateColorScheme
    val gateColors = if (darkTheme) DarkGateColors else LightGateColors

    CompositionLocalProvider(
        LocalGateColors provides gateColors
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
