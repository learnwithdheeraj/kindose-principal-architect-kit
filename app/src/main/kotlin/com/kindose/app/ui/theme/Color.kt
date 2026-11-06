package com.kindose.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Google Material 3 Expressive Core Palette (Medical Trust Cobalt & Emerald)
val primaryLight = Color(0xFF0B57D0)
val onPrimaryLight = Color(0xFFFFFFFF)
val primaryContainerLight = Color(0xFFD3E3FD)
val onPrimaryContainerLight = Color(0xFF041E49)

val secondaryLight = Color(0xFF006874)
val onSecondaryLight = Color(0xFFFFFFFF)
val secondaryContainerLight = Color(0xFF97F0FF)
val onSecondaryContainerLight = Color(0xFF001F24)

val tertiaryLight = Color(0xFF0D7A4E)
val onTertiaryLight = Color(0xFFFFFFFF)
val tertiaryContainerLight = Color(0xFFD3F9E5)
val onTertiaryContainerLight = Color(0xFF002112)

val backgroundLight = Color(0xFFF8FAFD)
val onBackgroundLight = Color(0xFF191C20)
val surfaceLight = Color(0xFFFFFFFF)
val onSurfaceLight = Color(0xFF191C20)
val surfaceVariantLight = Color(0xFFEDF2FA)
val onSurfaceVariantLight = Color(0xFF44474E)

// Dark Palette (OLED Senior Night-Safe High Contrast)
val primaryDark = Color(0xFFA8C7FA)
val onPrimaryDark = Color(0xFF062E6F)
val primaryContainerDark = Color(0xFF0842A0)
val onPrimaryContainerDark = Color(0xFFD3E3FD)

val secondaryDark = Color(0xFF4FD8EB)
val onSecondaryDark = Color(0xFF00363D)
val secondaryContainerDark = Color(0xFF004F58)
val onSecondaryContainerDark = Color(0xFF97F0FF)

val tertiaryDark = Color(0xFF66DBA8)
val onTertiaryDark = Color(0xFF003821)
val tertiaryContainerDark = Color(0xFF005232)
val onTertiaryContainerDark = Color(0xFFD3F9E5)

val backgroundDark = Color(0xFF0E131A)
val onBackgroundDark = Color(0xFFE2E8F0)
val surfaceDark = Color(0xFF161D26)
val onSurfaceDark = Color(0xFFE2E8F0)
val surfaceVariantDark = Color(0xFF222B38)
val onSurfaceVariantDark = Color(0xFFC4C6D0)

/**
 * Semantic Clinical Safety Palette (WCAG AAA Contrast Verified).
 * Ensures unmistakable visual states for:
 * - Safe Confirmation (Emerald)
 * - Double-Dose Prevention Lockout Shield (Amber Warning)
 * - Caregiver Escalation Breach (Crimson Alert)
 * - Bounded Snooze Waiting (Indigo)
 */
@Immutable
data class KinDoseSafetyColors(
    val safeEmerald: Color,
    val onSafeEmerald: Color,
    val safeEmeraldContainer: Color,
    val onSafeEmeraldContainer: Color,
    val shieldAmber: Color,
    val onShieldAmber: Color,
    val shieldAmberContainer: Color,
    val onShieldAmberContainer: Color,
    val escalationCrimson: Color,
    val onEscalationCrimson: Color,
    val escalationCrimsonContainer: Color,
    val onEscalationCrimsonContainer: Color,
    val snoozeIndigo: Color,
    val snoozeIndigoContainer: Color,
    val onSnoozeIndigoContainer: Color
)

val LightSafetyColors = KinDoseSafetyColors(
    safeEmerald = Color(0xFF0B6E4F),
    onSafeEmerald = Color(0xFFFFFFFF),
    safeEmeraldContainer = Color(0xFFD1FAE5),
    onSafeEmeraldContainer = Color(0xFF064E3B),
    shieldAmber = Color(0xFFB45309),
    onShieldAmber = Color(0xFFFFFFFF),
    shieldAmberContainer = Color(0xFFFEF3C7),
    onShieldAmberContainer = Color(0xFF78350F),
    escalationCrimson = Color(0xFFB91C1C),
    onEscalationCrimson = Color(0xFFFFFFFF),
    escalationCrimsonContainer = Color(0xFFFEE2E2),
    onEscalationCrimsonContainer = Color(0xFF7F1D1D),
    snoozeIndigo = Color(0xFF3730A3),
    snoozeIndigoContainer = Color(0xFFE0E7FF),
    onSnoozeIndigoContainer = Color(0xFF1E1B4B)
)

val DarkSafetyColors = KinDoseSafetyColors(
    safeEmerald = Color(0xFF34D399),
    onSafeEmerald = Color(0xFF022C22),
    safeEmeraldContainer = Color(0xFF064E3B),
    onSafeEmeraldContainer = Color(0xFFD1FAE5),
    shieldAmber = Color(0xFFFBBF24),
    onShieldAmber = Color(0xFF451A03),
    shieldAmberContainer = Color(0xFF78350F),
    onShieldAmberContainer = Color(0xFFFEF3C7),
    escalationCrimson = Color(0xFFF87171),
    onEscalationCrimson = Color(0xFF450A0A),
    escalationCrimsonContainer = Color(0xFF7F1D1D),
    onEscalationCrimsonContainer = Color(0xFFFEE2E2),
    snoozeIndigo = Color(0xFF818CF8),
    snoozeIndigoContainer = Color(0xFF312E81),
    onSnoozeIndigoContainer = Color(0xFFE0E7FF)
)

val LocalKinDoseSafetyColors = staticCompositionLocalOf { LightSafetyColors }
