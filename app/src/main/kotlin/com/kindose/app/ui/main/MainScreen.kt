package com.kindose.app.ui.main

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import com.kindose.app.data.DefaultDataRepository
import com.kindose.app.data.DoseWindowRecord
import com.kindose.app.data.RepositorySnapshot
import com.kindose.app.domain.*
import com.kindose.app.ui.theme.KinDoseTheme
import com.kindose.app.ui.theme.KinDoseThemeTokens

@Composable
fun MainScreen(
    onItemClick: (NavKey) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: MainScreenViewModel = viewModel { MainScreenViewModel(DefaultDataRepository()) }
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    when (val state = uiState) {
        MainScreenUiState.Loading -> {
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .semantics { liveRegion = LiveRegionMode.Polite; contentDescription = "Loading KinDose Medication Safety Engine" },
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }

        is MainScreenUiState.Error -> {
            Box(
                modifier = modifier.fillMaxSize().padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Unable to load medication state: ${state.throwable.message}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }

        is MainScreenUiState.Success -> {
            val baseDensity = LocalDensity.current
            val effectiveDensity = remember(baseDensity, state.controls.seniorLargeVisionBoost) {
                if (state.controls.seniorLargeVisionBoost) {
                    Density(
                        density = baseDensity.density,
                        fontScale = (baseDensity.fontScale * 1.22f).coerceAtMost(1.65f)
                    )
                } else {
                    baseDensity
                }
            }

            CompositionLocalProvider(LocalDensity provides effectiveDensity) {
                KinDoseInteractiveScaffold(
                    snapshot = state.snapshot,
                    controls = state.controls,
                    onSwitchPersona = viewModel::switchPersona,
                    onToggleVisionBoost = viewModel::toggleSeniorLargeVisionBoost,
                    onToggleLedgerSheet = viewModel::toggleArchitectureLedgerSheet,
                    onSelectWindow = viewModel::selectDoseWindow,
                    onSeniorConfirm = viewModel::onSeniorConfirmTap,
                    onSeniorSnooze = viewModel::onSeniorSnoozeTap,
                    onCaregiverResolve = viewModel::onCaregiverResolveTap,
                    onSimulateDuplicateTap = viewModel::onSimulateDuplicateTapAfter18m,
                    onSimulateEscalation = viewModel::onSimulateEscalationBreach,
                    onResetScenario = viewModel::onResetScenarioToDue,
                    onSwitchIdentityProvider = viewModel::onSwitchIdentityProvider,
                    onSimulateNewPhoneRestore = viewModel::onSimulateNewPhoneRestore,
                    modifier = modifier
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun KinDoseInteractiveScaffold(
    snapshot: RepositorySnapshot,
    controls: LocalUiControls,
    onSwitchPersona: (AppPersonaSurface) -> Unit,
    onToggleVisionBoost: () -> Unit,
    onToggleLedgerSheet: (Boolean) -> Unit,
    onSelectWindow: (DoseWindowId) -> Unit,
    onSeniorConfirm: () -> Unit,
    onSeniorSnooze: () -> Unit,
    onCaregiverResolve: (String) -> Unit,
    onSimulateDuplicateTap: () -> Unit,
    onSimulateEscalation: () -> Unit,
    onResetScenario: () -> Unit,
    onSwitchIdentityProvider: (IdentityProvider) -> Unit = {},
    onSimulateNewPhoneRestore: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val haptics = LocalHapticFeedback.current
    val scrollState = rememberScrollState()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            KinDoseTopHeader(
                snapshot = snapshot,
                controls = controls,
                onSwitchPersona = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onSwitchPersona(it)
                },
                onToggleVisionBoost = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onToggleVisionBoost()
                },
                onOpenLedger = { onToggleLedgerSheet(true) }
            )
        },
        bottomBar = {
            SimulationControlDock(
                activeRecord = snapshot.activeRecord,
                onSimulateDuplicateTap = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onSimulateDuplicateTap()
                },
                onSimulateEscalation = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onSimulateEscalation()
                },
                onResetScenario = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onResetScenario()
                },
                onOpenLedger = { onToggleLedgerSheet(true) }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Daily Schedule Pill Selector (8:00 AM, 1:00 PM, 8:00 PM)
            DailyScheduleTimelineStrip(
                windows = snapshot.windows,
                activeWindowId = snapshot.activeWindowId,
                onSelectWindow = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onSelectWindow(it)
                }
            )

            // Optional transient banner for simulation feedback
            controls.lastStatusBanner?.let { banner ->
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { liveRegion = LiveRegionMode.Polite }
                ) {
                    Text(
                        text = banner,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                    )
                }
            }

            // Main Persona Surface (Senior Appliance vs. Caregiver Command Center)
            AnimatedContent(
                targetState = controls.activePersona,
                transitionSpec = { fadeIn(spring(stiffness = Spring.StiffnessMediumLow)) togetherWith fadeOut() },
                label = "PersonaSurfaceTransition"
            ) { persona ->
                when (persona) {
                    AppPersonaSurface.SENIOR_APPLIANCE -> {
                        SeniorApplianceHeroSurface(
                            patientName = snapshot.patientName,
                            record = snapshot.activeRecord,
                            onConfirmTap = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                onSeniorConfirm()
                            },
                            onSnoozeTap = {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSeniorSnooze()
                            }
                        )
                    }

                    AppPersonaSurface.CAREGIVER_COMMAND -> {
                        CaregiverCommandCenterSurface(
                            snapshot = snapshot,
                            onResolveTap = { note ->
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                onCaregiverResolve(note)
                            },
                            onOpenAuditLedger = { onToggleLedgerSheet(true) },
                            onSwitchIdentityProvider = {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSwitchIdentityProvider(it)
                            },
                            onSimulateNewPhoneRestore = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                onSimulateNewPhoneRestore()
                            }
                        )
                    }
                }
            }

            // Accessibility & Engineering Rigor Footer Badge
            AccessibilityAndArchitectureBadgeCard(
                visionBoostEnabled = controls.seniorLargeVisionBoost,
                auditCount = snapshot.auditLedger.size,
                outboxCount = snapshot.outboxQueue.size,
                onInspectLedger = { onToggleLedgerSheet(true) }
            )
        }
    }

    if (controls.showArchitectureLedgerSheet) {
        ModalBottomSheet(
            onDismissRequest = { onToggleLedgerSheet(false) },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            TransactionalOutboxAndAuditSheetContent(
                snapshot = snapshot,
                onClose = { onToggleLedgerSheet(false) }
            )
        }
    }
}

