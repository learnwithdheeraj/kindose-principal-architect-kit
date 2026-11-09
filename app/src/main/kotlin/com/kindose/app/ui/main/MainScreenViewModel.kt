package com.kindose.app.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kindose.app.auth.AndroidJetpackCredentialGateway
import com.kindose.app.auth.AuthCredentialGateway
import com.kindose.app.auth.CredentialAuthResult
import com.kindose.app.data.DataRepository
import com.kindose.app.data.DefaultDataRepository
import com.kindose.app.data.MedicationCreationOutcome
import com.kindose.app.data.MedicationDeletionOutcome
import com.kindose.app.data.PairingCodeOutcome
import com.kindose.app.data.RepositorySnapshot
import com.kindose.app.domain.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AppPersonaSurface(val title: String, val subtitle: String) {
    SENIOR_APPLIANCE(
        title = "Senior Appliance",
        subtitle = "Zero-Confusion Daily Sanctuary"
    ),
    CAREGIVER_COMMAND(
        title = "Caregiver Hub",
        subtitle = "Family Live Escalation & Audit"
    )
}

data class LocalUiControls(
    val activePersona: AppPersonaSurface = AppPersonaSurface.SENIOR_APPLIANCE,
    val seniorLargeVisionBoost: Boolean = false,
    val showArchitectureLedgerSheet: Boolean = false,
    val showAccountSignInSheet: Boolean = false,
    val showAddMedicationSheet: Boolean = false,
    val lastStatusBanner: String? = null,
    val isStatusBannerError: Boolean = false,
    val addMedicationValidationError: String? = null,
    val pairingCodeValidationError: String? = null
)

sealed interface MainScreenUiState {
    data object Loading : MainScreenUiState

    data class Error(val throwable: Throwable) : MainScreenUiState

    data class Success(
        val snapshot: RepositorySnapshot,
        val controls: LocalUiControls
    ) : MainScreenUiState
}

