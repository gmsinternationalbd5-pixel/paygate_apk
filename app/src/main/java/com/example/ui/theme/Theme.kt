package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = GateAccentDark,
    onPrimary = Color.Black,
    primaryContainer = GateAccentDark.copy(alpha = 0.2f),
    onPrimaryContainer = GateAccentDark,
    secondary = GateAccentDark,
    onSecondary = Color.Black,
    tertiary = GateWarningDark,
    background = GateBgDark,
    surface = GateSurfaceDark,
    surfaceVariant = GateSurface2Dark,
    outlineVariant = GateBorderDark,
    onBackground = GateInkDark,
    onSurface = GateInkDark,
    onSurfaceVariant = GateInk2Dark,
    error = GateDangerDark,
    onError = Color.Black
)

private val LightColorScheme = lightColorScheme(
    primary = GateAccentLight,
    onPrimary = Color.White,
    primaryContainer = GateAccentLight.copy(alpha = 0.12f),
    onPrimaryContainer = GateAccentLight,
    secondary = GateAccentLight,
    onSecondary = Color.White,
    tertiary = GateWarningLight,
    background = GateBgLight,
    surface = GateSurfaceLight,
    surfaceVariant = GateSurface2Light,
    outlineVariant = GateBorderLight,
    onBackground = GateInkLight,
    onSurface = GateInkLight,
    onSurfaceVariant = GateInk2Light,
    error = GateDangerLight,
    onError = Color.White
)

@Composable
fun PayGateTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // PRD v3.0: dynamic colour off, so the brand stays fixed
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
