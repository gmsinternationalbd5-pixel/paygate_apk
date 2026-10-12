package com.paygate.smsfwd.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Gate UI Design System Tokens (DESIGN.md / tokens.json specification)
 * Calm, Precise, Fast design system for PayGate Merchant SaaS and Android companion endpoints.
 */

// ---------------------------------------------------------------------------
// 1. Core Palette Tokens (Light & Dark)
// ---------------------------------------------------------------------------

// Background: Master application canvas
val GateBgLight = Color(0xFFFAFAF9)
val GateBgDark = Color(0xFF0B0B0E)

// Surface: Primary cards, sheets, dialogs, surfaces
val GateSurfaceLight = Color(0xFFFFFFFF)
val GateSurfaceDark = Color(0xFF141418)

// Surface-2: Inputs, table headers, hover highlights, recessed containers
val GateSurface2Light = Color(0xFFF4F4F2)
val GateSurface2Dark = Color(0xFF1C1C22)

// Border: 1px hairline subtle borders
val GateBorderLight = Color(0xFFE7E7E3)
val GateBorderDark = Color(0xFF2A2A31)

// Ink: Primary text, primary CTA buttons
val GateInkLight = Color(0xFF0E0E11)
val GateInkDark = Color(0xFFF5F5F7)

// Ink-2: Secondary text, descriptions, subtle headers
val GateInk2Light = Color(0xFF55555E)
val GateInk2Dark = Color(0xFFA4A4AE)

// Ink-3: Placeholder text, captions, timestamps, de-emphasized metadata
val GateInk3Light = Color(0xFF8A8A94)
val GateInk3Dark = Color(0xFF6F6F7A)

// Accent: Interactive links, active tab indicator, primary focus ring
val GateAccentLight = Color(0xFF5B5BF0)
val GateAccentDark = Color(0xFF8B8BFF)

// Accent Soft: Reference chip background, active tab pill tint
val GateAccentSoftLight = Color(0xFFEEEEFF)
val GateAccentSoftDark = Color(0xFF23234A)

// Status / Feedback
val GateSuccessLight = Color(0xFF12B76A)
val GateSuccessDark = Color(0xFF32D583)

val GateWarningLight = Color(0xFFF79009)
val GateWarningDark = Color(0xFFFDB022)

val GateDangerLight = Color(0xFFF04438)
val GateDangerDark = Color(0xFFF97066)

// ---------------------------------------------------------------------------
// 2. Fixed MFS Provider Brand Accents (Restricted to indicator dots and chips)
// ---------------------------------------------------------------------------
// bKash: #E2136E
val ProviderBkash = Color(0xFFE2136E)

// Nagad: #F6921E (standard Gate UI hex / #F7941D compatible)
val ProviderNagad = Color(0xFFF6921E)

// Rocket: #8C3494
val ProviderRocket = Color(0xFF8C3494)

// ---------------------------------------------------------------------------
// 3. Gate UI Semantic Custom Color Tokens (Theme Extension)
// ---------------------------------------------------------------------------
@Immutable
data class GateColors(
    val bg: Color,
    val surface: Color,
    val surface2: Color,
    val border: Color,
    val ink: Color,
    val ink2: Color,
    val ink3: Color,
    val accent: Color,
    val accentSoft: Color,
    val success: Color,
    val warning: Color,
    val danger: Color,
    val bkash: Color = ProviderBkash,
    val nagad: Color = ProviderNagad,
    val rocket: Color = ProviderRocket
)

val LocalGateColors = staticCompositionLocalOf {
    GateColors(
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
        danger = GateDangerLight
    )
}

// ---------------------------------------------------------------------------
// 4. Backward Compatibility Aliases
// ---------------------------------------------------------------------------
val StatusSuccess = GateSuccessLight
val StatusWarning = GateWarningLight
val StatusError = GateDangerLight
val StatusPending = GateAccentLight
val PayGateBlue = GateAccentLight
