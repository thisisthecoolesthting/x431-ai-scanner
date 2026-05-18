package com.caseforge.scanner.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.caseforge.scanner.BuildConfig
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Stores secrets (Claude API key) in EncryptedSharedPreferences, and non-secret prefs
 * (model choice, kill switch state, autonomous mode toggle) in plain SharedPreferences.
 *
 * Merged from:
 * - A6: overlayOnX431, overlayOnX431Flow, setOverlayOnX431
 * - C2: overlayOnboardingSeen, overlayOnboardingSeenFlow, setOverlayOnboardingSeen
 * - D1: emergencyDismissHintSeen (property only, no Flow)
 *
 * All three overlay properties follow identical structural pattern: property getter/setter +
 * optional Flow-backed reactive view + optional suspend writer.
 */
class SettingsRepo(context: Context) {
    private val master = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val secure = EncryptedSharedPreferences.create(
        context,
        "caseforge_secure",
        master,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    private val prefs = context.getSharedPreferences("caseforge_prefs", Context.MODE_PRIVATE)

    /**
     * Claude API key. Stored encrypted; falls back to the BuildConfig default that's baked
     * in at compile time from local.properties (caseforge.claudeApiKey). That fallback lets
     * the dev's APK come pre-configured without ever committing the key to git.
     */
    var claudeApiKey: String
        get() = secure.getString(K_API_KEY, "").orEmpty()
            .ifBlank { BuildConfig.CLAUDE_API_KEY_DEFAULT }
        set(value) { secure.edit().putString(K_API_KEY, value).apply() }

    var model: String
        get() {
            val stored = prefs.getString(K_MODEL, "").orEmpty()
            // Auto-migrate older default that we shipped pre-#10 — some accounts return 404 for it.
            return if (stored.isBlank() || stored == "claude-sonnet-4-6") "claude-sonnet-4-5" else stored
        }
        set(value) { prefs.edit().putString(K_MODEL, value).apply() }

    var autonomousActuation: Boolean
        get() = prefs.getBoolean(K_AUTONOMOUS, true)
        set(value) { prefs.edit().putBoolean(K_AUTONOMOUS, value).apply() }

    var autoStartOnVin: Boolean
        get() = prefs.getBoolean(K_AUTOSTART_VIN, true)
        set(value) { prefs.edit().putBoolean(K_AUTOSTART_VIN, value).apply() }

    var killSwitch: Boolean
        get() = prefs.getBoolean(K_KILL, false)
        set(value) { prefs.edit().putBoolean(K_KILL, value).apply() }

    var requireApproval: Boolean
        get() = prefs.getBoolean(K_REQUIRE_APPROVAL, false)
        set(value) { prefs.edit().putBoolean(K_REQUIRE_APPROVAL, value).apply() }

    /** Speak the agent ticker out loud via TTS. */
    var speakEnabled: Boolean
        get() = prefs.getBoolean(K_SPEAK, false)
        set(value) { prefs.edit().putBoolean(K_SPEAK, value).apply() }

    var voiceEnabled: Boolean
        get() = prefs.getBoolean(K_VOICE, false)
        set(value) { prefs.edit().putBoolean(K_VOICE, value).apply() }

    /** Experimental direct VCI Bluetooth (Phase 2 spike — not for production main until approved). */
    var directVciExperimental: Boolean
        get() = prefs.getBoolean(K_DIRECT_VCI, false)
        set(value) { prefs.edit().putBoolean(K_DIRECT_VCI, value).apply() }

    val directVciExperimentalFlow: Flow<Boolean> = callbackFlow {
        trySend(directVciExperimental)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == K_DIRECT_VCI) trySend(directVciExperimental)
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }.distinctUntilChanged()

    /** User-picked bonded device when name does not match [VciSocketClient.VCI_NAME_PREFIXES]. */
    var vciSelectedBtAddress: String?
        get() = prefs.getString(K_VCI_BT_ADDRESS, null)?.takeIf { it.isNotBlank() }
        set(value) {
            prefs.edit().putString(K_VCI_BT_ADDRESS, value?.trim()).apply()
        }

    /** First header byte for VCI framing (default 0x55 — confirm on vehicle via probe). */
    var vciHeaderByte0: Int
        get() = prefs.getInt(K_VCI_HDR0, 0x55)
        set(value) { prefs.edit().putInt(K_VCI_HDR0, value and 0xFF).apply() }

