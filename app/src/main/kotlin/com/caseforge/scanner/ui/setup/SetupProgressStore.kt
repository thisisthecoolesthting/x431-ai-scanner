package com.caseforge.scanner.ui.setup

import android.content.Context
import com.caseforge.scanner.data.SettingsRepo
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Persists ordered setup/live wizard step outcomes (pass, fail, skip) alongside [SettingsRepo.setupLiveComplete].
 */
class SetupProgressStore(
    context: Context,
    private val settings: SettingsRepo,
) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    val steps: List<SetupLiveStep> = SetupLiveStep.entries

    var setupLiveComplete: Boolean
        get() = settings.setupLiveComplete
        set(value) {
            settings.setupLiveComplete = value
        }

    var tier4TrialAccepted: Boolean
        get() = settings.tier4TrialAccepted
        set(value) {
            if (!value) {
                settings.clearTier4Trial()
            }
        }

    fun stepState(step: SetupLiveStep): SetupStepState {
        val snap = loadSnapshot()
        val rec = snap.steps[step.id] ?: return SetupStepState(SetupStepStatus.PENDING, "")
        return SetupStepState(
            status = SetupStepStatus.valueOf(rec.status),
            detail = rec.detail,
            skipReason = rec.skipReason,
        )
    }

    fun setStepState(step: SetupLiveStep, state: SetupStepState) {
        val snap = loadSnapshot()
        val updated = snap.steps.toMutableMap()
        updated[step.id] = StepRecord(
            status = state.status.name,
            detail = state.detail,
            skipReason = state.skipReason,
        )
        saveSnapshot(snap.copy(steps = updated))
    }

    fun passedOrSkippedCount(): Int =
        steps.count { step ->
            when (stepState(step).status) {
                SetupStepStatus.PASSED, SetupStepStatus.SKIPPED -> true
                else -> false
            }
        }

    fun allStepsResolved(): Boolean =
        steps.all { stepState(it).status in RESOLVED_STATUSES }

    fun resetAll() {
        prefs.edit()
            .remove(K_SNAPSHOT)
            .apply()
        setupLiveComplete = false
        settings.clearTier4Trial()
    }

    private fun loadSnapshot(): SetupProgressSnapshot {
        val raw = prefs.getString(K_SNAPSHOT, null) ?: return SetupProgressSnapshot()
        return runCatching { json.decodeFromString<SetupProgressSnapshot>(raw) }.getOrDefault(SetupProgressSnapshot())
    }

    private fun saveSnapshot(snap: SetupProgressSnapshot) {
        prefs.edit().putString(K_SNAPSHOT, json.encodeToString(snap)).apply()
    }

    @Serializable
    private data class SetupProgressSnapshot(
        val steps: Map<String, StepRecord> = emptyMap(),
    )

    @Serializable
    private data class StepRecord(
        val status: String,
        val detail: String,
        val skipReason: String? = null,
    )

    companion object {
        private const val PREFS_NAME = "setup_live_progress"
        private const val K_SNAPSHOT = "snapshot_json"

        private val RESOLVED_STATUSES = setOf(
            SetupStepStatus.PASSED,
            SetupStepStatus.SKIPPED,
            SetupStepStatus.FAILED,
        )

        private val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    }
}

enum class SetupLiveStep(
    val id: String,
    val title: String,
    val summary: String,
) {
    APP_HEALTH(
        "app_health",
        "App health",
        "Encrypted preferences and no crash on last run",
    ),
    SHOP_DESK(
        "shop_desk",
        "Shop Desk",
        "GET health from Shop Desk ingest base URL",
    ),
    LINK_TRANSPORT(
        "link_transport",
        "USB / link transport",
        "Non-destructive ELM327 or OEM USB probe",
    ),
    CAMERA(
        "camera",
        "Camera",
        "Launch a test capture (session camera flow)",
    ),
    SESSION_BOOTSTRAP(
        "session_bootstrap",
        "Session bootstrap",
        "Background session link probe",
    ),
    HARVEST_PATH(
        "harvest_path",
        "Harvest path",
        "Dry-run harvest manifest (zip path check)",
    ),
    TIER4_TRIAL(
        "tier4_trial",
        "Tier 4 trial gate",
        "Acknowledge programming trial before enabling Tier 4",
    ),
    MARK_COMPLETE(
        "mark_complete",
        "All systems live",
        "Mark setup complete and open compact home",
    ),
}

enum class SetupStepStatus {
    PENDING,
    RUNNING,
    PASSED,
    FAILED,
    SKIPPED,
}

data class SetupStepState(
    val status: SetupStepStatus,
    val detail: String,
    val skipReason: String? = null,
)
