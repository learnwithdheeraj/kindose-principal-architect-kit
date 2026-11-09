package com.kindose.app.ui.main

import com.kindose.app.data.DefaultDataRepository
import com.kindose.app.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MainScreenViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialState_loadsMorningLisinoprilInDueState() = runTest {
        val repository = DefaultDataRepository()
        val viewModel = MainScreenViewModel(repository)
        backgroundScope.launch(testDispatcher) { viewModel.uiState.collect() }

        val state = viewModel.uiState.value as MainScreenUiState.Success
        val active = state.snapshot.activeRecord

        assertEquals("Lisinopril", active.spec.medicationName)
        assertTrue(active.state is DoseState.Due)
    }

    @Test
    fun doubleDosePreventionShield_blocksDuplicateTapAndPreservesOriginalTimestamp() = runTest {
        val repository = DefaultDataRepository()
        val viewModel = MainScreenViewModel(repository)
        backgroundScope.launch(testDispatcher) { viewModel.uiState.collect() }

        // First tap confirms the dose
        viewModel.onSeniorConfirmTap()
        val afterFirstTap = (viewModel.uiState.value as MainScreenUiState.Success).snapshot.activeRecord
        assertTrue(afterFirstTap.state is DoseState.Confirmed)
        val originalConfirmedAt = (afterFirstTap.state as DoseState.Confirmed).confirmedAt

        // Second tap 18 minutes later triggers Double-Dose Lockout Shield without mutating confirmedAt
        viewModel.onSimulateDuplicateTapAfter18m()
        val afterSecondTap = (viewModel.uiState.value as MainScreenUiState.Success).snapshot.activeRecord
        assertTrue(afterSecondTap.state is DoseState.Confirmed)
        assertEquals(originalConfirmedAt, (afterSecondTap.state as DoseState.Confirmed).confirmedAt)
        assertEquals(18L, afterSecondTap.duplicateTapWarningMinutes)
    }

    @Test
    fun boundedSnooze_clampsToEscalationDeadlineAndCapsAtMaxSnoozes() = runTest {
        val repository = DefaultDataRepository()
        val windowId = DefaultDataRepository.MORNING_WINDOW_ID
        val spec = repository.snapshot.value.activeRecord.spec

        // Snooze 1 at 08:05 -> wakes at 08:15
        val out1 = repository.dispatchCommand(
            windowId,
            DoseCommand.OnSeniorSnooze(
                occurredAt = spec.dueAt + (5 * 60_000L),
                idempotencyKey = IdempotencyKey("idem-snooze-1")
            )
        )
        assertTrue(out1 is TransitionOutcome.Applied)

        // Snooze 2 at 08:25 -> 08:25 + 10m = 08:35, clamped to 08:30 escalationDeadline!
        val out2 = repository.dispatchCommand(
            windowId,
            DoseCommand.OnSeniorSnooze(
                occurredAt = spec.dueAt + (25 * 60_000L),
                idempotencyKey = IdempotencyKey("idem-snooze-2")
            )
        )
        assertTrue(out2 is TransitionOutcome.Applied)
        val snoozed2 = (out2 as TransitionOutcome.Applied).newState as DoseState.Snoozed
        assertEquals(spec.escalationDeadline, snoozed2.snoozeUntil)

        // Snooze 3 is rejected by SNOOZE_LIMIT_REACHED
        val out3 = repository.dispatchCommand(
            windowId,
            DoseCommand.OnSeniorSnooze(
                occurredAt = spec.dueAt + (26 * 60_000L),
                idempotencyKey = IdempotencyKey("idem-snooze-3")
            )
        )
        assertTrue(out3 is TransitionOutcome.RejectedDomainRule)
        assertEquals(RejectionCode.SNOOZE_LIMIT_REACHED, (out3 as TransitionOutcome.RejectedDomainRule).code)
    }

    @Test
    fun escalationThenLateSeniorConfirm_emitsCaregiverAllClearOutboxEvent() = runTest {
        val repository = DefaultDataRepository()
        val viewModel = MainScreenViewModel(repository)
        backgroundScope.launch(testDispatcher) { viewModel.uiState.collect() }

        viewModel.onSimulateEscalationBreach()
        val escalatedSnap = (viewModel.uiState.value as MainScreenUiState.Success).snapshot
        assertTrue(escalatedSnap.activeRecord.state is DoseState.Escalated)

        // Senior taps confirm after escalation -> transitions to Confirmed and emits CAREGIVER_ALL_CLEAR_LATE_CONFIRM
        viewModel.onSeniorConfirmTap()
        val resolvedSnap = (viewModel.uiState.value as MainScreenUiState.Success).snapshot
        assertTrue(resolvedSnap.activeRecord.state is DoseState.Confirmed)
        assertTrue(
            resolvedSnap.outboxQueue.any { it.eventType == OutboxEventType.CAREGIVER_ALL_CLEAR_LATE_CONFIRM }
        )
    }

    @Test
    fun deviceMigrationRestore_preservesSchedulesAndStreakAcrossNewDevice() = runTest {
        val repository = DefaultDataRepository()
        val viewModel = MainScreenViewModel(repository)
        backgroundScope.launch(testDispatcher) { viewModel.uiState.collect() }

        viewModel.onSwitchIdentityProvider(IdentityProvider.PASSKEY_FIDO2)
        viewModel.onSimulateNewPhoneRestore()

        val snap = (viewModel.uiState.value as MainScreenUiState.Success).snapshot
        assertEquals(IdentityProvider.PASSKEY_FIDO2, snap.cloudVault.activeProvider)
        assertEquals("Samsung Galaxy S25 Ultra (Android 16)", snap.cloudVault.currentDeviceModel)
        assertEquals(3, snap.windows.size)
        assertEquals(14, snap.adherenceStreakDays)
    }

    @Test
    fun addNewMedication_insertsAndSelectsNewScheduleWindow() = runTest {
        val repository = DefaultDataRepository()
        val viewModel = MainScreenViewModel(repository)
        backgroundScope.launch(testDispatcher) { viewModel.uiState.collect() }

        viewModel.onAddNewMedication(
            medicationName = "Metformin",
            strengthLabel = "500 mg",
            scheduledTimeLabel = "9:00 AM",
            clinicalPurpose = "Blood Sugar Control",
            foodWarning = "Take with breakfast",
            pillShape = PillShape.ROUND_TABLET,
            pillColorLabel = "White Round Tablet",
            imprintCode = "GL 500"
        )

        val snap = (viewModel.uiState.value as MainScreenUiState.Success).snapshot
        assertEquals(4, snap.windows.size)
        assertEquals("Metformin", snap.activeRecord.spec.medicationName)
        assertEquals("500 mg", snap.activeRecord.spec.strengthLabel)
        assertTrue(snap.activeRecord.state is DoseState.Due)
    }

    @Test
    fun zeroTrustColdStart_startsSignedOutWithNoPreselectedAccount() = runTest {
        val repository = DefaultDataRepository(startSignedIn = false)
        val viewModel = MainScreenViewModel(repository)
        backgroundScope.launch(testDispatcher) { viewModel.uiState.collect() }

        val snap = (viewModel.uiState.value as MainScreenUiState.Success).snapshot
        assertEquals(false, snap.cloudVault.isSignedIn)
        assertEquals("", snap.cloudVault.primaryEmail)
        assertEquals("", snap.cloudVault.accountDisplayName)
        assertTrue(snap.cloudVault.linkedProviders.isEmpty())
    }

    @Test
    fun seniorPairingCode_rejectsInvalidShortCodeAndAcceptsValid6DigitCode() = runTest {
        val repository = DefaultDataRepository(startSignedIn = false)
        val viewModel = MainScreenViewModel(repository)
        backgroundScope.launch(testDispatcher) { viewModel.uiState.collect() }

        // Wrong path: 2 digits
        viewModel.onVerifySeniorPairingCode("12", "Eleanor Vance")
        val afterInvalid = viewModel.uiState.value as MainScreenUiState.Success
        assertEquals(false, afterInvalid.snapshot.cloudVault.isSignedIn)
        assertTrue(afterInvalid.controls.pairingCodeValidationError?.contains("6 digits") == true)

        // Happy path: 6 digits ("849-204")
        viewModel.onVerifySeniorPairingCode("849-204", "Eleanor Vance")
        val afterValid = viewModel.uiState.value as MainScreenUiState.Success
        assertEquals(true, afterValid.snapshot.cloudVault.isSignedIn)
        assertEquals(IdentityProvider.SENIOR_QR_PAIRING, afterValid.snapshot.cloudVault.activeProvider)
    }

    @Test
    fun addMedicationValidation_rejectsBlankNameAndDuplicateScheduleConflict() = runTest {
        val repository = DefaultDataRepository()
        val viewModel = MainScreenViewModel(repository)
        backgroundScope.launch(testDispatcher) { viewModel.uiState.collect() }

        // Wrong path 1: Blank medication name
        viewModel.onAddNewMedication(
            medicationName = " ",
            strengthLabel = "10 mg",
            scheduledTimeLabel = "9:00 AM",
            clinicalPurpose = "Test",
            foodWarning = "Water",
            pillShape = PillShape.ROUND_TABLET,
            pillColorLabel = "White",
            imprintCode = "T1"
        )
        val afterBlank = viewModel.uiState.value as MainScreenUiState.Success
        assertEquals(3, afterBlank.snapshot.windows.size)
        assertTrue(afterBlank.controls.addMedicationValidationError?.contains("required") == true)

        // Wrong path 2: Duplicate Lisinopril at 8:00 AM
        viewModel.onAddNewMedication(
            medicationName = "Lisinopril",
            strengthLabel = "10 mg",
            scheduledTimeLabel = "8:00 AM",
            clinicalPurpose = "Blood Pressure",
            foodWarning = "Water",
            pillShape = PillShape.ROUND_TABLET,
            pillColorLabel = "Pink",
            imprintCode = "LUPIN 10"
        )
        val afterDuplicate = viewModel.uiState.value as MainScreenUiState.Success
        assertEquals(3, afterDuplicate.snapshot.windows.size)
        assertTrue(afterDuplicate.controls.addMedicationValidationError?.contains("already scheduled") == true)
    }

    @Test
    fun seniorUndoAccidentalConfirm_revertsToDueAndEmitsOutboxEvent() = runTest {
        val repository = DefaultDataRepository()
        val viewModel = MainScreenViewModel(repository)
        backgroundScope.launch(testDispatcher) { viewModel.uiState.collect() }

        viewModel.onSeniorConfirmTap()
        assertTrue((viewModel.uiState.value as MainScreenUiState.Success).snapshot.activeRecord.state is DoseState.Confirmed)

        viewModel.onSeniorUndoConfirmTap()
        val afterUndo = (viewModel.uiState.value as MainScreenUiState.Success).snapshot
        assertTrue(afterUndo.activeRecord.state is DoseState.Due)
        assertTrue(afterUndo.outboxQueue.any { it.eventType == OutboxEventType.DOSE_CONFIRMATION_UNDONE })
    }

    @Test
    fun caregiverResolve_rejectsBlankResolutionNote() = runTest {
        val repository = DefaultDataRepository()
        val viewModel = MainScreenViewModel(repository)
        backgroundScope.launch(testDispatcher) { viewModel.uiState.collect() }

        viewModel.onSimulateEscalationBreach()
        viewModel.onCaregiverResolveTap("   ")
        val stateAfterBlank = (viewModel.uiState.value as MainScreenUiState.Success)
        assertTrue(stateAfterBlank.snapshot.activeRecord.state is DoseState.Escalated)
        assertTrue(stateAfterBlank.controls.isStatusBannerError)
    }
}
