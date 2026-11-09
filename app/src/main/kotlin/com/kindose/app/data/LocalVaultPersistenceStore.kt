package com.kindose.app.data

import android.content.Context
import com.kindose.app.domain.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class PersistedVaultState(
    val isSignedIn: Boolean,
    val provider: IdentityProvider,
    val email: String,
    val displayName: String,
    val patientName: String,
    val caregiverName: String,
    val credentialSourceNote: String,
    val activeWindowId: DoseWindowId?,
    val windows: List<DoseWindowRecord>?
)

/**
 * Durable Local Persistence & Zero-Trust Cache Sentinel (`L8`/`IC8` Security Boundary).
 *
 * Guarantees:
 * 1. Fresh Install, "Clear Storage", OR "Clear Cache" in Android OS Settings destroys the
 *    ephemeral session nonce in `context.cacheDir`, immediately revoking any cached session
 *    and forcing a clean Zero-State Login Gate with zero pre-selected accounts.
 * 2. Normal app restarts while authenticated preserve both the signed-in identity AND all
 *    added/deleted medications and dose confirmation states.
 */
class LocalVaultPersistenceStore(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val cacheSentinelFile = File(appContext.cacheDir, CACHE_SENTINEL_FILENAME)

    /**
     * Verifies that the cache-directory session nonce still matches SharedPreferences.
     * If the user cleared the app cache in Android Settings while the app was backgrounded
     * or before relaunching, the sentinel file is missing and the session is immediately revoked.
     */
    fun isCacheSessionValid(): Boolean {
        val signedIn = prefs.getBoolean(KEY_IS_SIGNED_IN, false)
        if (!signedIn) return false
        val expectedNonce = prefs.getString(KEY_SESSION_NONCE, null)
        if (expectedNonce.isNullOrBlank()) return false
        if (!cacheSentinelFile.exists()) return false
        val actualNonce = runCatching { cacheSentinelFile.readText().trim() }.getOrNull()
        return !actualNonce.isNullOrBlank() && actualNonce == expectedNonce
    }

    fun loadPersistedState(): PersistedVaultState? {
        if (!isCacheSessionValid()) {
            // Cache or storage was cleared, or user has never signed in -> enforce Zero-Trust sign-out
            clearAuthSessionOnly()
            return null
        }

        val email = prefs.getString(KEY_EMAIL, "")?.trim().orEmpty()
        val displayName = prefs.getString(KEY_DISPLAY_NAME, "")?.trim().orEmpty()
        if (email.isEmpty() || displayName.isEmpty()) {
            clearAuthSessionOnly()
            return null
        }

        val providerName = prefs.getString(KEY_PROVIDER, IdentityProvider.GOOGLE_ONE_TAP.name)
        val provider = runCatching {
            IdentityProvider.valueOf(providerName ?: IdentityProvider.GOOGLE_ONE_TAP.name)
        }.getOrDefault(IdentityProvider.GOOGLE_ONE_TAP)

        val patientName = prefs.getString(KEY_PATIENT_NAME, displayName).orEmpty().ifBlank { displayName }
        val caregiverName = prefs.getString(KEY_CAREGIVER_NAME, displayName).orEmpty().ifBlank { displayName }
        val sourceNote = prefs.getString(KEY_CREDENTIAL_NOTE, "${provider.displayName} · ${provider.protocolBadge}")
            .orEmpty()
        val activeIdRaw = prefs.getString(KEY_ACTIVE_WINDOW_ID, null)
        val activeWindowId = activeIdRaw?.takeIf { it.isNotBlank() }?.let { DoseWindowId(it) }

        val windowsJson = prefs.getString(KEY_WINDOWS_JSON, null)
        val hydratedWindows = windowsJson?.let { parseWindowsJson(it) }?.takeIf { it.isNotEmpty() }

        return PersistedVaultState(
            isSignedIn = true,
            provider = provider,
            email = email,
            displayName = displayName,
            patientName = patientName,
            caregiverName = caregiverName,
            credentialSourceNote = sourceNote,
            activeWindowId = activeWindowId,
            windows = hydratedWindows
        )
    }

    fun saveSnapshot(snapshot: RepositorySnapshot) {
        val vault = snapshot.cloudVault
        if (!vault.isSignedIn || vault.primaryEmail.isBlank()) {
            clearAuthSessionOnly()
            return
        }

        val existingNonce = prefs.getString(KEY_SESSION_NONCE, null)
            ?.takeIf { it.isNotBlank() && cacheSentinelFile.exists() }
            ?: UUID.randomUUID().toString()

        runCatching {
            cacheSentinelFile.parentFile?.mkdirs()
            cacheSentinelFile.writeText(existingNonce)
        }

        val windowsJson = serializeWindowsJson(snapshot.windows)

        prefs.edit()
            .putBoolean(KEY_IS_SIGNED_IN, true)
            .putString(KEY_SESSION_NONCE, existingNonce)
            .putString(KEY_PROVIDER, vault.activeProvider.name)
            .putString(KEY_EMAIL, vault.primaryEmail)
            .putString(KEY_DISPLAY_NAME, vault.accountDisplayName)
            .putString(KEY_PATIENT_NAME, snapshot.patientName)
            .putString(KEY_CAREGIVER_NAME, snapshot.caregiverName)
            .putString(KEY_CREDENTIAL_NOTE, vault.credentialSourceNote)
            .putString(KEY_ACTIVE_WINDOW_ID, snapshot.activeWindowId.value)
            .putString(KEY_WINDOWS_JSON, windowsJson)
            .apply()
    }

    fun clearAuthSessionOnly() {
        runCatching {
            if (cacheSentinelFile.exists()) {
                cacheSentinelFile.delete()
            }
        }
        prefs.edit()
            .putBoolean(KEY_IS_SIGNED_IN, false)
            .remove(KEY_SESSION_NONCE)
            .remove(KEY_EMAIL)
            .remove(KEY_DISPLAY_NAME)
            .apply()
    }

    private fun serializeWindowsJson(windows: List<DoseWindowRecord>): String {
        val array = JSONArray()
        windows.forEach { record ->
            val spec = record.spec
            val obj = JSONObject().apply {
                put("doseWindowId", spec.doseWindowId.value)
                put("scheduleId", spec.scheduleId.value)
                put("patientId", spec.patientId.value)
                put("scheduledTimeLabel", spec.scheduledTimeLabel)
                put("medicationName", spec.medicationName)
                put("strengthLabel", spec.strengthLabel)
                put("clinicalPurpose", spec.clinicalPurpose)
                put("dosageInstructions", spec.dosageInstructions)
                put("foodWarning", spec.foodWarning)
                put("pillShape", spec.pillShape.name)
                put("pillColorLabel", spec.pillColorLabel)
                put("imprintCode", spec.imprintCode)
                put("windowStart", spec.windowStart.millis)
                put("dueAt", spec.dueAt.millis)
                put("escalationDeadline", spec.escalationDeadline.millis)
                put("windowEnd", spec.windowEnd.millis)
                put("maxSnoozes", spec.maxSnoozes)
                put("duplicateTapWarningMinutes", record.duplicateTapWarningMinutes ?: -1L)

                val state = record.state
                put("stateType", state.stateName)
                when (state) {
                    is DoseState.Scheduled -> Unit
                    is DoseState.Due -> put("notifiedAt", state.notifiedAt.millis)
                    is DoseState.Snoozed -> {
                        put("snoozeCount", state.snoozeCount)
                        put("snoozeUntil", state.snoozeUntil.millis)
                    }
                    is DoseState.Confirmed -> {
                        put("confirmedAt", state.confirmedAt.millis)
                        put("idempotencyKey", state.idempotencyKey.value)
                        put("source", state.source.name)
                    }
                    is DoseState.Escalated -> {
                        put("escalatedAt", state.escalatedAt.millis)
                        put("snoozeCountPrior", state.snoozeCountPrior)
                    }
                    is DoseState.ResolvedByCaregiver -> {
                        put("resolvedAt", state.resolvedAt.millis)
                        put("caregiverId", state.caregiverId.value)
                        put("caregiverName", state.caregiverName)
                        put("resolutionNote", state.resolutionNote)
                        put("idempotencyKey", state.idempotencyKey.value)
                    }
                    is DoseState.MissedExpired -> {
                        put("expiredAt", state.expiredAt.millis)
                    }
                }
            }
            array.put(obj)
        }
        return array.toString()
    }

    private fun parseWindowsJson(rawJson: String): List<DoseWindowRecord> = runCatching {
        val array = JSONArray(rawJson)
        val result = mutableListOf<DoseWindowRecord>()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            val dueAtMs = obj.optLong("dueAt", DefaultDataRepository.BASE_0800_EPOCH_MS)
            val spec = DoseScheduleSpec(
                doseWindowId = DoseWindowId(obj.getString("doseWindowId")),
                scheduleId = ScheduleId(obj.getString("scheduleId")),
                patientId = PatientId(obj.optString("patientId", "patient-eleanor-vance")),
                scheduledTimeLabel = obj.getString("scheduledTimeLabel"),
                medicationName = obj.getString("medicationName"),
                strengthLabel = obj.getString("strengthLabel"),
                clinicalPurpose = obj.optString("clinicalPurpose", "Daily Prescribed Therapy"),
                dosageInstructions = obj.optString("dosageInstructions", "Take 1 dose by mouth with water"),
                foodWarning = obj.optString("foodWarning", "Take with water"),
                pillShape = runCatching {
                    PillShape.valueOf(obj.optString("pillShape", PillShape.ROUND_TABLET.name))
                }.getOrDefault(PillShape.ROUND_TABLET),
                pillColorLabel = obj.optString("pillColorLabel", "White Round Tablet"),
                imprintCode = obj.optString("imprintCode", "RX 100"),
                windowStart = EpochMillis(obj.optLong("windowStart", dueAtMs - 15 * 60_000L)),
                dueAt = EpochMillis(dueAtMs),
                escalationDeadline = EpochMillis(obj.optLong("escalationDeadline", dueAtMs + 30 * 60_000L)),
                windowEnd = EpochMillis(obj.optLong("windowEnd", dueAtMs + 120 * 60_000L)),
                maxSnoozes = obj.optInt("maxSnoozes", 2)
            )

            val stateType = obj.optString("stateType", "DUE")
            val state: DoseState = when (stateType) {
                "SCHEDULED" -> DoseState.Scheduled
                "SNOOZED" -> DoseState.Snoozed(
                    snoozeCount = obj.optInt("snoozeCount", 1).coerceAtLeast(1),
                    snoozeUntil = EpochMillis(obj.optLong("snoozeUntil", dueAtMs + 10 * 60_000L))
                )
                "CONFIRMED" -> DoseState.Confirmed(
                    confirmedAt = EpochMillis(obj.optLong("confirmedAt", dueAtMs + 2 * 60_000L)),
                    idempotencyKey = IdempotencyKey(obj.optString("idempotencyKey", "idem-hydrated-$i")),
                    source = runCatching {
                        ConfirmationSource.valueOf(
                            obj.optString("source", ConfirmationSource.SENIOR_APPLIANCE_TAP.name)
                        )
                    }.getOrDefault(ConfirmationSource.SENIOR_APPLIANCE_TAP)
                )
                "ESCALATED" -> DoseState.Escalated(
                    escalatedAt = EpochMillis(obj.optLong("escalatedAt", dueAtMs + 30 * 60_000L)),
                    snoozeCountPrior = obj.optInt("snoozeCountPrior", 0)
                )
                "RESOLVED_BY_CAREGIVER" -> DoseState.ResolvedByCaregiver(
                    resolvedAt = EpochMillis(obj.optLong("resolvedAt", dueAtMs + 35 * 60_000L)),
                    caregiverId = CaregiverId(obj.optString("caregiverId", "caregiver-primary")),
                    caregiverName = obj.optString("caregiverName", "Family Caregiver"),
                    resolutionNote = obj.optString("resolutionNote", "Verified safe by phone").ifBlank { "Verified safe" },
                    idempotencyKey = IdempotencyKey(obj.optString("idempotencyKey", "idem-cg-hydrated-$i"))
                )
                "MISSED_EXPIRED" -> DoseState.MissedExpired(
                    expiredAt = EpochMillis(obj.optLong("expiredAt", dueAtMs + 120 * 60_000L))
                )
                else -> DoseState.Due(notifiedAt = EpochMillis(obj.optLong("notifiedAt", dueAtMs)))
            }

            val dupWarn = obj.optLong("duplicateTapWarningMinutes", -1L).takeIf { it >= 0L }
            result.add(
                DoseWindowRecord(
                    spec = spec,
                    state = state,
                    duplicateTapWarningMinutes = dupWarn
                )
            )
        }
        result
    }.getOrDefault(emptyList())

    companion object {
        private const val PREFS_NAME = "kindose_vault_store_v2"
        private const val CACHE_SENTINEL_FILENAME = "kindose_active_session.nonce"
        private const val KEY_IS_SIGNED_IN = "is_signed_in"
        private const val KEY_SESSION_NONCE = "session_nonce"
        private const val KEY_PROVIDER = "active_provider"
        private const val KEY_EMAIL = "primary_email"
        private const val KEY_DISPLAY_NAME = "account_display_name"
        private const val KEY_PATIENT_NAME = "patient_name"
        private const val KEY_CAREGIVER_NAME = "caregiver_name"
        private const val KEY_CREDENTIAL_NOTE = "credential_source_note"
        private const val KEY_ACTIVE_WINDOW_ID = "active_window_id"
        private const val KEY_WINDOWS_JSON = "windows_json"
    }
}
