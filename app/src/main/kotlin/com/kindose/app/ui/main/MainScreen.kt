package com.kindose.app.ui.main

import android.accounts.AccountManager
import android.app.Activity
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import com.kindose.app.data.DefaultDataRepository
import com.kindose.app.data.DoseWindowRecord
import com.kindose.app.data.RepositorySnapshot
import com.kindose.app.domain.*
import com.kindose.app.ui.theme.GoogleSansDisplayFamily
import com.kindose.app.ui.theme.GoogleSansMonoFamily
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
                    .semantics { liveRegion = LiveRegionMode.Polite; contentDescription = "Loading KinDose" },
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
                        fontScale = (baseDensity.fontScale * 1.18f).coerceAtMost(1.55f)
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
                    onToggleAccountSheet = viewModel::toggleAccountSignInSheet,
                    onToggleAddMedicationSheet = viewModel::toggleAddMedicationSheet,
                    onAddNewMedication = viewModel::onAddNewMedication,
                    onSignInWithProvider = viewModel::onSignInWithProvider,
                    onSignOut = viewModel::onSignOut,
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
    onToggleAccountSheet: (Boolean) -> Unit = {},
    onToggleAddMedicationSheet: (Boolean) -> Unit = {},
    onAddNewMedication: (String, String, String, String, String, PillShape, String, String) -> Unit = { _, _, _, _, _, _, _, _ -> },
    onSignInWithProvider: (IdentityProvider, String, String) -> Unit = { _, _, _ -> },
    onSignOut: () -> Unit = {},
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
            ExpressiveTopHeader(
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
                onOpenAccountSheet = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onToggleAccountSheet(true)
                }
            )
        },
        bottomBar = {
            FloatingScenarioPillBar(
                activeRecord = snapshot.activeRecord,
                onOpenAddMedication = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onToggleAddMedicationSheet(true)
                },
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
                onOpenAccountSheet = { onToggleAccountSheet(true) }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Expressive Pill Schedule Carousel (8:00 AM, 1:00 PM, 8:00 PM) + "+ Add Medication"
            ExpressiveScheduleStrip(
                windows = snapshot.windows,
                activeWindowId = snapshot.activeWindowId,
                onSelectWindow = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onSelectWindow(it)
                },
                onOpenAddMedication = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onToggleAddMedicationSheet(true)
                }
            )

            // Main Persona Surface (Senior Sanctuary vs. Family Caregiver Hub)
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

            Spacer(modifier = Modifier.height(12.dp))
        }
    }

    if (controls.showArchitectureLedgerSheet) {
        val ledgerSheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { onToggleLedgerSheet(false) },
            sheetState = ledgerSheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            TransactionalOutboxAndAuditSheetContent(
                snapshot = snapshot,
                onClose = { onToggleLedgerSheet(false) }
            )
        }
    }

    if (controls.showAddMedicationSheet) {
        val addMedSheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { onToggleAddMedicationSheet(false) },
            sheetState = addMedSheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            AddMedicationModalSheetContent(
                onSaveMedication = onAddNewMedication,
                onClose = { onToggleAddMedicationSheet(false) }
            )
        }
    }

    if (controls.showAccountSignInSheet || !snapshot.cloudVault.isSignedIn) {
        val accountSheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { onToggleAccountSheet(false) },
            sheetState = accountSheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            AccountSignInModalSheetContent(
                snapshot = snapshot,
                onSignInWithProvider = onSignInWithProvider,
                onSignOut = onSignOut,
                onSimulateNewPhoneRestore = {
                    onSimulateNewPhoneRestore()
                    onToggleAccountSheet(false)
                },
                onOpenAuditLedger = {
                    onToggleAccountSheet(false)
                    onToggleLedgerSheet(true)
                },
                onClose = { onToggleAccountSheet(false) }
            )
        }
    }
}

/**
 * Material 3 Expressive Editorial Header (Pixel Weather / Fitbit Inspired).
 * Uses bundled Google Sans Display with warm tonal pill controls and a prominent Account/Login chip.
 */