    /** Second header byte for VCI framing (default 0xAA). */
    var vciHeaderByte1: Int
        get() = prefs.getInt(K_VCI_HDR1, 0xAA)
        set(value) { prefs.edit().putInt(K_VCI_HDR1, value and 0xFF).apply() }

    /** When true, SPP transport uses hex-ASCII lines instead of raw binary. */
    var vciUseHexEncoding: Boolean
        get() = prefs.getBoolean(K_VCI_HEX, false)
        set(value) { prefs.edit().putBoolean(K_VCI_HEX, value).apply() }

    /** Set after tablet probe sweep locks header + transport. */
    var vciProtocolConfirmed: Boolean
        get() = prefs.getBoolean(K_VCI_PROTO_OK, false)
        set(value) { prefs.edit().putBoolean(K_VCI_PROTO_OK, value).apply() }

    /** Whether the first-launch setup wizard has been completed. */
    var wizardComplete: Boolean
        get() = prefs.getBoolean(K_WIZARD, false)
        set(value) { prefs.edit().putBoolean(K_WIZARD, value).apply() }

    /** Theme mode: "system" | "light" | "dark". Default follows the device. */
    var themeMode: String
        get() = prefs.getString(K_THEME, "system") ?: "system"
        set(value) { prefs.edit().putString(K_THEME, value).apply() }

    /** Free-form notes that get prepended to the agent's system prompt every call. */
    var agentNotes: String
        get() = prefs.getString(K_NOTES, DEFAULT_AGENT_NOTES) ?: DEFAULT_AGENT_NOTES
        set(value) { prefs.edit().putString(K_NOTES, value).apply() }

    // ---- A6: overlayOnX431 ----

    /**
     * When true, [ScannerAccessibilityService] auto-launches [FullScreenOverlayService]
     * the moment any X431 package becomes the foreground window.
     * Default false — opt-in only.
     */
    var overlayOnX431: Boolean
        get() = prefs.getBoolean(K_OVERLAY_ON_X431, false)
        set(value) { prefs.edit().putBoolean(K_OVERLAY_ON_X431, value).apply() }

