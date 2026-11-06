package com.kindose.app.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kindose.app.data.DataRepository
import com.kindose.app.data.DefaultDataRepository
import com.kindose.app.data.RepositorySnapshot
import com.kindose.app.domain.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

enum class AppPersonaSurface(val title: String, val subtitle: String) {
    SENIOR_APPLIANCE(
        title = "Senior Appliance",
        subtitle = "Mom's Zero-Login Screen"
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
    val lastStatusBanner: String? = null
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
    private val dataRepository: DataRepository = DefaultDataRepository()
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
        localControls.update { it.copy(showAccountSignInSheet = show) }
    }

    fun onSignInWithProvider(provider: IdentityProvider, email: String, displayName: String) {
        dataRepository.signInWithAccount(provider, email, displayName)
        localControls.update {
            it.copy(
                showAccountSignInSheet = false,
                lastStatusBanner = "Signed in as $displayName ($email) via ${provider.displayName}"
            )
        }
    }

    fun onSignOut() {
        dataRepository.signOutAccount()
        localControls.update {
            it.copy(
                showAccountSignInSheet = true,
                lastStatusBanner = "Signed out — Choose Google, Passkey, Facebook, Apple, or QR Pairing to sign in"
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
            localControls.update { it.copy(lastStatusBanner = outcome.reason) }
        } else {
            localControls.update { it.copy(lastStatusBanner = null) }
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
            localControls.update { it.copy(lastStatusBanner = outcome.reason) }
        } else {
            localControls.update { it.copy(lastStatusBanner = "Snoozed for 10 minutes (clamped before 8:30 AM escalation)") }
        }
    }

    fun onCaregiverResolveTap(note: String) {
        val snap = dataRepository.snapshot.value
        val activeId = snap.activeWindowId
        val now = EpochMillis(snap.simulatedClockMillis.millis + 120_000L)
        dataRepository.dispatchCommand(
            doseWindowId = activeId,
            command = DoseCommand.OnCaregiverResolve(
                caregiverId = CaregiverId("caregiver-maya-vance"),
                caregiverName = snap.caregiverName,
                resolutionNote = note.ifBlank { "Called Mom on phone — confirmed she took her pill with breakfast" },
                occurredAt = now,
                idempotencyKey = IdempotencyKey("idem-cg-resolve-${System.nanoTime()}")
            )
        )
        localControls.update {
            it.copy(lastStatusBanner = "Caregiver resolution committed to SQLite WAL + Outbox")
        }
    }

    fun onSimulateDuplicateTapAfter18m() {
        val activeId = dataRepository.snapshot.value.activeWindowId
        dataRepository.simulateDuplicateTapAfter18Minutes(activeId)
        localControls.update {
            it.copy(
                activePersona = AppPersonaSurface.SENIOR_APPLIANCE,
                lastStatusBanner = "Simulated 2nd tap 18 mins later -> Double-Dose Lockout Shield activated!"
            )
        }
    }

    fun onSimulateEscalationBreach() {
        val activeId = dataRepository.snapshot.value.activeWindowId
        dataRepository.simulateFastForwardToEscalation(activeId)
        localControls.update {
            it.copy(
                lastStatusBanner = "Fast-forwarded to 8:30 AM -> Grace expired, Caregiver Escalation triggered!"
            )
        }
    }

    fun onResetScenarioToDue() {
        val activeId = dataRepository.snapshot.value.activeWindowId
        dataRepository.resetWindowToDue(activeId)
        localControls.update {
            it.copy(lastStatusBanner = "Reset dose window to 8:00 AM DUE state")
        }
    }

    fun onSwitchIdentityProvider(provider: IdentityProvider) {
        dataRepository.switchIdentityProvider(provider)
        localControls.update {
            it.copy(lastStatusBanner = "Active Auth Provider switched to ${provider.displayName} (${provider.protocolBadge})")
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
                lastStatusBanner = "Restored FamilyVault (${currentVault.familyVaultId}) onto $nextDevice via ${currentVault.activeProvider.displayName} — Zero Data Loss!"
            )
        }
    }
}