@Composable
private fun ExpressiveTopHeader(
    snapshot: RepositorySnapshot,
    controls: LocalUiControls,
    onSwitchPersona: (AppPersonaSurface) -> Unit,
    onToggleVisionBoost: () -> Unit,
    onOpenAccountSheet: () -> Unit = {}
) {
    val safetyColors = KinDoseThemeTokens.safetyColors
    val vault = snapshot.cloudVault

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f).padding(end = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = if (controls.activePersona == AppPersonaSurface.SENIOR_APPLIANCE) {
                        "Good morning, ${snapshot.patientName.substringBefore(" ")}"
                    } else {
                        "${snapshot.patientName.substringBefore(" ")}'s Care Circle"
                    },
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = GoogleSansDisplayFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        lineHeight = 23.sp
                    ),
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 2,
                    modifier = Modifier.semantics { heading() }
                )
                Text(
                    text = if (vault.isSignedIn) {
                        "Synced · ${vault.accountDisplayName}"
                    } else {
                        "Offline Mode · Tap Sign In"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Prominent Account / Login Pill Button
                Surface(
                    onClick = onOpenAccountSheet,
                    shape = CircleShape,
                    color = if (vault.isSignedIn) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        safetyColors.shieldAmberContainer
                    },
                    modifier = Modifier
                        .height(40.dp)
                        .semantics {
                            role = Role.Button
                            contentDescription = "Open Sign In and Cloud Account Switcher"
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = if (vault.isSignedIn) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                safetyColors.shieldAmber
                            },
                            modifier = Modifier.size(22.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = if (vault.isSignedIn) {
                                        vault.accountDisplayName.take(1).uppercase()
                                    } else {
                                        "+"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Text(
                            text = if (vault.isSignedIn) {
                                vault.accountDisplayName.substringBefore(" ").take(10)
                            } else {
                                "Sign In"
                            },
                            style = MaterialTheme.typography.labelLarge,
                            fontFamily = GoogleSansDisplayFamily,
                            fontWeight = FontWeight.Bold,
                            color = if (vault.isSignedIn) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                safetyColors.onShieldAmberContainer
                            }
                        )
                    }
                }

                // Sleek Pill Toggle for Low-Vision Scale Boost
                Surface(
                    onClick = onToggleVisionBoost,
                    shape = CircleShape,
                    color = if (controls.seniorLargeVisionBoost) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                    modifier = Modifier
                        .height(40.dp)
                        .semantics {
                            contentDescription = "Toggle Large Text Accessibility Mode"
                            stateDescription = if (controls.seniorLargeVisionBoost) "Enabled" else "Standard"
                        }
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    ) {
                        Text(
                            text = if (controls.seniorLargeVisionBoost) "Aa+" else "Aa",
                            style = MaterialTheme.typography.labelLarge,
                            fontFamily = GoogleSansDisplayFamily,
                            fontWeight = FontWeight.Bold,
                            color = if (controls.seniorLargeVisionBoost) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
            }
        }

        // Expressive Pill Mode Switcher (Eleanor's Screen vs. Family Caregiver)
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = CircleShape,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val tabs = listOf(
                    AppPersonaSurface.SENIOR_APPLIANCE to "Eleanor's View",
                    AppPersonaSurface.CAREGIVER_COMMAND to "Family Caregiver"
                )
                tabs.forEach { (persona, label) ->
                    val isSelected = controls.activePersona == persona
                    val bgColor by animateColorAsState(
                        targetValue = if (isSelected) MaterialTheme.colorScheme.surface else Color.Transparent,
                        label = "TabBg"
                    )
                    val fgColor by animateColorAsState(
                        targetValue = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        label = "TabFg"
                    )

                    Surface(
                        onClick = { onSwitchPersona(persona) },
                        shape = CircleShape,
                        color = bgColor,
                        shadowElevation = if (isSelected) 2.dp else 0.dp,
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .semantics {
                                role = Role.Tab
                                selected = isSelected
                                contentDescription = label
                            }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = fgColor
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Tactile Pill-Shaped Schedule Carousel with Zero Harsh Outlines + Prominent "+ Add Medication" Action.
 */
@Composable
private fun ExpressiveScheduleStrip(
    windows: List<DoseWindowRecord>,
    activeWindowId: DoseWindowId,
    onSelectWindow: (DoseWindowId) -> Unit,
    onOpenAddMedication: () -> Unit = {}
) {
    val safetyColors = KinDoseThemeTokens.safetyColors

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Today's Schedule (${windows.size} Meds)",
                style = MaterialTheme.typography.titleMedium,
                fontFamily = GoogleSansDisplayFamily,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            Surface(
                onClick = onOpenAddMedication,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.semantics {
                    role = Role.Button
                    contentDescription = "Add New Medication to Schedule"
                }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "+ Add Medication",
                        style = MaterialTheme.typography.labelLarge,
                        fontFamily = GoogleSansDisplayFamily,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
        items(windows, key = { it.spec.doseWindowId.value }) { item ->
            val isSelected = item.spec.doseWindowId == activeWindowId
            val (statusLabel, dotColor) = when (item.state) {
                is DoseState.Confirmed -> "Taken" to safetyColors.safeEmerald
                is DoseState.Escalated -> "Alert Sent" to safetyColors.escalationCrimson
                is DoseState.Snoozed -> "Snoozed" to safetyColors.snoozeIndigo
                is DoseState.Due -> "Due Now" to MaterialTheme.colorScheme.primary
                is DoseState.ResolvedByCaregiver -> "Verified" to safetyColors.safeEmerald
                is DoseState.Scheduled -> "Upcoming" to MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                is DoseState.MissedExpired -> "Missed" to safetyColors.escalationCrimson
            }

            Surface(
                onClick = { onSelectWindow(item.spec.doseWindowId) },
                shape = RoundedCornerShape(24.dp),
                color = if (isSelected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surface
                },
                shadowElevation = if (isSelected) 2.dp else 0.dp,
                modifier = Modifier
                    .widthIn(min = 148.dp)
                    .semantics(mergeDescendants = true) {
                        selected = isSelected
                        contentDescription = "${item.spec.scheduledTimeLabel}, ${item.spec.medicationName}, $statusLabel"
                    }
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(dotColor)
                        )
                        Text(
                            text = "${item.spec.scheduledTimeLabel} · $statusLabel",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                    Text(
                        text = item.spec.medicationName,
                        style = MaterialTheme.typography.titleMedium,
                        fontFamily = GoogleSansDisplayFamily,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
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
 * SENIOR APPLIANCE HERO SURFACE:
 * Material 3 Expressive Bento Layout with authentic Google Sans Display typography,
 * sculptural pill artwork, and an unmistakable 92dp tactile confirmation pill button.
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

            Surface(
                shape = RoundedCornerShape(36.dp),
                color = containerColor,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics {
                        liveRegion = LiveRegionMode.Assertive
                        contentDescription = if (isDuplicateTapAlert) {
                            "Hold on! Double-Dose Shield Active. You already took ${spec.medicationName} ${record.duplicateTapWarningMinutes} minutes ago. Do not take another pill."
                        } else {
                            "All done for ${spec.scheduledTimeLabel}. You took your ${spec.medicationName}."
                        }
                    }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(26.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    // Custom Vector Shield / Check Emblem
                    Surface(
                        shape = CircleShape,
                        color = accentColor,
                        modifier = Modifier.size(80.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Canvas(modifier = Modifier.size(38.dp)) {
                                if (isDuplicateTapAlert) {
                                    // Exclamation / Shield Icon
                                    drawLine(
                                        color = Color.White,
                                        start = Offset(size.width / 2f, size.height * 0.18f),
                                        end = Offset(size.width / 2f, size.height * 0.58f),
                                        strokeWidth = 9f,
                                        cap = StrokeCap.Round
                                    )
                                    drawCircle(
                                        color = Color.White,
                                        radius = 5f,
                                        center = Offset(size.width / 2f, size.height * 0.80f)
                                    )
                                } else {
                                    // Bold Checkmark
                                    val path = Path().apply {
                                        moveTo(size.width * 0.20f, size.height * 0.52f)
                                        lineTo(size.width * 0.42f, size.height * 0.74f)
                                        lineTo(size.width * 0.82f, size.height * 0.28f)
                                    }
                                    drawPath(
                                        path = path,
                                        color = Color.White,
                                        style = Stroke(width = 9f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                                    )
                                }
                            }
                        }
                    }

                    if (isDuplicateTapAlert) {
                        Surface(
                            color = safetyColors.shieldAmber,
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "DO NOT TAKE A SECOND PILL",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = safetyColors.onShieldAmber,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "You already took ${spec.medicationName} ${record.duplicateTapWarningMinutes} minutes ago (at 8:02 AM).",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = safetyColors.onShieldAmber,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = if (isDuplicateTapAlert) {
                                "Double-Dose Shield Active"
                            } else {
                                "All Done for ${spec.scheduledTimeLabel}"
                            },
                            style = MaterialTheme.typography.displaySmall,
                            color = onContainerColor,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.semantics { heading() }
                        )
                        Text(
                            text = "${spec.medicationName} (${spec.strengthLabel}) is complete · Maya has been notified.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = onContainerColor.copy(alpha = 0.88f),
                            textAlign = TextAlign.Center
                        )
                    }

                    ExpressivePillBentoRow(spec = spec)

                    if (!isDuplicateTapAlert) {
                        Surface(
                            onClick = onConfirmTap,
                            shape = CircleShape,
                            color = Color.White.copy(alpha = 0.65f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 54.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 16.dp)) {
                                Text(
                                    text = "Tap again to see how Double-Dose Shield protects you",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = onContainerColor
                                )
                            }
                        }
                    }
                }
            }
        }

        is DoseState.ResolvedByCaregiver -> {
            Surface(
                shape = RoundedCornerShape(36.dp),
                color = safetyColors.safeEmeraldContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(26.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "Verified by ${state.caregiverName}",
                        style = MaterialTheme.typography.displaySmall,
                        color = safetyColors.onSafeEmeraldContainer,
                        modifier = Modifier.semantics { heading() }
                    )
                    Text(
                        text = "\"${state.resolutionNote}\"",
                        style = MaterialTheme.typography.bodyLarge,
                        color = safetyColors.onSafeEmeraldContainer
                    )
                    ExpressivePillBentoRow(spec = spec)
                }
            }
        }

        else -> {
            // ACTIVE DOSE HERO CARD (Material 3 Expressive Bento)
            val isEscalated = state is DoseState.Escalated
            val isSnoozed = state is DoseState.Snoozed

            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Surface(
                    shape = RoundedCornerShape(36.dp),
                    color = if (isEscalated) {
                        safetyColors.escalationCrimsonContainer
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
                    shadowElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp)
                    ) {
                        // Top Row: Warm Time Badge + Strength Pill
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                color = when {
                                    isEscalated -> safetyColors.escalationCrimson
                                    isSnoozed -> safetyColors.snoozeIndigo
                                    else -> MaterialTheme.colorScheme.primaryContainer
                                },
                                shape = CircleShape
                            ) {
                                Text(
                                    text = when {
                                        isEscalated -> "Overdue · Family Alerted"
                                        isSnoozed -> "Snoozed (${state.snoozeCount} of ${spec.maxSnoozes})"
                                        else -> "${spec.scheduledTimeLabel} Morning Dose"
                                    },
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (isEscalated || isSnoozed) {
                                        Color.White
                                    } else {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    },
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                )
                            }

                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = CircleShape
                            ) {
                                Text(
                                    text = spec.strengthLabel,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontFamily = GoogleSansDisplayFamily,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                                )
                            }
                        }

                        // Hero Medication Display Name (Google Sans Display Bold)
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = spec.medicationName,
                                style = MaterialTheme.typography.displayMedium,
                                color = if (isEscalated) {
                                    safetyColors.onEscalationCrimsonContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                                modifier = Modifier.semantics { heading() }
                            )
                            Text(
                                text = spec.clinicalPurpose,
                                style = MaterialTheme.typography.titleMedium,
                                color = if (isEscalated) {
                                    safetyColors.onEscalationCrimsonContainer.copy(alpha = 0.85f)
                                } else {
                                    MaterialTheme.colorScheme.primary
                                }
                            )
                        }

                        // 2-Column Tactile Bento Row: Visual Pill Identification + Water/Meal Instruction
                        ExpressivePillBentoRow(spec = spec)

                        // GIANT TACTILE CONFIRMATION PILL BUTTON (94.dp height, Expressive Squircle)
                        Button(
                            onClick = onConfirmTap,
                            shape = RoundedCornerShape(30.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = safetyColors.safeEmerald,
                                contentColor = safetyColors.onSafeEmerald
                            ),
                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 94.dp)
                                .semantics {
                                    role = Role.Button
                                    contentDescription = "I took my ${spec.scheduledTimeLabel} ${spec.medicationName} ${spec.strengthLabel}. Tap once to confirm."
                                }
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                // Tactile White Circle Check Badge
                                Surface(
                                    shape = CircleShape,
                                    color = Color.White.copy(alpha = 0.22f),
                                    modifier = Modifier.size(54.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Canvas(modifier = Modifier.size(26.dp)) {
                                            val path = Path().apply {
                                                moveTo(size.width * 0.18f, size.height * 0.52f)
                                                lineTo(size.width * 0.42f, size.height * 0.76f)
                                                lineTo(size.width * 0.84f, size.height * 0.26f)
                                            }
                                            drawPath(
                                                path = path,
                                                color = Color.White,
                                                style = Stroke(width = 7f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                                            )
                                        }
                                    }
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "I Took This Pill",
                                        style = MaterialTheme.typography.headlineMedium,
                                        fontFamily = GoogleSansDisplayFamily,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Text(
                                        text = if (isEscalated) {
                                            "Sends instant All-Clear to Maya"
                                        } else {
                                            "Confirm ${spec.scheduledTimeLabel} dose & lock safety shield"
                                        },
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color.White.copy(alpha = 0.9f)
                                    )
                                }
                            }
                        }

                        // Soft Tonal Snooze Pill Button
                        if (!isEscalated) {
                            val usedSnoozes = (state as? DoseState.Snoozed)?.snoozeCount ?: 0
                            val remainingSnoozes = (spec.maxSnoozes - usedSnoozes).coerceAtLeast(0)
                            FilledTonalButton(
                                onClick = onSnoozeTap,
                                enabled = remainingSnoozes > 0,
                                shape = RoundedCornerShape(22.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 58.dp)
                            ) {
                                Text(
                                    text = if (remainingSnoozes > 0) {
                                        "Remind me in 10 minutes ($remainingSnoozes left)"
                                    } else {
                                        "Max snoozes used · Caregiver alert at 8:30 AM"
                                    },
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 2-Tile Expressive Bento Row for Pill Appearance & Dosage Guidance.
 */
@Composable
private fun ExpressivePillBentoRow(spec: DoseScheduleSpec) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Left Bento Tile: Sculptural Pill Visual Check
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .semantics(mergeDescendants = true) {
                    contentDescription = "Pill visual check: ${spec.pillColorLabel}, ${spec.pillShape.a11yDescription}, stamped ${spec.imprintCode}"
                }
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SculpturalPillCanvas(pillShape = spec.pillShape, modifier = Modifier.size(48.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = spec.imprintCode,
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = GoogleSansMonoFamily,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = spec.pillColorLabel,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = spec.pillShape.displayName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Right Bento Tile: How to Take (Water & Food Guidance)
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .semantics(mergeDescendants = true) {}
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Custom Vector Water Glass / Droplet Emblem
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Canvas(modifier = Modifier.size(22.dp)) {
                            val drop = Path().apply {
                                moveTo(size.width / 2f, size.height * 0.10f)
                                cubicTo(
                                    size.width * 0.88f, size.height * 0.50f,
                                    size.width * 0.85f, size.height * 0.88f,
                                    size.width / 2f, size.height * 0.90f
                                )
                                cubicTo(
                                    size.width * 0.15f, size.height * 0.88f,
                                    size.width * 0.12f, size.height * 0.50f,
                                    size.width / 2f, size.height * 0.10f
                                )
                                close()
                            }
                            drawPath(drop, color = Color(0xFF1D4ED8))
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "1 Tablet · Full Water",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = spec.foodWarning,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun SculpturalPillCanvas(
    pillShape: PillShape,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface)
    ) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        when (pillShape) {
            PillShape.ROUND_TABLET -> {
                val r = size.minDimension * 0.34f
                drawCircle(
                    brush = Brush.linearGradient(
                        colors = listOf(Color(0xFFFFB2C9), Color(0xFFEC407A))
                    ),
                    radius = r,
                    center = Offset(cx, cy)
                )
                drawLine(
                    color = Color.White.copy(alpha = 0.75f),
                    start = Offset(cx - r * 0.72f, cy),
                    end = Offset(cx + r * 0.72f, cy),
                    strokeWidth = 3.5f,
                    cap = StrokeCap.Round
                )
            }

            PillShape.OVAL_CAPLET -> {
                val w = size.width * 0.70f
                val h = size.height * 0.40f
                drawRoundRect(
                    brush = Brush.linearGradient(
                        colors = listOf(Color(0xFFFFE082), Color(0xFFFFB300))
                    ),
                    topLeft = Offset(cx - w / 2f, cy - h / 2f),
                    size = Size(w, h),
                    cornerRadius = CornerRadius(h / 2f, h / 2f)
                )
            }

            PillShape.TWO_TONE_CAPSULE -> {
                val w = size.width * 0.72f
                val h = size.height * 0.38f
                drawRoundRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(Color(0xFF42A5F5), Color(0xFFE3F2FD))
                    ),
                    topLeft = Offset(cx - w / 2f, cy - h / 2f),
                    size = Size(w, h),
                    cornerRadius = CornerRadius(h / 2f, h / 2f)
                )
            }
        }
    }
}

/**
 * FAMILY CAREGIVER COMMAND CENTER SURFACE:
 * Fitbit / Google Health Bento Dashboard with Circular Adherence Ring,
 * Escalation Resolution Card, and Multi-Provider CredentialManager Sync Vault.
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
        // 1. Live Escalation Card (When 8:30 AM Grace Period Expires)
        if (state is DoseState.Escalated) {
            Surface(
                shape = RoundedCornerShape(32.dp),
                color = safetyColors.escalationCrimsonContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { liveRegion = LiveRegionMode.Assertive }
            ) {
                Column(
                    modifier = Modifier.padding(22.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(safetyColors.escalationCrimson)
                        )
                        Text(
                            text = "NEEDS CHECK-IN · 8:30 AM GRACE EXPIRED",
                            style = MaterialTheme.typography.labelLarge,
                            color = safetyColors.escalationCrimson,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = "${snapshot.patientName} hasn't tapped her ${activeRecord.spec.scheduledTimeLabel} ${activeRecord.spec.medicationName} yet.",
                        style = MaterialTheme.typography.headlineMedium,
                        color = safetyColors.onEscalationCrimsonContainer,
                        modifier = Modifier.semantics { heading() }
                    )

                    Text(
                        text = "Phone is online (84% battery) · ${state.snoozeCountPrior} snoozes used.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = safetyColors.onEscalationCrimsonContainer.copy(alpha = 0.85f)
                    )

                    Button(
                        onClick = {
                            onResolveTap("Called Mom at 8:32 AM — confirmed she took ${activeRecord.spec.medicationName} with breakfast.")
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = safetyColors.escalationCrimson,
                            contentColor = safetyColors.onEscalationCrimson
                        ),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 60.dp)
                    ) {
                        Text(
                            text = "I Called Mom — Mark Dose Safe",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // 2. Hero Adherence Ring & Live Device Health Bento Card
        Surface(
            shape = RoundedCornerShape(32.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 1.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    // Custom Circular Adherence Ring (98%)
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(96.dp)
                    ) {
                        val trackColor = MaterialTheme.colorScheme.surfaceVariant
                        val progressColor = safetyColors.safeEmerald
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val stroke = 10.dp.toPx()
                            drawArc(
                                color = trackColor,
                                startAngle = 0f,
                                sweepAngle = 360f,
                                useCenter = false,
                                style = Stroke(width = stroke, cap = StrokeCap.Round)
                            )
                            drawArc(
                                color = progressColor,
                                startAngle = -90f,
                                sweepAngle = 360f * (snapshot.thirtyDayAdherencePercent / 100f),
                                useCenter = false,
                                style = Stroke(width = stroke, cap = StrokeCap.Round)
                            )
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "${snapshot.thirtyDayAdherencePercent}%",
                                style = MaterialTheme.typography.headlineSmall,
                                fontFamily = GoogleSansDisplayFamily,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "30-Day",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "${snapshot.adherenceStreakDays}-Day Safe Streak",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.semantics { heading() }
                        )
                        Text(
                            text = "Monitored by ${snapshot.caregiverName} · Zero accidental double-doses.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = CircleShape
                        ) {
                            Text(
                                text = "Mom's Pixel · 84% Battery · Online",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }

        // 3. Multi-Provider CredentialManager & Zero-Loss Device Restore Bento Card
        Surface(
            shape = RoundedCornerShape(32.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 1.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text(
                            text = "Cloud Sync & Device Backup",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.semantics { heading() }
                        )
                        Text(
                            text = "${snapshot.cloudVault.primaryEmail} · ${snapshot.cloudVault.currentDeviceModel}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Surface(
                        color = safetyColors.safeEmeraldContainer,
                        shape = CircleShape
                    ) {
                        Text(
                            text = "Synced",
                            style = MaterialTheme.typography.labelMedium,
                            color = safetyColors.onSafeEmeraldContainer,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(IdentityProvider.entries) { provider ->
                        val isActive = snapshot.cloudVault.activeProvider == provider
                        FilterChip(
                            selected = isActive,
                            onClick = { onSwitchIdentityProvider(provider) },
                            shape = CircleShape,
                            label = {
                                Text(
                                    text = provider.displayName,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FilledTonalButton(
                        onClick = onSimulateNewPhoneRestore,
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.weight(1f).heightIn(min = 50.dp)
                    ) {
                        Text(
                            text = "Test New Phone Restore",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                    OutlinedButton(
                        onClick = onOpenAuditLedger,
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.heightIn(min = 50.dp)
                    ) {
                        Text(
                            text = "Audit Log (${snapshot.auditLedger.size})",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }
    }
}

/**
 * Sleek Floating Pill Dock at the bottom for one-tap interactive scenario testing.
 */
@Composable
private fun FloatingScenarioPillBar(
    activeRecord: DoseWindowRecord,
    onOpenAddMedication: () -> Unit = {},
    onSimulateDuplicateTap: () -> Unit,
    onSimulateEscalation: () -> Unit,
    onResetScenario: () -> Unit,
    onOpenAccountSheet: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp,
            shadowElevation = 6.dp
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    onClick = onOpenAddMedication,
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary
                ) {
                    Text(
                        text = "+ Add Med",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                    )
                }

                Surface(
                    onClick = onSimulateDuplicateTap,
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = "2x Tap Shield",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(horizontal = 13.dp, vertical = 10.dp)
                    )
                }

                Surface(
                    onClick = onSimulateEscalation,
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.tertiaryContainer
                ) {
                    Text(
                        text = "8:30 Alert",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                    )
                }

                Surface(
                    onClick = onOpenAccountSheet,
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = "Sign In",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                    )
                }
            }
        }
    }
}