    /**
     * Reactive view of [overlayOnX431]. Backed by a SharedPreferences listener so every
     * collector sees the latest value immediately on subscription and on every change.
     */
    val overlayOnX431Flow: Flow<Boolean> = callbackFlow {
        // Emit the current value immediately so collectors don't wait for the first change.
        trySend(overlayOnX431)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == K_OVERLAY_ON_X431) trySend(overlayOnX431)
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }.distinctUntilChanged()

    /** Coroutine-friendly writer; delegates to the property setter. */
    suspend fun setOverlayOnX431(value: Boolean) {
        overlayOnX431 = value
    }

    // ---- C2: overlayOnboardingSeen ----

    /**
     * When true, the onboarding overlay is skipped on subsequent launches.
     * Default false — first-run only shows onboarding.
     */
    var overlayOnboardingSeen: Boolean
        get() = prefs.getBoolean(K_OVERLAY_ONBOARDING_SEEN, false)
        set(value) { prefs.edit().putBoolean(K_OVERLAY_ONBOARDING_SEEN, value).apply() }

    /**
     * Reactive view of [overlayOnboardingSeen]. Backed by a SharedPreferences listener so every
     * collector sees the latest value immediately on subscription and on every change.
     */
    val overlayOnboardingSeenFlow: Flow<Boolean> = callbackFlow {
        // Emit the current value immediately so collectors don't wait for the first change.
        trySend(overlayOnboardingSeen)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == K_OVERLAY_ONBOARDING_SEEN) trySend(overlayOnboardingSeen)
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }.distinctUntilChanged()

    /** Coroutine-friendly writer; delegates to the property setter. */
    suspend fun setOverlayOnboardingSeen(value: Boolean) {
        overlayOnboardingSeen = value
    }

    // ---- D1: emergencyDismissHintSeen ----

    /**
     * Tracks whether the user has already seen a hint/tip about the 3-second long-press
     * emergency dismiss gesture. Set to true after showing a one-time nudge.
     * Default false — hint eligible on first launch.
     */
    var emergencyDismissHintSeen: Boolean
        get() = prefs.getBoolean(K_EMERGENCY_DISMISS_HINT_SEEN, false)
        set(value) { prefs.edit().putBoolean(K_EMERGENCY_DISMISS_HINT_SEEN, value).apply() }

    companion object {
        private const val K_API_KEY = "claude_api_key"
        private const val K_MODEL = "claude_model"
        private const val K_AUTONOMOUS = "autonomous_actuation"
        private const val K_AUTOSTART_VIN = "autostart_on_vin"
        private const val K_KILL = "kill_switch"
        private const val K_REQUIRE_APPROVAL = "require_approval"
        private const val K_SPEAK = "speak_enabled"
        private const val K_VOICE = "voice_enabled"
        private const val K_DIRECT_VCI = "direct_vci_experimental"
        private const val K_VCI_BT_ADDRESS = "vci_bt_address"
        private const val K_VCI_HDR0 = "vci_header_byte0"
        private const val K_VCI_HDR1 = "vci_header_byte1"
        private const val K_VCI_HEX = "vci_use_hex_encoding"
        private const val K_VCI_PROTO_OK = "vci_protocol_confirmed"
        private const val K_NOTES = "agent_notes"
        private const val K_THEME = "theme_mode"
        private const val K_WIZARD = "wizard_complete"
        private const val K_OVERLAY_ON_X431 = "overlay_on_x431"   // A6
        private const val K_OVERLAY_ONBOARDING_SEEN = "overlay_onboarding_seen"   // C2
        private const val K_EMERGENCY_DISMISS_HINT_SEEN = "emergency_dismiss_hint_seen"   // D1

        const val DEFAULT_AGENT_NOTES = """About this app
==============
You (the agent) live inside CaseForge Scanner AI on the technician's X431 PRO/PROS/V+ tablet.
The technician uses you to drive the Launch X431 diagnostic app — you see its UI via Android's
AccessibilityService and operate it via the tools you've been given.

The technician owns this tablet and this VCI. You are NOT operating on a customer's behalf;
you are the tech's copilot. Treat the tech as an experienced automotive professional — be
concise, technical, and decisive. Do not over-explain basics they already know.

What you can do
===============
- read_screen / tap / type / scroll / back / wait_for — drive the X431 app UI
- capture_screenshot — for graphical screens (gauges, charts) the accessibility tree misses
- vin_lookup — NHTSA decode + recalls for a 17-char VIN. Always call when you first see one.
- repair_info_lookup — separate Claude call for DTC causes / tests / TSBs / wiring hints
- propose_actuation — only required when the per-action approval mode is on
- finish_session — when the goal is met, with a structured summary

What you can't do
=================
- Bypass Launch's licensing / subscriptions. Some advanced functions (key programming on
  newer cars, online ECU programming) require the tech's X431 subscription be active.
- Talk to the VCI dongle directly. You always go through the X431 app.
- Modify the X431 app. You drive it as a user would.

Operating principles
====================
- Always read_screen before acting. Don't tap from memory.
- Take ONE action at a time. Verify with read_screen before the next.
- When a VIN is on screen, call vin_lookup early so you know the vehicle.
- When you see an unfamiliar DTC, call repair_info_lookup before recommending repairs.
- The tech has fully-autonomous actuation enabled by default. Don't ask before each test;
  log a one-line reason in your reasoning instead.
- If a screen looks ambiguous (graphical gauges, charts), call capture_screenshot.
- If the X431 app stops responding for 8+ seconds, press back() and try a different path.
- When the goal is met, call finish_session with the structured summary. Don't keep going.

History — what this app has been through
=========================================
- Built as an Android Kotlin/Compose wrapper that uses AccessibilityService to operate the
  X431 app. Architecture: AgentRunner is a Claude tool-use loop.
- Phase 0: GitHub CI builds APKs on every push to main; tablet pulls from the 'latest' release.
- Phase 1: One-tap Full Scan All Modules + repair_info_lookup tool.
- Round 14: Action log viewer, TTS readout, NHTSA vin_lookup, conversation trimming.
- Round 15: Unified Dashboard UI as main view; Customer + RepairOrder DB entities for
  shop integration; voice mute toggle.
- Planned: streaming responses, guided wizards per procedure, camera vision tool,
  microphone for acoustic diagnosis, customer-facing PDF, OEM playbooks.

Notes from the technician
=========================
(Edit this section freely. Examples:)
- I mostly work on GM trucks and Stellantis SUVs.
- Default to bidirectional confirmation before recommending parts swaps.
- Skip the cosmetic codes (B-codes on old airbags); flag them but don't dive in.
- When in doubt, finish_session and let me decide.
"""
    }
}
