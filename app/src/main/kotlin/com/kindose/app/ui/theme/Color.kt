package com.kindose.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Google Material 3 Expressive Warm Sage & Botanical Wellness Palette (Google Health / Pixel Aesthetic)
val primaryLight = Color(0xFF0E6B50)
val onPrimaryLight = Color(0xFFFFFFFF)
val primaryContainerLight = Color(0xFFD2F5E6)
val onPrimaryContainerLight = Color(0xFF052E22)

val secondaryLight = Color(0xFF3E5F55)
val onSecondaryLight = Color(0xFFFFFFFF)
val secondaryContainerLight = Color(0xFFE3EFEA)
val onSecondaryContainerLight = Color(0xFF132A23)

val tertiaryLight = Color(0xFF285EA7)
val onTertiaryLight = Color(0xFFFFFFFF)
val tertiaryContainerLight = Color(0xFFDCE7FF)
val onTertiaryContainerLight = Color(0xFF0A254E)

val backgroundLight = Color(0xFFF3F6F4) // Warm Organic Sage-Tinted Alabaster Canvas
val onBackgroundLight = Color(0xFF111815)
val surfaceLight = Color(0xFFFFFFFF)
val onSurfaceLight = Color(0xFF111815)
val surfaceVariantLight = Color(0xFFE7EDE9)
val onSurfaceVariantLight = Color(0xFF45534D)

// Dark Palette (OLED Night Sanctuary)
val primaryDark = Color(0xFF5EEAD4)
val onPrimaryDark = Color(0xFF033025)
val primaryContainerDark = Color(0xFF0A4F3C)
val onPrimaryContainerDark = Color(0xFFD2F5E6)

val secondaryDark = Color(0xFFA7C4BC)
val onSecondaryDark = Color(0xFF152C25)
val secondaryContainerDark = Color(0xFF253B34)
val onSecondaryContainerDark = Color(0xFFE3EFEA)

val tertiaryDark = Color(0xFF93C5FD)
val onTertiaryDark = Color(0xFF0D2B54)
val tertiaryContainerDark = Color(0xFF1E3A5F)
val onTertiaryContainerDark = Color(0xFFDCE7FF)

val backgroundDark = Color(0xFF0C1210)
val onBackgroundDark = Color(0xFFE6F0EC)
val surfaceDark = Color(0xFF141D1A)
val onSurfaceDark = Color(0xFFE6F0EC)
val surfaceVariantDark = Color(0xFF1F2B27)
val onSurfaceVariantDark = Color(0xFFAAB8B2)

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
    safeEmerald = Color(0xFF0D7452),
    onSafeEmerald = Color(0xFFFFFFFF),
    safeEmeraldContainer = Color(0xFFD8F8EA),
    onSafeEmeraldContainer = Color(0xFF063826),
    shieldAmber = Color(0xFFB85400),
    onShieldAmber = Color(0xFFFFFFFF),
    shieldAmberContainer = Color(0xFFFFECCC),
    onShieldAmberContainer = Color(0xFF4A2000),
    escalationCrimson = Color(0xFFC02626),
    onEscalationCrimson = Color(0xFFFFFFFF),
    escalationCrimsonContainer = Color(0xFFFFE4E4),
    onEscalationCrimsonContainer = Color(0xFF4A0A0A),
    snoozeIndigo = Color(0xFF3B4CCA),
    snoozeIndigoContainer = Color(0xFFE4E8FF),
    onSnoozeIndigoContainer = Color(0xFF161E54)
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