@Composable
private fun KinDoseTopHeader(
    snapshot: RepositorySnapshot,
    controls: LocalUiControls,
    onSwitchPersona: (AppPersonaSurface) -> Unit,
    onToggleVisionBoost: () -> Unit,
    onOpenLedger: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        shadowElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Medical Cross / Shield Emblem
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "K+",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                    Column {
                        Text(
                            text = "KinDose Safety Engine",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.semantics { heading() }
                        )
                        Text(
                            text = "${snapshot.patientName} (${snapshot.patientAge}) · Doze-Exempt WAL Active",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // WCAG AAA High-Scale Vision Boost Toggle Button
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = controls.seniorLargeVisionBoost,
                        onClick = onToggleVisionBoost,
                        label = {
                            Text(
                                text = if (controls.seniorLargeVisionBoost) "A11y AAA+" else "A11y AAA",
                                style = MaterialTheme.typography.labelMedium
                            )
                        },
                        modifier = Modifier.semantics {
                            contentDescription = "Toggle Senior Low-Vision Large Typography Boost"
                            stateDescription = if (controls.seniorLargeVisionBoost) "Large Font Enabled" else "Standard Font"
                        }
                    )
                }
            }

            // Segmented Persona Switcher (Senior Appliance vs Caregiver Command Center)
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    AppPersonaSurface.entries.forEach { persona ->
                        val isSelected = controls.activePersona == persona
                        val bgColor by animateColorAsState(
                            targetValue = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                            label = "PersonaTabBg"
                        )
                        val contentColor by animateColorAsState(
                            targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            label = "PersonaTabFg"
                        )

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(bgColor)
                                .clickable(
                                    role = Role.Tab,
                                    onClickLabel = "Switch to ${persona.title}"
                                ) { onSwitchPersona(persona) }
                                .semantics {
                                    selected = isSelected
                                    contentDescription = "${persona.title}: ${persona.subtitle}"
                                }
                                .padding(vertical = 8.dp, horizontal = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = persona.title,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = contentColor
                                )
                                Text(
                                    text = persona.subtitle,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = contentColor.copy(alpha = 0.85f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DailyScheduleTimelineStrip(
    windows: List<DoseWindowRecord>,
    activeWindowId: DoseWindowId,
    onSelectWindow: (DoseWindowId) -> Unit
) {
    val safetyColors = KinDoseThemeTokens.safetyColors

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "TODAY'S MEDICATION WINDOWS",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.semantics { heading() }
        )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(windows, key = { it.spec.doseWindowId.value }) { item ->
                val isSelected = item.spec.doseWindowId == activeWindowId
                val (badgeText, badgeBg, badgeFg) = when (item.state) {
                    is DoseState.Confirmed -> Triple(
                        "TAKEN",
                        safetyColors.safeEmeraldContainer,
                        safetyColors.onSafeEmeraldContainer
                    )
                    is DoseState.Escalated -> Triple(
                        "ESCALATED",
                        safetyColors.escalationCrimsonContainer,
                        safetyColors.onEscalationCrimsonContainer
                    )
                    is DoseState.Snoozed -> Triple(
                        "SNOOZED",
                        safetyColors.snoozeIndigoContainer,
                        safetyColors.onSnoozeIndigoContainer
                    )
                    is DoseState.Due -> Triple(
                        "DUE NOW",
                        MaterialTheme.colorScheme.primaryContainer,
                        MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    is DoseState.ResolvedByCaregiver -> Triple(
                        "CAREGIVER OK",
                        safetyColors.safeEmeraldContainer,
                        safetyColors.onSafeEmeraldContainer
                    )
                    is DoseState.Scheduled -> Triple(
                        "UPCOMING",
                        MaterialTheme.colorScheme.surfaceVariant,
                        MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    is DoseState.MissedExpired -> Triple(
                        "EXPIRED",
                        safetyColors.escalationCrimsonContainer,
                        safetyColors.onEscalationCrimsonContainer
                    )
                }

                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = if (isSelected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    border = BorderStroke(
                        width = if (isSelected) 2.5.dp else 1.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                    ),
                    shadowElevation = if (isSelected) 4.dp else 0.dp,
                    modifier = Modifier
                        .widthIn(min = 152.dp)
                        .heightIn(min = 64.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .clickable(role = Role.Button) { onSelectWindow(item.spec.doseWindowId) }
                        .semantics(mergeDescendants = true) {
                            selected = isSelected
                            contentDescription = "${item.spec.scheduledTimeLabel}, ${item.spec.medicationName} ${item.spec.strengthLabel}, Status: $badgeText"
                        }
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = item.spec.scheduledTimeLabel,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Surface(
                                color = badgeBg,
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = badgeText,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = badgeFg,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = "${item.spec.medicationName} ${item.spec.strengthLabel}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

/**
 * SENIOR APPLIANCE SURFACE:
 * Designed for zero-login, tremor-safe, low-vision senior clarity (WCAG 2.2 AAA).
 * Minimum interactive button height: 92.dp (nearly double the 48.dp Android requirement).
 */
@Composable
internal fun SeniorApplianceHeroSurface(
    patientName: String,
    record: DoseWindowRecord,
    onConfirmTap: () -> Unit,
    onSnoozeTap: () -> Unit
) {
    val safetyColors = KinDoseThemeTokens.safetyColors
    val spec = record.spec
    val state = record.state

    when (state) {
        is DoseState.Confirmed -> {
            // DOUBLE-DOSE LOCKOUT SHIELD SURFACE
            val isDuplicateTapAlert = record.duplicateTapWarningMinutes != null
            val containerColor = if (isDuplicateTapAlert) {
                safetyColors.shieldAmberContainer
            } else {
                safetyColors.safeEmeraldContainer
            }
            val accentColor = if (isDuplicateTapAlert) {
                safetyColors.shieldAmber
            } else {
                safetyColors.safeEmerald
            }
            val onContainerColor = if (isDuplicateTapAlert) {
                safetyColors.onShieldAmberContainer
            } else {
                safetyColors.onSafeEmeraldContainer
            }

            Card(
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(containerColor = containerColor),
                border = BorderStroke(3.dp, accentColor),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics {
                        liveRegion = LiveRegionMode.Assertive
                        contentDescription = if (isDuplicateTapAlert) {
                            "Stop! Double-Dose Prevention Shield Active. You already took ${spec.medicationName} ${record.duplicateTapWarningMinutes} minutes ago. Do not take another pill."
                        } else {
                            "Confirmed safe. You took your ${spec.scheduledTimeLabel} ${spec.medicationName}. Double-Dose Lockout Shield is protecting you until the next window."
                        }
                    }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Giant Shield Emblem
                    Surface(
                        shape = CircleShape,
                        color = accentColor,
                        modifier = Modifier.size(76.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = if (isDuplicateTapAlert) "STOP" else "SAFE",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }

                    if (isDuplicateTapAlert) {
                        Surface(
                            color = safetyColors.shieldAmber,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "HOLD ON — DO NOT TAKE A SECOND PILL!\nYou already confirmed this dose ${record.duplicateTapWarningMinutes} minutes ago.",
                                style = MaterialTheme.typography.titleMedium,
                                color = safetyColors.onShieldAmber,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(14.dp)
                            )
                        }
                    }

                    Text(
                        text = "${spec.medicationName} ${spec.strengthLabel} Already Taken",
                        style = MaterialTheme.typography.headlineLarge,
                        color = onContainerColor,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.semantics { heading() }
                    )

                    Text(
                        text = "Recorded for ${spec.scheduledTimeLabel} window · Family notified automatically.\nYou are all set for this medication.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = onContainerColor,
                        textAlign = TextAlign.Center
                    )

                    // Visual Pill Verification Summary
                    PillIdentificationCard(spec = spec)

                    // Test Duplicate Tap Button (Demonstrates how tapping again triggers the Amber Shield)
                    OutlinedButton(
                        onClick = onConfirmTap,
                        shape = MaterialTheme.shapes.large,
                        border = BorderStroke(2.dp, accentColor),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 64.dp)
                    ) {
                        Text(
                            text = "Tap Again to Test Double-Dose Lockout Shield",
                            style = MaterialTheme.typography.titleMedium,
                            color = onContainerColor
                        )
                    }
                }
            }
        }

        is DoseState.ResolvedByCaregiver -> {
            Card(
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(containerColor = safetyColors.safeEmeraldContainer),
                border = BorderStroke(2.5.dp, safetyColors.safeEmerald),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Checked In with ${state.caregiverName}",
                        style = MaterialTheme.typography.headlineMedium,
                        color = safetyColors.onSafeEmeraldContainer,
                        modifier = Modifier.semantics { heading() }
                    )
                    Text(
                        text = "\"${state.resolutionNote}\"",
                        style = MaterialTheme.typography.bodyLarge,
                        color = safetyColors.onSafeEmeraldContainer
                    )
                    PillIdentificationCard(spec = spec)
                }
            }
        }

        else -> {
            // ACTIVE DOSE PROMPT (DUE, SNOOZED, ESCALATED, SCHEDULED)
            val isEscalated = state is DoseState.Escalated
            val isSnoozed = state is DoseState.Snoozed

            Card(
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(
                    containerColor = if (isEscalated) {
                        safetyColors.escalationCrimsonContainer
                    } else {
                        MaterialTheme.colorScheme.surface
                    }
                ),
                border = BorderStroke(
                    width = if (isEscalated) 3.dp else 2.dp,
                    color = if (isEscalated) safetyColors.escalationCrimson else MaterialTheme.colorScheme.primary
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(22.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Status Header Pill
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = when {
                                isEscalated -> safetyColors.escalationCrimson
                                isSnoozed -> safetyColors.snoozeIndigo
                                else -> MaterialTheme.colorScheme.primary
                            },
                            shape = RoundedCornerShape(50)
                        ) {
                            Text(
                                text = when {
                                    isEscalated -> "OVERDUE · FAMILY ALERTED AT 8:30 AM"
                                    isSnoozed -> "SNOOZED (${state.snoozeCount} OF ${spec.maxSnoozes} USED)"
                                    else -> "TIME FOR YOUR ${spec.scheduledTimeLabel} PILL"
                                },
                                style = MaterialTheme.typography.labelLarge,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                            )
                        }
                    }

                    // Giant Medication Title & Purpose
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "${spec.medicationName} (${spec.strengthLabel})",
                            style = MaterialTheme.typography.displaySmall,
                            color = if (isEscalated) safetyColors.onEscalationCrimsonContainer else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.semantics { heading() }
                        )
                        Text(
                            text = spec.clinicalPurpose,
                            style = MaterialTheme.typography.titleMedium,
                            color = if (isEscalated) safetyColors.onEscalationCrimsonContainer else MaterialTheme.colorScheme.primary
                        )
                    }

                    // Visual & Tactile Pill Identification Card (Non-Color-Alone A11y)
                    PillIdentificationCard(spec = spec)

                    // Plain-English Instructions Box
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = spec.dosageInstructions,
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = spec.foodWarning,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // GIANT TACTILE CONFIRMATION CTA (96.dp height for Senior Tremor & Low-Vision Accessibility)
                    Button(
                        onClick = onConfirmTap,
                        shape = RoundedCornerShape(24.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = safetyColors.safeEmerald,
                            contentColor = safetyColors.onSafeEmerald
                        ),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 96.dp)
                            .semantics {
                                role = Role.Button
                                contentDescription = "I took my ${spec.scheduledTimeLabel} ${spec.medicationName} ${spec.strengthLabel}. Tap once to confirm and activate double-dose safety shield."
                            }
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(vertical = 8.dp)
                        ) {
                            Text(
                                text = "I TOOK MY ${spec.scheduledTimeLabel} PILL",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.ExtraBold,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = if (isEscalated) {
                                    "Tap once — sends instant All-Clear to Maya"
                                } else {
                                    "Tap once to confirm & lock Double-Dose Shield"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = safetyColors.onSafeEmerald.copy(alpha = 0.92f)
                            )
                        }
                    }

                    // Bounded Snooze Secondary Action (Hidden once Escalated)
                    if (!isEscalated) {
                        val usedSnoozes = (state as? DoseState.Snoozed)?.snoozeCount ?: 0
                        val remainingSnoozes = (spec.maxSnoozes - usedSnoozes).coerceAtLeast(0)
                        OutlinedButton(
                            onClick = onSnoozeTap,
                            enabled = remainingSnoozes > 0,
                            shape = RoundedCornerShape(18.dp),
                            border = BorderStroke(2.dp, MaterialTheme.colorScheme.outline),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 64.dp)
                                .semantics {
                                    contentDescription = "Remind me in 10 minutes. $remainingSnoozes snoozes remaining before 8:30 AM caregiver escalation."
                                }
                        ) {
                            Text(
                                text = if (remainingSnoozes > 0) {
                                    "Remind Me in 10 Mins ($remainingSnoozes left before 8:30 AM alert)"
                                } else {
                                    "Maximum Snoozes Reached (Caregiver Alert at 8:30 AM)"
                                },
                                style = MaterialTheme.typography.titleMedium,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Custom Vector/Canvas Pill Visual Identifier Card.
 * Satisfies WCAG 1.4.1 (Use of Color) by combining shape geometry, imprint code text, and color description.
 */
@Composable
private fun PillIdentificationCard(spec: DoseScheduleSpec) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription = "Visual Pill Check: ${spec.pillColorLabel}, shape is ${spec.pillShape.a11yDescription}, stamped with imprint code ${spec.imprintCode}"
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Custom Canvas Pill Illustration
            Canvas(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                val cx = size.width / 2f
                val cy = size.height / 2f
                when (spec.pillShape) {
                    PillShape.ROUND_TABLET -> {
                        drawCircle(
                            color = Color(0xFFF48FB1),
                            radius = size.minDimension * 0.34f,
                            center = Offset(cx, cy)
                        )
                        drawCircle(
                            color = Color(0xFF880E4F),
                            radius = size.minDimension * 0.34f,
                            center = Offset(cx, cy),
                            style = Stroke(width = 4f)
                        )
                        // Score line across middle
                        drawLine(
                            color = Color(0xFF880E4F),
                            start = Offset(cx - size.minDimension * 0.25f, cy),
                            end = Offset(cx + size.minDimension * 0.25f, cy),
                            strokeWidth = 3f,
                            cap = StrokeCap.Round
                        )
                    }

                    PillShape.OVAL_CAPLET -> {
                        val w = size.width * 0.72f
                        val h = size.height * 0.42f
                        drawRoundRect(
                            color = Color(0xFFFFD54F),
                            topLeft = Offset(cx - w / 2f, cy - h / 2f),
                            size = Size(w, h),
                            cornerRadius = CornerRadius(h / 2f, h / 2f)
                        )
                        drawRoundRect(
                            color = Color(0xFFF57F17),
                            topLeft = Offset(cx - w / 2f, cy - h / 2f),
                            size = Size(w, h),
                            cornerRadius = CornerRadius(h / 2f, h / 2f),
                            style = Stroke(width = 4f)
                        )
                    }

                    PillShape.TWO_TONE_CAPSULE -> {
                        val w = size.width * 0.74f
                        val h = size.height * 0.38f
                        drawRoundRect(
                            color = Color(0xFF42A5F5),
                            topLeft = Offset(cx - w / 2f, cy - h / 2f),
                            size = Size(w, h),
                            cornerRadius = CornerRadius(h / 2f, h / 2f)
                        )
                        drawRoundRect(
                            color = Color(0xFF1565C0),
                            topLeft = Offset(cx - w / 2f, cy - h / 2f),
                            size = Size(w, h),
                            cornerRadius = CornerRadius(h / 2f, h / 2f),
                            style = Stroke(width = 4f)
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "PILL VISUAL CHECK · ${spec.pillShape.displayName.uppercase()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = spec.pillColorLabel,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Imprint stamped on pill: \"${spec.imprintCode}\"",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * FAMILY CAREGIVER COMMAND CENTER SURFACE:
 * Gives the adult child / caregiver immediate visibility into adherence, active escalations,
 * one-tap phone verification resolution, and the SQLite Transactional Outbox ledger.
 */
@Composable
internal fun CaregiverCommandCenterSurface(
    snapshot: RepositorySnapshot,
    onResolveTap: (String) -> Unit,
    onOpenAuditLedger: () -> Unit,
    onSwitchIdentityProvider: (IdentityProvider) -> Unit = {},
    onSimulateNewPhoneRestore: () -> Unit = {}
) {
    val safetyColors = KinDoseThemeTokens.safetyColors
    val activeRecord = snapshot.activeRecord
    val state = activeRecord.state

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // 1. Live Escalation Banner if any window is ESCALATED
        if (state is DoseState.Escalated) {
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = safetyColors.escalationCrimsonContainer),
                border = BorderStroke(2.5.dp, safetyColors.escalationCrimson),
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { liveRegion = LiveRegionMode.Assertive }
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "ACTION NEEDED · GRACE DEADLINE EXPIRED",
                        style = MaterialTheme.typography.labelLarge,
                        color = safetyColors.escalationCrimson
                    )
                    Text(
                        text = "${snapshot.patientName} has not confirmed her ${activeRecord.spec.scheduledTimeLabel} ${activeRecord.spec.medicationName} (${activeRecord.spec.strengthLabel}).",
                        style = MaterialTheme.typography.headlineSmall,
                        color = safetyColors.onEscalationCrimsonContainer,
                        modifier = Modifier.semantics { heading() }
                    )
                    Text(
                        text = "Prior snoozes used: ${state.snoozeCountPrior} · Senior phone heartbeat: Online (84% Battery)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = safetyColors.onEscalationCrimsonContainer
                    )

                    Button(
                        onClick = {
                            onResolveTap("Called Mom at 8:32 AM — confirmed she took ${activeRecord.spec.medicationName} with breakfast.")
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = safetyColors.escalationCrimson,
                            contentColor = safetyColors.onEscalationCrimson
                        ),
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 60.dp)
                    ) {
                        Text(
                            text = "I Called Mom & Verified — Mark Dose Safe",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // 2. Adherence & Telemetry Summary Card
        Card(
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "${snapshot.patientName}'s Safety Telemetry",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.semantics { heading() }
                        )
                        Text(
                            text = "Monitored by ${snapshot.caregiverName} · End-to-End Idempotent Sync",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Surface(
                        color = safetyColors.safeEmeraldContainer,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "${snapshot.thirtyDayAdherencePercent}% 30-Day",
                            style = MaterialTheme.typography.labelLarge,
                            color = safetyColors.onSafeEmeraldContainer,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    MetricTile(
                        label = "Current Streak",
                        value = "${snapshot.adherenceStreakDays} Days",
                        subtext = "Zero missed critical meds",
                        modifier = Modifier.weight(1f)
                    )
                    MetricTile(
                        label = "Senior Device",
                        value = "84% Battery",
                        subtext = "AlarmClock Doze-Exempt",
                        modifier = Modifier.weight(1f)
                    )
                }

                // QuickCaregiver Override for Due/Snoozed states too
                if (state is DoseState.Due || state is DoseState.Snoozed) {
                    OutlinedButton(
                        onClick = {
                            onResolveTap("Verified with Mom by phone — marking ${activeRecord.spec.medicationName} resolved.")
                        },
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                    ) {
                        Text("Log Caregiver Phone Check-In for ${activeRecord.spec.medicationName}")
                    }
                }
            }
        }

        // 2B. Multi-Provider CredentialManager & Zero-Loss Device Restore Card
        Card(
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Identity & Multi-Device Sync Vault",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.semantics { heading() }
                        )
                        Text(
                            text = "Vault ID: ${snapshot.cloudVault.familyVaultId} · ${snapshot.cloudVault.currentDeviceModel}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = "BlockStore Ready",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Text(
                    text = "Linked Login Providers (Tap to test CredentialManager identity switch):",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(IdentityProvider.entries) { provider ->
                        val isActive = snapshot.cloudVault.activeProvider == provider
                        FilterChip(
                            selected = isActive,
                            onClick = { onSwitchIdentityProvider(provider) },
                            label = {
                                Text(
                                    text = "${provider.displayName} (${provider.protocolBadge})",
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        )
                    }
                }

                FilledTonalButton(
                    onClick = onSimulateNewPhoneRestore,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                ) {
                    Text(
                        text = "Simulate Switching to Brand-New Phone (Zero-Loss Restore)",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }

        // 3. Recent Immutable Audit & Outbox Feed Preview
        Card(
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "LIVE TRANSACTIONAL OUTBOX & AUDIT LOG",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.semantics { heading() }
                    )
                    TextButton(onClick = onOpenAuditLedger) {
                        Text("Full Ledger (${snapshot.auditLedger.size})")
                    }
                }

                snapshot.auditLedger.take(3).forEach { entry ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "${entry.medicationName} · ${entry.fromState} -> ${entry.toState}",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = entry.formattedTime,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = entry.detail,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricTile(
    label: String,
    value: String,
    subtext: String,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.semantics(mergeDescendants = true) {}
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtext,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun AccessibilityAndArchitectureBadgeCard(
    visionBoostEnabled: Boolean,
    auditCount: Int,
    outboxCount: Int,
    onInspectLedger: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button) { onInspectLedger() }
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "Principal Architecture & WCAG 2.2 AAA Verified",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Google Sans Flex · 96dp Touch Targets · Audit ($auditCount) · Outbox ($outboxCount)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = "Inspect ->",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

/**
 * Interactive Simulation Dock at the bottom of the screen so the user can test every
 * Principal-grade safety invariant with one tap.
 */
@Composable
private fun SimulationControlDock(
    activeRecord: DoseWindowRecord,
    onSimulateDuplicateTap: () -> Unit,
    onSimulateEscalation: () -> Unit,
    onResetScenario: () -> Unit,
    onOpenLedger: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp,
        shadowElevation = 8.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "SCENARIO SIMULATOR (${activeRecord.spec.medicationName.uppercase()})",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "State: ${activeRecord.state.stateName}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalButton(
                    onClick = onSimulateDuplicateTap,
                    shape = MaterialTheme.shapes.small,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                ) {
                    Text(
                        text = "2x Tap Shield",
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1
                    )
                }

                FilledTonalButton(
                    onClick = onSimulateEscalation,
                    shape = MaterialTheme.shapes.small,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                ) {
                    Text(
                        text = "8:30 Escalate",
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1
                    )
                }

                OutlinedButton(
                    onClick = onResetScenario,
                    shape = MaterialTheme.shapes.small,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                    modifier = Modifier.weight(0.85f).heightIn(min = 48.dp)
                ) {
                    Text(
                        text = "Reset 8 AM",
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun TransactionalOutboxAndAuditSheetContent(
    snapshot: RepositorySnapshot,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "SQLite WAL Audit & Transactional Outbox",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() }
            )
            TextButton(onClick = onClose) {
                Text("Done")
            }
        }

        Text(
            text = "Every state transition commits atomically alongside its idempotency key and outbox payload (Zero Dual-Write Bug).",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Text(
            text = "TRANSACTIONAL OUTBOX QUEUE (${snapshot.outboxQueue.size})",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )

        if (snapshot.outboxQueue.isEmpty()) {
            Text(
                text = "No outbox events queued yet — confirm or escalate a dose to inspect outbox records.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            snapshot.outboxQueue.forEach { item ->
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "${item.outboxId} · ${item.eventType.displayLabel}",
                            style = MaterialTheme.typography.labelLarge
                        )
                        Text(
                            text = "IdempotencyKey: ${item.idempotencyKey.value} · ${item.deliveryStatus}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Text(
            text = "IMMUTABLE STATE AUDIT LOG (${snapshot.auditLedger.size})",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )

        snapshot.auditLedger.forEach { audit ->
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "[${audit.formattedTime}] ${audit.auditId}: ${audit.fromState} -> ${audit.toState}",
                        style = MaterialTheme.typography.labelLarge
                    )
                    Text(
                        text = audit.detail,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Preview(name = "1. Senior Appliance - 8:00 AM Dose Due", showBackground = true, widthDp = 412, heightDp = 915)
@Composable
fun SeniorApplianceDuePreview() {
    val repo = DefaultDataRepository()
    KinDoseTheme(darkTheme = false) {
        KinDoseInteractiveScaffold(
            snapshot = repo.snapshot.value,
            controls = LocalUiControls(activePersona = AppPersonaSurface.SENIOR_APPLIANCE),
            onSwitchPersona = {},
            onToggleVisionBoost = {},
            onToggleLedgerSheet = {},
            onSelectWindow = {},
            onSeniorConfirm = {},
            onSeniorSnooze = {},
            onCaregiverResolve = {},
            onSimulateDuplicateTap = {},
            onSimulateEscalation = {},
            onResetScenario = {}
        )
    }
}

@Preview(name = "2. Senior Appliance - Double-Dose Lockout Shield", showBackground = true, widthDp = 412, heightDp = 915)
@Composable
fun SeniorDoubleDoseShieldPreview() {
    val repo = DefaultDataRepository().apply {
        simulateDuplicateTapAfter18Minutes(DefaultDataRepository.MORNING_WINDOW_ID)
    }
    KinDoseTheme(darkTheme = false) {
        KinDoseInteractiveScaffold(
            snapshot = repo.snapshot.value,
            controls = LocalUiControls(
                activePersona = AppPersonaSurface.SENIOR_APPLIANCE,
                lastStatusBanner = "Simulated 2nd tap 18 mins later -> Double-Dose Lockout Shield activated!"
            ),
            onSwitchPersona = {},
            onToggleVisionBoost = {},
            onToggleLedgerSheet = {},
            onSelectWindow = {},
            onSeniorConfirm = {},
            onSeniorSnooze = {},
            onCaregiverResolve = {},
            onSimulateDuplicateTap = {},
            onSimulateEscalation = {},
            onResetScenario = {}
        )
    }
}

@Preview(name = "3. Caregiver Hub - 8:30 AM Escalation Alert", showBackground = true, widthDp = 412, heightDp = 915)
@Composable
fun CaregiverCommandEscalationPreview() {
    val repo = DefaultDataRepository().apply {
        simulateFastForwardToEscalation(DefaultDataRepository.MORNING_WINDOW_ID)
    }
    KinDoseTheme(darkTheme = false) {
        KinDoseInteractiveScaffold(
            snapshot = repo.snapshot.value,
            controls = LocalUiControls(activePersona = AppPersonaSurface.CAREGIVER_COMMAND),
            onSwitchPersona = {},
            onToggleVisionBoost = {},
            onToggleLedgerSheet = {},
            onSelectWindow = {},
            onSeniorConfirm = {},
            onSeniorSnooze = {},
            onCaregiverResolve = {},
            onSimulateDuplicateTap = {},
            onSimulateEscalation = {},
            onResetScenario = {}
        )
    }
}
