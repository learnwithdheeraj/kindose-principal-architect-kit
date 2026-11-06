package com.kindose.app.fcm

import com.kindose.app.auth.AndroidJetpackCredentialGateway
import com.kindose.app.auth.CredentialAuthResult
import com.kindose.app.data.DefaultDataRepository
import com.kindose.app.domain.IdentityProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FcmEscalationAndAuthTest {

    @Test
    fun `parses valid high priority caregiver escalation FCM payload and deduplicates retries`() {
        val deduplicator = BoundedFcmDeduplicator(maxCapacity = 32)
        val rawData = mapOf(
            "idempotency_key" to "fcm-esc-20261107-0830",
            "schema_version" to "1",
            "sent_epoch_ms" to "1762417800000",
            "event_type" to "CAREGIVER_ESCALATION_SLA_BREACH",
            "patient_id" to "patient-eleanor-01",
            "patient_name" to "Eleanor Vance",
            "window_id" to "dose-2026-11-06-0800-lisinopril",
            "medication_name" to "Lisinopril",
            "strength_label" to "10 mg",
            "scheduled_time_label" to "8:00 AM",
            "overdue_minutes" to "30"
        )

        val firstOutcome = FcmPayloadParser.parseAndDeduplicate(
            rawData = rawData,
            deduplicator = deduplicator,
            nowEpochMs = 1_762_417_800_100L
        )
        assertTrue(firstOutcome is FcmParseOutcome.Valid)
        val payload = (firstOutcome as FcmParseOutcome.Valid).payload
        assertTrue(payload is FcmInboundPayload.CaregiverEscalationPush)
        val escalation = payload as FcmInboundPayload.CaregiverEscalationPush
        assertEquals("Lisinopril", escalation.medicationName)
        assertEquals(30, escalation.overdueMinutes)

        // Duplicate delivery of the same idempotency_key must be rejected idempotently
        val duplicateOutcome = FcmPayloadParser.parseAndDeduplicate(
            rawData = rawData,
            deduplicator = deduplicator,
            nowEpochMs = 1_762_417_805_000L
        )
        assertTrue(duplicateOutcome is FcmParseOutcome.DuplicateIgnored)
    }

    @Test
    fun `handles forward compatible schema v2 payloads without crashing`() {
        val deduplicator = BoundedFcmDeduplicator(maxCapacity = 32)
        val futureData = mapOf(
            "idempotency_key" to "fcm-future-v2-key-001",
            "schema_version" to "2",
            "event_type" to "BIOMETRIC_WEARABLE_HEART_RATE_SYNC"
        )

        val outcome = FcmPayloadParser.parseAndDeduplicate(
            rawData = futureData,
            deduplicator = deduplicator,
            nowEpochMs = 1_762_417_900_000L
        )
        assertTrue(outcome is FcmParseOutcome.Valid)
        val payload = (outcome as FcmParseOutcome.Valid).payload
        assertTrue(payload is FcmInboundPayload.UnknownForwardCompatiblePush)
        assertEquals(2, payload.schemaVersion)
    }

    @Test
    fun `bounded deduplicator strictly enforces maximum capacity`() {
        val deduplicator = BoundedFcmDeduplicator(maxCapacity = 16)
        for (i in 1..40) {
            val raw = mapOf(
                "idempotency_key" to "idem-bounded-key-$i",
                "event_type" to "DOSE_CONFIRMED_SYNC"
            )
            FcmPayloadParser.parseAndDeduplicate(raw, deduplicator, 1000L + i)
        }
        assertEquals(16, deduplicator.size())
    }

    @Test
    fun `credential gateway deterministically falls back in hermetic JVM environment`() = runTest {
        val gateway = AndroidJetpackCredentialGateway(appContext = null)
        val result = gateway.authenticate(
            provider = IdentityProvider.GOOGLE_ONE_TAP,
            fallbackEmail = "maya.vance@gmail.com",
            fallbackDisplayName = "Maya Vance"
        )
        assertTrue(result is CredentialAuthResult.FallbackDemoVault)
        val fallback = result as CredentialAuthResult.FallbackDemoVault
        assertEquals("maya.vance@gmail.com", fallback.email)
        assertEquals(IdentityProvider.GOOGLE_ONE_TAP, fallback.provider)
    }

    @Test
    fun `repository escalation increments FCM push telemetry counter`() {
        val repository = DefaultDataRepository()
        val initialCount = repository.snapshot.value.cloudVault.fcmPushDeliveredCount
        repository.simulateFastForwardToEscalation(DefaultDataRepository.MORNING_WINDOW_ID)
        val updatedVault = repository.snapshot.value.cloudVault
        assertEquals(initialCount + 1, updatedVault.fcmPushDeliveredCount)
        assertTrue(updatedVault.lastFcmPushSummary.contains("CRITICAL FCM PUSH"))
    }
}