/**
 * First-Class Multi-Provider Sign-In, CredentialManager & Zero-Loss Device Restore Sheet.
 * Supports:
 * 1. Continue with Google (Jetpack CredentialManager / GetGoogleIdOption)
 * 2. Sign in with Biometric Passkey (FIDO2 / WebAuthn)
 * 3. Continue with Facebook (OAuth 2.1)
 * 4. Continue with Apple (OIDC)
 * 5. Senior Zero-Password QR Pairing Code (`849-204`)
 * 6. One-Tap New Phone Restore & Sign Out
 */
@Composable
private fun AccountSignInModalSheetContent(
    snapshot: RepositorySnapshot,
    onSignInWithProvider: (IdentityProvider, String, String) -> Unit,
    onSignOut: () -> Unit,
    onSimulateNewPhoneRestore: () -> Unit,
    onOpenAuditLedger: () -> Unit,
    onClose: () -> Unit
) {
    val safetyColors = KinDoseThemeTokens.safetyColors
    val vault = snapshot.cloudVault
    val context = LocalContext.current
    var showGm3GooglePicker by remember { mutableStateOf(false) }
    var deviceGoogleEmails by remember { mutableStateOf(emptyList<String>()) }

    fun refreshDeviceGoogleAccounts(): List<String> {
        val discovered = try {
            AccountManager.get(context)
                .getAccountsByType("com.google")
                .mapNotNull { it.name?.trim() }
                .filter { it.isNotEmpty() }
        } catch (_: Throwable) {
            emptyList()
        }
        deviceGoogleEmails = discovered
        return discovered
    }

    val googleAccountPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val selectedEmail = result.data
                ?.getStringExtra(AccountManager.KEY_ACCOUNT_NAME)
                ?.trim()
                .orEmpty()
            if (selectedEmail.isNotEmpty()) {
                deviceGoogleEmails = (listOf(selectedEmail) + deviceGoogleEmails).distinct()
                val localPart = selectedEmail.substringBefore("@")
                val formattedName = localPart
                    .split(".", "_", "-")
                    .filter { it.isNotBlank() }
                    .joinToString(" ") { part ->
                        part.replaceFirstChar { ch -> ch.uppercase() }
                    }
                    .ifBlank { selectedEmail }
                onSignInWithProvider(
                    IdentityProvider.GOOGLE_ONE_TAP,
                    selectedEmail,
                    formattedName
                )
            }
        }
    }

    if (showGm3GooglePicker) {
        Gm3ExpressiveGoogleAccountPickerDialog(
            discoveredEmails = deviceGoogleEmails,
            activeEmail = if (vault.isSignedIn) vault.primaryEmail else null,
            onSelectGoogleAccount = { email, displayName ->
                showGm3GooglePicker = false
                onSignInWithProvider(IdentityProvider.GOOGLE_ONE_TAP, email, displayName)
            },
            onAddAnotherGoogleAccount = {
                showGm3GooglePicker = false
                try {
                    val addAccountIntent = Intent(Settings.ACTION_ADD_ACCOUNT).apply {
                        putExtra(Settings.EXTRA_ACCOUNT_TYPES, arrayOf("com.google"))
                    }
                    googleAccountPickerLauncher.launch(addAccountIntent)
                } catch (_: Throwable) {
                    @Suppress("DEPRECATION")
                    val fallbackIntent = AccountManager.newChooseAccountIntent(
                        null, null, arrayOf("com.google"), null, null, null, null
                    )
                    googleAccountPickerLauncher.launch(fallbackIntent)
                }
            },
            onDismiss = { showGm3GooglePicker = false }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp, vertical = 8.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f).padding(end = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "Sign In & Cloud Vault",
                    style = MaterialTheme.typography.headlineSmall,
                    fontFamily = GoogleSansDisplayFamily,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.semantics { heading() }
                )
                Text(
                    text = "Keep schedules & streaks safe across any phone change",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = onClose) {
                Text("Close")
            }
        }

        // Active Account Banner
        Surface(
            color = if (vault.isSignedIn) {
                safetyColors.safeEmeraldContainer
            } else {
                safetyColors.shieldAmberContainer
            },
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    modifier = Modifier.weight(1f).padding(end = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = if (vault.isSignedIn) {
                            "${vault.accountDisplayName} · ${vault.activeProvider.displayName}"
                        } else {
                            "Signed Out (Local Offline Mode)"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (vault.isSignedIn) {
                            safetyColors.onSafeEmeraldContainer
                        } else {
                            safetyColors.onShieldAmberContainer
                        }
                    )
                    Text(
                        text = if (vault.isSignedIn) {
                            "${vault.primaryEmail} · ${vault.currentDeviceModel}"
                        } else {
                            "Select any provider below to restore your Family Vault"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (vault.isSignedIn) {
                            safetyColors.onSafeEmeraldContainer.copy(alpha = 0.85f)
                        } else {
                            safetyColors.onShieldAmberContainer.copy(alpha = 0.85f)
                        }
                    )
                }

                if (vault.isSignedIn) {
                    Surface(
                        onClick = onSignOut,
                        shape = CircleShape,
                        color = Color.White.copy(alpha = 0.75f)
                    ) {
                        Text(
                            text = "Sign Out",
                            style = MaterialTheme.typography.labelMedium,
                            color = safetyColors.onSafeEmeraldContainer,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }
            }
        }

        Text(
            text = "CHOOSE SIGN-IN METHOD (JETPACK CREDENTIAL MANAGER)",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // 1. Continue with Google (Launches GM3 Expressive CredentialManager Account Switcher)
        ProviderSignInButton(
            badgeText = "G",
            badgeColor = Color(0xFF4285F4),
            title = "Continue with Google",
            subtitle = if (vault.isSignedIn && vault.activeProvider == IdentityProvider.GOOGLE_ONE_TAP) {
                "Signed in as ${vault.primaryEmail} · Tap to switch Google account"
            } else {
                "GM3 Expressive Google Account Switcher (Device com.google)"
            },
            isCurrent = vault.isSignedIn && vault.activeProvider == IdentityProvider.GOOGLE_ONE_TAP,
            onClick = {
                refreshDeviceGoogleAccounts()
                showGm3GooglePicker = true
            }
        )

        // 2. Sign in with Biometric Passkey
        ProviderSignInButton(
            badgeText = "PK",
            badgeColor = Color(0xFF0D7452),
            title = "Sign in with Biometric Passkey",
            subtitle = "FIDO2 / WebAuthn Fingerprint · Synced in Google Password Manager",
            isCurrent = vault.isSignedIn && vault.activeProvider == IdentityProvider.PASSKEY_FIDO2,
            onClick = {
                onSignInWithProvider(
                    IdentityProvider.PASSKEY_FIDO2,
                    "maya.vance@passkey.local",
                    "Maya Vance (Passkey)"
                )
            }
        )

        // 3. Continue with Facebook
        ProviderSignInButton(
            badgeText = "f",
            badgeColor = Color(0xFF1877F2),
            title = "Continue with Facebook",
            subtitle = "Meta OAuth 2.1 · Linked to Family Vault ${vault.familyVaultId}",
            isCurrent = vault.isSignedIn && vault.activeProvider == IdentityProvider.FACEBOOK_OAUTH,
            onClick = {
                onSignInWithProvider(
                    IdentityProvider.FACEBOOK_OAUTH,
                    "maya.vance.fb@facebook.com",
                    "Maya Vance (FB)"
                )
            }
        )

        // 4. Continue with Apple ID
        ProviderSignInButton(
            badgeText = "A",
            badgeColor = Color(0xFF1F2937),
            title = "Continue with Apple",
            subtitle = "Apple OIDC · Cross-platform family access",
            isCurrent = vault.isSignedIn && vault.activeProvider == IdentityProvider.APPLE_ID,
            onClick = {
                onSignInWithProvider(
                    IdentityProvider.APPLE_ID,
                    "maya.vance@icloud.com",
                    "Maya Vance (Apple)"
                )
            }
        )

        // 5. Senior Zero-Password QR Pairing Card
        Surface(
            onClick = {
                onSignInWithProvider(
                    IdentityProvider.SENIOR_QR_PAIRING,
                    "eleanor.paired@kindose.family",
                    "Eleanor's Paired Phone"
                )
            },
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
            shape = RoundedCornerShape(22.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.size(52.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "QR",
                            style = MaterialTheme.typography.titleMedium,
                            fontFamily = GoogleSansMonoFamily,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Pair Mom's Phone via QR Code (${vault.seniorPairingCode})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Zero passwords for seniors — scan from caregiver's phone",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // 6. Firebase Cloud Messaging (FCM) High-Priority Escalation Status
        Surface(
            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Firebase Cloud Messaging (FCM)",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "${vault.fcmPushDeliveredCount} Pushes · ${vault.fcmTokenPreview.take(12)}...",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = GoogleSansMonoFamily,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
                Text(
                    text = vault.lastFcmPushSummary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f)
                )
            }
        }

        // Device Migration & Audit Actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            FilledTonalButton(
                onClick = onSimulateNewPhoneRestore,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.weight(1f).heightIn(min = 50.dp)
            ) {
                Text("Simulate New Phone Restore")
            }
            OutlinedButton(
                onClick = onOpenAuditLedger,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.heightIn(min = 50.dp)
            ) {
                Text("Audit Logs")
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}

private data class QuickMedicationPreset(
    val name: String,
    val strength: String,
    val timeLabel: String,
    val purpose: String,
    val foodWarning: String,
    val shape: PillShape,
    val colorLabel: String,
    val imprint: String
)

/**
 * First-Class "Add New Medication" Material 3 Expressive Sheet.
 * Supports 1-tap clinical presets + custom Medication Name, Strength, Schedule Time,
 * Visual Pill Shape, and Pill Color so seniors and caregivers can add any medication end-to-end.
 */
@Composable
private fun AddMedicationModalSheetContent(
    onSaveMedication: (
        medicationName: String,
        strengthLabel: String,
        scheduledTimeLabel: String,
        clinicalPurpose: String,
        foodWarning: String,
        pillShape: PillShape,
        pillColorLabel: String,
        imprintCode: String
    ) -> Unit,
    onClose: () -> Unit
) {
    val presets = remember {
        listOf(
            QuickMedicationPreset(
                name = "Metformin",
                strength = "500 mg",
                timeLabel = "8:00 AM",
                purpose = "Blood Sugar & Metabolic Control",
                foodWarning = "Take with breakfast to prevent stomach upset",
                shape = PillShape.ROUND_TABLET,
                colorLabel = "White Round Tablet",
                imprint = "GL 500"
            ),
            QuickMedicationPreset(
                name = "Aspirin",
                strength = "81 mg",
                timeLabel = "9:00 AM",
                purpose = "Daily Heart & Platelet Protection",
                foodWarning = "Take with a full glass of water",
                shape = PillShape.ROUND_TABLET,
                colorLabel = "Warm Yellow Enteric Tablet",
                imprint = "BAYER 81"
            ),
            QuickMedicationPreset(
                name = "Vitamin D3",
                strength = "2000 IU",
                timeLabel = "1:00 PM",
                purpose = "Bone Density & Immune Support",
                foodWarning = "Best absorbed with lunch",
                shape = PillShape.OVAL_CAPLET,
                colorLabel = "Golden Softgel Caplet",
                imprint = "D3 2K"
            ),
            QuickMedicationPreset(
                name = "Amlodipine",
                strength = "5 mg",
                timeLabel = "8:00 PM",
                purpose = "Evening Blood Pressure Support",
                foodWarning = "Can be taken with or without food",
                shape = PillShape.TWO_TONE_CAPSULE,
                colorLabel = "Blue & White Capsule",
                imprint = "NOR 5"
            )
        )
    }

    var medName by remember { mutableStateOf("Metformin") }
    var strength by remember { mutableStateOf("500 mg") }
    var selectedTime by remember { mutableStateOf("8:00 AM") }
    var purpose by remember { mutableStateOf("Blood Sugar & Metabolic Control") }
    var foodNote by remember { mutableStateOf("Take with breakfast to prevent stomach upset") }
    var selectedShape by remember { mutableStateOf(PillShape.ROUND_TABLET) }
    var colorLabel by remember { mutableStateOf("White Round Tablet") }
    var imprint by remember { mutableStateOf("GL 500") }

    val timeOptions = listOf("8:00 AM", "9:00 AM", "1:00 PM", "6:00 PM", "8:00 PM")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp, vertical = 8.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                Text(
                    text = "Add New Medication",
                    style = MaterialTheme.typography.headlineSmall,
                    fontFamily = GoogleSansDisplayFamily,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.semantics { heading() }
                )
                Text(
                    text = "Tap a quick preset or enter custom prescription details",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = onClose) {
                Text("Close")
            }
        }

        // 1. One-Tap Common Senior Medication Presets
        Text(
            text = "QUICK PRESETS (1-TAP AUTO-FILL)",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(presets) { preset ->
                val isSelected = medName.equals(preset.name, ignoreCase = true)
                FilterChip(
                    selected = isSelected,
                    onClick = {
                        medName = preset.name
                        strength = preset.strength
                        selectedTime = preset.timeLabel
                        purpose = preset.purpose
                        foodNote = preset.foodWarning
                        selectedShape = preset.shape
                        colorLabel = preset.colorLabel
                        imprint = preset.imprint
                    },
                    shape = CircleShape,
                    label = {
                        Text(
                            text = "${preset.name} (${preset.strength})",
                            fontFamily = GoogleSansDisplayFamily,
                            fontWeight = FontWeight.Bold
                        )
                    }
                )
            }
        }

        // 2. Medication Name & Strength Inputs
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedTextField(
                value = medName,
                onValueChange = { medName = it },
                label = { Text("Medication Name") },
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.weight(1.4f)
            )
            OutlinedTextField(
                value = strength,
                onValueChange = { strength = it },
                label = { Text("Strength") },
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.weight(1f)
            )
        }

        // 3. Clinical Purpose Input
        OutlinedTextField(
            value = purpose,
            onValueChange = { purpose = it },
            label = { Text("What is this medication for?") },
            singleLine = true,
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth()
        )

        // 4. Schedule Time Slot Picker
        Text(
            text = "SCHEDULE TIME OF DAY",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(timeOptions) { timeSlot ->
                FilterChip(
                    selected = selectedTime == timeSlot,
                    onClick = { selectedTime = timeSlot },
                    shape = CircleShape,
                    label = {
                        Text(
                            text = timeSlot,
                            fontFamily = GoogleSansDisplayFamily,
                            fontWeight = FontWeight.Bold
                        )
                    }
                )
            }
        }

        // 5. Visual Pill Shape Picker (Crucial for Senior Safety Identification)
        Text(
            text = "PILL APPEARANCE FOR SENIOR VISUAL CHECK",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PillShape.entries.forEach { shape ->
                val isChosen = selectedShape == shape
                Surface(
                    onClick = {
                        selectedShape = shape
                        colorLabel = when (shape) {
                            PillShape.ROUND_TABLET -> "White Round Tablet"
                            PillShape.OVAL_CAPLET -> "Golden Oval Caplet"
                            PillShape.TWO_TONE_CAPSULE -> "Blue & White Capsule"
                        }
                    },
                    shape = RoundedCornerShape(18.dp),
                    color = if (isChosen) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = shape.a11yDescription.substringBefore(","),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (isChosen) FontWeight.Bold else FontWeight.Medium,
                        color = if (isChosen) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp)
                    )
                }
            }
        }

        // 6. Save & Schedule Primary CTA Button
        Button(
            onClick = {
                onSaveMedication(
                    medName,
                    strength,
                    selectedTime,
                    purpose,
                    foodNote,
                    selectedShape,
                    colorLabel,
                    imprint
                )
            },
            shape = RoundedCornerShape(24.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
        ) {
            Text(
                text = "Save & Schedule $medName ($selectedTime)",
                style = MaterialTheme.typography.titleMedium,
                fontFamily = GoogleSansDisplayFamily,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}

/**
 * Latest Google Material 3 Expressive (`GM3`) One-Google / CredentialManager Account Switcher.
 * Replaces the legacy AOSP Material 2 radio-button dialog with a 32dp Expressive container,
 * 4-color Google ring avatar, Google Sans Display typography, and pill-shaped "Continue as" CTA.
 */
@Composable
private fun Gm3ExpressiveGoogleAccountPickerDialog(
    discoveredEmails: List<String>,
    activeEmail: String?,
    onSelectGoogleAccount: (email: String, displayName: String) -> Unit,
    onAddAnotherGoogleAccount: () -> Unit,
    onDismiss: () -> Unit
) {
    val accountsToDisplay = remember(discoveredEmails, activeEmail) {
        val base = discoveredEmails.toMutableList()
        if (!activeEmail.isNullOrBlank() && activeEmail.contains("@") && activeEmail !in base) {
            base.add(activeEmail)
        }
        base.distinct()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(32.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            shadowElevation = 14.dp,
            modifier = Modifier.fillMaxWidth(0.92f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // GM3 Header: 4-Color Google Badge + Google Sans Display Title
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f).padding(end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        GoogleFourColorRingBadge(initial = "G", sizeDp = 36)
                        Column {
                            Text(
                                text = "Sign in with Google",
                                style = MaterialTheme.typography.titleMedium,
                                fontFamily = GoogleSansDisplayFamily,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Choose an account on this device",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    TextButton(onClick = onDismiss) {
                        Text("Close")
                    }
                }

                // Discovered Device Google Accounts (GM3 Expressive Cards, Zero M2 Radio Buttons)
                accountsToDisplay.forEach { email ->
                    val localPart = email.substringBefore("@")
                    val formattedName = localPart
                        .split(".", "_", "-")
                        .filter { it.isNotBlank() }
                        .joinToString(" ") { part ->
                            part.replaceFirstChar { ch -> ch.uppercase() }
                        }
                        .ifBlank { email }
                    val firstName = formattedName.substringBefore(" ")
                    val isSelected = email.equals(activeEmail, ignoreCase = true)

                    Surface(
                        onClick = { onSelectGoogleAccount(email, formattedName) },
                        shape = RoundedCornerShape(24.dp),
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                GoogleFourColorRingBadge(
                                    initial = formattedName.take(1).uppercase(),
                                    sizeDp = 46
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = formattedName,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontFamily = GoogleSansDisplayFamily,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = email,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (isSelected) {
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.primary
                                    ) {
                                        Text(
                                            text = "Signed In",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                        )
                                    }
                                }
                            }

                            // GM3 Expressive Full-Width Pill Button ("Continue as ...")
                            Button(
                                onClick = { onSelectGoogleAccount(email, formattedName) },
                                shape = CircleShape,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF0B57D0),
                                    contentColor = Color.White
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                            ) {
                                Text(
                                    text = "Continue as $firstName",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontFamily = GoogleSansDisplayFamily,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // Add Another Google Account Pill Row
                Surface(
                    onClick = onAddAnotherGoogleAccount,
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "+",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Use another Google account",
                                style = MaterialTheme.typography.titleSmall,
                                fontFamily = GoogleSansDisplayFamily,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Add or switch via Google Play Services",
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
private fun GoogleFourColorRingBadge(
    initial: String,
    sizeDp: Int
) {
    Box(
        modifier = Modifier.size(sizeDp.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = 3.2.dp.toPx()
            val inset = stroke / 2f
            val arcSize = Size(size.width - stroke, size.height - stroke)
            val topLeft = Offset(inset, inset)
            // Google 4-Color Expressive Ring (Blue, Red, Yellow, Green)
            drawArc(
                color = Color(0xFF4285F4),
                startAngle = -45f,
                sweepAngle = 100f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
            drawArc(
                color = Color(0xFFEA4335),
                startAngle = 185f,
                sweepAngle = 90f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
            drawArc(
                color = Color(0xFFFBBC05),
                startAngle = 115f,
                sweepAngle = 70f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
            drawArc(
                color = Color(0xFF34A853),
                startAngle = 55f,
                sweepAngle = 60f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
        }
        Surface(
            shape = CircleShape,
            color = Color(0xFFE8F0FE),
            modifier = Modifier.size((sizeDp - 8).dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = initial,
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = GoogleSansDisplayFamily,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0B57D0)
                )
            }
        }
    }
}

@Composable
private fun ProviderSignInButton(
    badgeText: String,
    badgeColor: Color,
    title: String,
    subtitle: String,
    isCurrent: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(22.dp),
        color = if (isCurrent) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        },
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = "$title, $subtitle"
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = badgeColor,
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = badgeText,
                        style = MaterialTheme.typography.titleMedium,
                        fontFamily = GoogleSansDisplayFamily,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = GoogleSansDisplayFamily,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (isCurrent) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary
                ) {
                    Text(
                        text = "Active",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
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
            text = "Every state transition commits atomically alongside its idempotency key and outbox payload.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        snapshot.auditLedger.forEach { audit ->
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "[${audit.formattedTime}] ${audit.auditId}: ${audit.fromState} -> ${audit.toState}",
                        style = MaterialTheme.typography.labelLarge,
                        fontFamily = GoogleSansMonoFamily
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