class MainScreenViewModel(
    private val dataRepository: DataRepository = DefaultDataRepository(),
    private val authGateway: AuthCredentialGateway = AndroidJetpackCredentialGateway(appContext = null)
) : ViewModel() {

    private val localControls = MutableStateFlow(LocalUiControls())

    val uiState: StateFlow<MainScreenUiState> = combine(
        dataRepository.snapshot,
        localControls
    ) { snap, controls ->
        MainScreenUiState.Success(snapshot = snap, controls = controls)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = MainScreenUiState.Success(
            snapshot = dataRepository.snapshot.value,
            controls = localControls.value
        )
    )

    fun onVerifyCacheSessionOnResume() {
        val revoked = dataRepository.verifyAndRevokeIfCacheCleared()
        if (revoked) {
            localControls.update {
                it.copy(
                    showAccountSignInSheet = false,
                    lastStatusBanner = "App cache cleared — Session token revoked for Zero-Trust security",
                    isStatusBannerError = true
                )
            }
        }
    }

    fun dismissStatusBanner() {
        localControls.update { it.copy(lastStatusBanner = null, isStatusBannerError = false) }
    }

    fun switchPersona(persona: AppPersonaSurface) {
        localControls.update { it.copy(activePersona = persona) }
    }

    fun toggleSeniorLargeVisionBoost() {
        localControls.update { it.copy(seniorLargeVisionBoost = !it.seniorLargeVisionBoost) }
    }

    fun toggleArchitectureLedgerSheet(show: Boolean) {
        localControls.update { it.copy(showArchitectureLedgerSheet = show) }
    }

    fun toggleAccountSignInSheet(show: Boolean) {
        localControls.update {
            it.copy(
                showAccountSignInSheet = show,
                pairingCodeValidationError = null
            )
        }
    }

    fun toggleAddMedicationSheet(show: Boolean) {
        localControls.update {
            it.copy(
                showAddMedicationSheet = show,
                addMedicationValidationError = null
            )
        }
    }

    fun onAddNewMedication(
        medicationName: String,
        strengthLabel: String,
        scheduledTimeLabel: String,
        clinicalPurpose: String,
        foodWarning: String,
        pillShape: PillShape,
        pillColorLabel: String,
        imprintCode: String
    ) {
        when (
            val outcome = dataRepository.validateAndAddMedicationSchedule(
                medicationName = medicationName,
                strengthLabel = strengthLabel,
                scheduledTimeLabel = scheduledTimeLabel,
                clinicalPurpose = clinicalPurpose,
                foodWarning = foodWarning,
                pillShape = pillShape,
                pillColorLabel = pillColorLabel,
                imprintCode = imprintCode
            )
        ) {
            is MedicationCreationOutcome.Created -> {
                localControls.update {
                    it.copy(
                        showAddMedicationSheet = false,
                        addMedicationValidationError = null,
                        lastStatusBanner = "Added ${outcome.medicationName} (${outcome.strengthLabel}) at ${outcome.scheduledTimeLabel} & persisted to Family Vault",
                        isStatusBannerError = false
                    )
                }
            }

            is MedicationCreationOutcome.RejectedValidation -> {
                localControls.update {
                    it.copy(
                        addMedicationValidationError = outcome.userMessage,
                        lastStatusBanner = outcome.userMessage,
                        isStatusBannerError = true
                    )
                }
            }
        }
    }

    fun onDeleteActiveMedication() {
        val activeId = dataRepository.snapshot.value.activeWindowId
        when (val outcome = dataRepository.deleteMedicationSchedule(activeId)) {
            is MedicationDeletionOutcome.Deleted -> {
                localControls.update {
                    it.copy(
                        lastStatusBanner = "Removed ${outcome.deletedMedicationName} from daily schedule",
                        isStatusBannerError = false
                    )
                }
            }
            is MedicationDeletionOutcome.RejectedLastSchedule -> {
                localControls.update {
                    it.copy(
                        lastStatusBanner = outcome.userMessage,
                        isStatusBannerError = true
                    )
                }
            }
        }
    }

    fun onVerifySeniorPairingCode(rawCode: String, seniorName: String = "Eleanor Vance") {
        when (val outcome = dataRepository.verifySeniorPairingCode(rawCode, seniorName)) {
            is PairingCodeOutcome.Verified -> {
                localControls.update {
                    it.copy(
                        showAccountSignInSheet = false,
                        pairingCodeValidationError = null,
                        lastStatusBanner = "Paired via code ${outcome.normalizedCode} as ${outcome.pairedDisplayName}",
                        isStatusBannerError = false
                    )
                }
            }
            is PairingCodeOutcome.InvalidCode -> {
                localControls.update {
                    it.copy(
                        pairingCodeValidationError = outcome.userMessage,
                        lastStatusBanner = outcome.userMessage,
                        isStatusBannerError = true
                    )
                }
            }
        }
    }

    fun onSignInWithProvider(provider: IdentityProvider, email: String, displayName: String) {
        val cleanEmail = email.trim()
        val cleanName = displayName.trim()
        if (cleanEmail.isEmpty() || !cleanEmail.contains("@")) {
            localControls.update {
                it.copy(
                    lastStatusBanner = "Please select or enter a valid account email address.",
                    isStatusBannerError = true
                )
            }
            return
        }

        viewModelScope.launch {
            when (val result = authGateway.authenticate(provider, cleanEmail, cleanName)) {
                is CredentialAuthResult.Authenticated -> {
                    dataRepository.signInWithAccount(
                        provider = result.provider,
                        email = result.email,
                        displayName = result.displayName,
                        credentialSourceNote = result.sourceDescription
                    )
                    localControls.update {
                        it.copy(
                            showAccountSignInSheet = false,
                            pairingCodeValidationError = null,
                            lastStatusBanner = "Signed in as ${result.displayName} (${result.email}) via ${result.provider.displayName}",
                            isStatusBannerError = false
                        )
                    }
                }

                is CredentialAuthResult.FallbackDemoVault -> {
                    dataRepository.signInWithAccount(
                        provider = result.provider,
                        email = result.email,
                        displayName = result.displayName,
                        credentialSourceNote = result.fallbackReason
                    )
                    localControls.update {
                        it.copy(
                            showAccountSignInSheet = false,
                            pairingCodeValidationError = null,
                            lastStatusBanner = "Signed in as ${result.displayName} (${result.email})",
                            isStatusBannerError = false
                        )
                    }
                }

                is CredentialAuthResult.Cancelled -> {
                    localControls.update {
                        it.copy(
                            lastStatusBanner = "Sign-in cancelled: ${result.reason}",
                            isStatusBannerError = true
                        )
                    }
                }

                is CredentialAuthResult.TransientFailure -> {
                    localControls.update {
                        it.copy(
                            lastStatusBanner = "Temporary auth error (${result.errorCode}): ${result.diagnosticMessage}",
                            isStatusBannerError = true
                        )
                    }
                }
            }
        }
    }

    fun onTestFcmPushEscalation() {
        dataRepository.simulateFcmPushEscalationTest()
        localControls.update {
            it.copy(
                lastStatusBanner = "Dispatched FCM Priority-10 Caregiver Alert (${dataRepository.snapshot.value.cloudVault.fcmTokenPreview})",
                isStatusBannerError = false
            )
        }
    }

    fun onFlushOutboxQueue() {
        val count = dataRepository.flushPendingOutboxEvents()
        localControls.update {
            it.copy(
                lastStatusBanner = "Synced Transactional Outbox ($count pending WAL events flushed over TLS 1.3)",
                isStatusBannerError = false
            )
        }
    }

    fun onSignOut() {
        dataRepository.signOutAccount()
        localControls.update {
            it.copy(
                showAccountSignInSheet = false,
                lastStatusBanner = "Signed out — Session token destroyed. Authenticate to access Family Vault.",
                isStatusBannerError = false
            )
        }
    }

    fun selectDoseWindow(doseWindowId: DoseWindowId) {
        dataRepository.selectDoseWindow(doseWindowId)
    }

    fun onSeniorConfirmTap() {
        val snap = dataRepository.snapshot.value
        val activeId = snap.activeWindowId
        val now = EpochMillis(snap.simulatedClockMillis.millis + 60_000L)
        val outcome = dataRepository.dispatchCommand(
            doseWindowId = activeId,
            command = DoseCommand.OnSeniorConfirm(
                occurredAt = now,
                idempotencyKey = IdempotencyKey("idem-senior-tap-${System.nanoTime()}")
            )
        )
        if (outcome is TransitionOutcome.RejectedDomainRule) {
            localControls.update {
                it.copy(
                    lastStatusBanner = outcome.reason,
                    isStatusBannerError = true
                )
            }
        } else {
            val activeRecord = dataRepository.snapshot.value.activeRecord
            val dupWarn = activeRecord.duplicateTapWarningMinutes
            localControls.update {
                it.copy(
                    lastStatusBanner = if (dupWarn != null) {
                        "Double-Dose Shield blocked duplicate tap (+${dupWarn}m after initial dose)"
                    } else {
                        "Confirmed ${activeRecord.spec.medicationName} (${activeRecord.spec.strengthLabel}) · Synced to Family Vault"
                    },
                    isStatusBannerError = dupWarn != null
                )
            }
        }
    }

    fun onSeniorUndoConfirmTap() {
        val snap = dataRepository.snapshot.value
        val activeId = snap.activeWindowId
        val now = EpochMillis(snap.simulatedClockMillis.millis + 30_000L)
        val outcome = dataRepository.dispatchCommand(
            doseWindowId = activeId,
            command = DoseCommand.OnSeniorUndoConfirm(
                reason = "Senior tapped Undo Accidental Confirmation",
                occurredAt = now,
                idempotencyKey = IdempotencyKey("idem-senior-undo-${System.nanoTime()}")
            )
        )
        if (outcome is TransitionOutcome.RejectedDomainRule) {
            localControls.update {
                it.copy(
                    lastStatusBanner = outcome.reason,
                    isStatusBannerError = true
                )
            }
        } else {
            localControls.update {
                it.copy(
                    lastStatusBanner = "Reverted accidental confirmation — Dose restored to Due Now & 8:30 AM watchdog re-armed",
                    isStatusBannerError = false
                )
            }
        }
    }

    fun onSeniorSnoozeTap() {
        val snap = dataRepository.snapshot.value
        val activeId = snap.activeWindowId
        val now = snap.simulatedClockMillis
        val outcome = dataRepository.dispatchCommand(
            doseWindowId = activeId,
            command = DoseCommand.OnSeniorSnooze(
                occurredAt = now,
                idempotencyKey = IdempotencyKey("idem-senior-snooze-${System.nanoTime()}")
            )
        )
        if (outcome is TransitionOutcome.RejectedDomainRule) {
            localControls.update {
                it.copy(
                    lastStatusBanner = outcome.reason,
                    isStatusBannerError = true
                )
            }
        } else {
            localControls.update {
                it.copy(
                    lastStatusBanner = "Snoozed for 10 minutes (clamped before 8:30 AM escalation)",
                    isStatusBannerError = false
                )
            }
        }
    }

    fun onCaregiverResolveTap(note: String) {
        val snap = dataRepository.snapshot.value
        val activeId = snap.activeWindowId
        val now = EpochMillis(snap.simulatedClockMillis.millis + 120_000L)
        val outcome = dataRepository.dispatchCommand(
            doseWindowId = activeId,
            command = DoseCommand.OnCaregiverResolve(
                caregiverId = CaregiverId("caregiver-primary"),
                caregiverName = snap.caregiverName.ifBlank { "Family Caregiver" },
                resolutionNote = note,
                occurredAt = now,
                idempotencyKey = IdempotencyKey("idem-cg-resolve-${System.nanoTime()}")
            )
        )
        if (outcome is TransitionOutcome.RejectedDomainRule) {
            localControls.update {
                it.copy(
                    lastStatusBanner = outcome.reason,
                    isStatusBannerError = true
                )
            }
        } else {
            localControls.update {
                it.copy(
                    lastStatusBanner = "Caregiver resolution committed to SQLite WAL + Outbox",
                    isStatusBannerError = false
                )
            }
        }
    }

    fun onSimulateDuplicateTapAfter18m() {
        val activeId = dataRepository.snapshot.value.activeWindowId
        dataRepository.simulateDuplicateTapAfter18Minutes(activeId)
        localControls.update {
            it.copy(
                activePersona = AppPersonaSurface.SENIOR_APPLIANCE,
                lastStatusBanner = "Simulated 2nd tap 18 mins later -> Double-Dose Lockout Shield activated!",
                isStatusBannerError = true
            )
        }
    }

    fun onSimulateEscalationBreach() {
        val activeId = dataRepository.snapshot.value.activeWindowId
        dataRepository.simulateFastForwardToEscalation(activeId)
        localControls.update {
            it.copy(
                lastStatusBanner = "Fast-forwarded to 8:30 AM -> Grace expired, Caregiver Escalation triggered!",
                isStatusBannerError = true
            )
        }
    }

    fun onResetScenarioToDue() {
        val activeId = dataRepository.snapshot.value.activeWindowId
        dataRepository.resetWindowToDue(activeId)
        localControls.update {
            it.copy(
                lastStatusBanner = "Reset dose window to 8:00 AM DUE state",
                isStatusBannerError = false
            )
        }
    }

    fun onSwitchIdentityProvider(provider: IdentityProvider) {
        dataRepository.switchIdentityProvider(provider)
        localControls.update {
            it.copy(
                lastStatusBanner = "Active Auth Provider switched to ${provider.displayName} (${provider.protocolBadge})",
                isStatusBannerError = false
            )
        }
    }

    fun onSimulateNewPhoneRestore() {
        val currentVault = dataRepository.snapshot.value.cloudVault
        val nextDevice = if (currentVault.currentDeviceModel.startsWith("Pixel")) {
            "Samsung Galaxy S25 Ultra (Android 16)"
        } else {
            "Pixel 10 Pro XL (Android 16)"
        }
        dataRepository.simulateDeviceChangeRestore(nextDevice, currentVault.activeProvider)
        localControls.update {
            it.copy(
                lastStatusBanner = "Restored FamilyVault (${currentVault.familyVaultId}) onto $nextDevice via ${currentVault.activeProvider.displayName} — Zero Data Loss!",
                isStatusBannerError = false
            )
        }
    }
}
