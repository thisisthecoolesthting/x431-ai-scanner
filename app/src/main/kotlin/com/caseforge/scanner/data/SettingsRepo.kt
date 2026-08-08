package com.caseforge.scanner.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.caseforge.scanner.BuildConfig
import com.caseforge.scanner.agent.session.SessionTokenAccounting
import com.caseforge.scanner.transfer.DEFAULT_RECEIVER_HOST
import com.caseforge.scanner.transfer.DEFAULT_RECEIVER_PORT
import com.caseforge.scanner.transfer.TransferDeliveryMode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Stores secrets (Claude API key) in EncryptedSharedPreferences, and non-secret prefs
 * (model choice, kill switch state, autonomous mode toggle) in plain SharedPreferences.
 *
 * Merged from:
 * - A6: overlayOnOemDiag, overlayOnOemDiagFlow, setOverlayOnOemDiag
 * - C2: overlayOnboardingSeen, overlayOnboardingSeenFlow, setOverlayOnboardingSeen
 * - D1: emergencyDismissHintSeen (property only, no Flow)
 *
 * All three overlay properties follow identical structural pattern: property getter/setter +
 * optional Flow-backed reactive view + optional suspend writer.
 */
class SettingsRepo(context: Context) {
    private val secure: SharedPreferences = createSecurePrefs(context)

    private val prefs = context.getSharedPreferences("tcw_prefs", Context.MODE_PRIVATE)

    /**
     * Anthropic API key resolution order (rotate later via rebuild or Settings override):
     * 1. [BuildConfig.ANTHROPIC_API_KEY] — baked at `assembleDebug` from repo-root `.env`
     * 2. Encrypted user entry in secure prefs (future override wins once we flip priority)
     * 3. [BuildConfig.CLAUDE_API_KEY_DEFAULT] — legacy local.properties fallback
     */
    var claudeApiKey: String
        get() = BuildConfig.ANTHROPIC_API_KEY.ifBlank {
            secure.getString(K_API_KEY, "").orEmpty()
                .ifBlank { BuildConfig.CLAUDE_API_KEY_DEFAULT }
        }
        set(value) { secure.edit().putString(K_API_KEY, value).apply() }

    /** True when the operator typed a key in Settings (ignored while BuildConfig embed is non-blank). */
    val hasUserStoredApiKey: Boolean
        get() = secure.getString(K_API_KEY, "").orEmpty().isNotBlank()

    /** True when a compile-time default exists (`.env` debug embed or legacy local.properties). */
    val hasEmbeddedBuildApiKey: Boolean
        get() = BuildConfig.ANTHROPIC_API_KEY.isNotBlank() ||
            BuildConfig.CLAUDE_API_KEY_DEFAULT.isNotBlank()

    var model: String
        get() {
            val stored = prefs.getString(K_MODEL, "").orEmpty()
            // Auto-migrate older defaults to the current env-configured Sonnet family target.
            return if (stored.isBlank() || stored == "claude-sonnet-4-5") DEFAULT_MODEL else stored
        }
        set(value) { prefs.edit().putString(K_MODEL, value).apply() }

    var autonomousActuation: Boolean
        get() = prefs.getBoolean(K_AUTONOMOUS, true)
        set(value) { prefs.edit().putBoolean(K_AUTONOMOUS, value).apply() }

    var autoStartOnVin: Boolean
        get() = prefs.getBoolean(K_AUTOSTART_VIN, false)
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
    /** Detached product: Direct VCI is the default path (not experimental). */
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

    /**
     * Plan B native OEM OBD wedge (experimental). Default off — no lazy OBD engine init until enabled.
     */
    var nativeObdExperimental: Boolean
        get() = prefs.getBoolean(K_NATIVE_OBD_EXPERIMENTAL, false)
        set(value) { prefs.edit().putBoolean(K_NATIVE_OBD_EXPERIMENTAL, value).apply() }

    /** Same flag as [nativeObdExperimental]; use for feature checks on the OEM engine facade. */
    val nativeObdWedgeEnabled: Boolean
        get() = nativeObdExperimental

    /**
     * When true with [nativeObdExperimental], route native OBD through [com.caseforge.scanner.obd.VciObdTransport]
     * even if [directVciExperimental] is off. Default false.
     */
    var nativeObdUseVci: Boolean
        get() = prefs.getBoolean(K_NATIVE_OBD_USE_VCI, false)
        set(value) { prefs.edit().putBoolean(K_NATIVE_OBD_USE_VCI, value).apply() }

    /**
     * Plan A bridge: when direct VCI/OEM USB fails but Launch/X431 is installed, read DTCs from
     * accessibility golden captures and guide the tech back into Launch diagnostics.
     */
    var launchPlanABridgeEnabled: Boolean
        get() = prefs.getBoolean(K_LAUNCH_PLAN_A_BRIDGE_ENABLED, false)
        set(value) { prefs.edit().putBoolean(K_LAUNCH_PLAN_A_BRIDGE_ENABLED, value).apply() }

    /**
     * Use VCI-backed OBD transport for the Plan B OEM path when native OBD is on and either
     * Direct VCI is enabled or [nativeObdUseVci] is set.
     */
    fun useVciNativeObdTransport(): Boolean =
        nativeObdExperimental && (directVciExperimental || nativeObdUseVci)

    /**
     * Plan B tier 0 (alias of [nativeObdExperimental]). Unified naming for tier cards / gating.
     */
    var tier0ObdEnabled: Boolean
        get() = nativeObdExperimental
        set(value) { nativeObdExperimental = value }

    /** Plan B tier 1 — alias of [planbBodyRead]. */
    var tier1BodyEnabled: Boolean
        get() = planbBodyRead
        set(value) { planbBodyRead = value }

    /** Plan B tier 2 — alias of [planbCoding]. */
    var tier2CodingEnabled: Boolean
        get() = planbCoding
        set(value) { planbCoding = value }

    /** Plan B tier 3 — alias of [planbImmoInfo]. */
    var tier3ImmoEnabled: Boolean
        get() = planbImmoInfo
        set(value) { planbImmoInfo = value }

    /** Plan B tier 4 — alias of [planbProgramming]. Default off; enable only with an active trial. */
    var tier4ProgrammingEnabled: Boolean
        get() = planbProgramming
        set(value) { planbProgramming = value }

    /** Epoch ms when the operator accepted Tier 4 trial terms (0 = never). */
    var tier4TrialAcceptedAtMs: Long
        get() = prefs.getLong(K_TIER4_TRIAL_ACCEPTED_AT, 0L)
        private set(value) { prefs.edit().putLong(K_TIER4_TRIAL_ACCEPTED_AT, value.coerceAtLeast(0L)).apply() }

    /** Epoch ms when the trial expires (0 = unset). Set to accept + [TIER4_TRIAL_DURATION_MS]. */
    var tier4TrialExpiresAtMs: Long
        get() = prefs.getLong(K_TIER4_TRIAL_EXPIRES_AT, 0L)
        private set(value) { prefs.edit().putLong(K_TIER4_TRIAL_EXPIRES_AT, value.coerceAtLeast(0L)).apply() }

    /** Read-only trial marque list (v1); mirrors [com.caseforge.scanner.planb.PlanbMarque.TRIAL_MARQUES]. */
    val tier4TrialMarques: String
        get() = prefs.getString(K_TIER4_TRIAL_MARQUES, TIER4_TRIAL_MARQUES_DEFAULT) ?: TIER4_TRIAL_MARQUES_DEFAULT

    /** Primary marque picked on the trial gate screen (Ford/Jeep/Dodge/Ram/Chrysler). */
    var tier4TrialMarqueId: String?
        get() = prefs.getString(K_TIER4_TRIAL_MARQUE_ID, null)?.takeIf { it.isNotBlank() }
        private set(value) { prefs.edit().putString(K_TIER4_TRIAL_MARQUE_ID, value?.trim()).apply() }

    val tier4TrialAccepted: Boolean
        get() = tier4TrialAcceptedAtMs > 0L

    /** True while [tier4TrialAcceptedAtMs] is set and wall clock is before [tier4TrialExpiresAtMs]. */
    val tier4TrialActive: Boolean
        get() {
            val acceptedAt = tier4TrialAcceptedAtMs
            if (acceptedAt <= 0L) return false
            val expiresAt = tier4TrialExpiresAtMs
            if (expiresAt <= acceptedAt) return false
            return System.currentTimeMillis() < expiresAt
        }

    /** True when an active trial or operator-issued full license covers Tier 4. */
    val hasTier4ProgrammingEntitlement: Boolean
        get() = tier4TrialActive || tier4FullLicenseEnabled

    /** Operator-issued full Tier 4 license (survives trial expiry). Default off. */
    var tier4FullLicenseEnabled: Boolean
        get() = prefs.getBoolean(K_TIER4_FULL_LICENSE_ENABLED, false)
        private set(value) { prefs.edit().putBoolean(K_TIER4_FULL_LICENSE_ENABLED, value).apply() }

    /** Epoch ms when [redeemTier4LicenseCode] last succeeded (0 = never). */
    var tier4LicenseIssuedAtMs: Long
        get() = prefs.getLong(K_TIER4_LICENSE_ISSUED_AT, 0L)
        private set(value) { prefs.edit().putLong(K_TIER4_LICENSE_ISSUED_AT, value.coerceAtLeast(0L)).apply() }

    /** Optional comma-separated marque ids covered by the full license; blank = unrestricted. */
    var tier4LicenseMarques: String?
        get() = prefs.getString(K_TIER4_LICENSE_MARQUES, null)?.takeIf { it.isNotBlank() }
        private set(value) { prefs.edit().putString(K_TIER4_LICENSE_MARQUES, value?.trim()).apply() }

    /** Programming reference routes require trial or full license plus explicit Tier 4 enable. */
    fun canAccessTier4Programming(): Boolean =
        hasTier4ProgrammingEntitlement && tier4ProgrammingEnabled

    /**
     * Accept Tier 4 trial terms for [marque]. Sets trial window; does **not** flip [tier4ProgrammingEnabled].
     */
    fun acceptTier4Trial(marqueId: String) {
        val now = System.currentTimeMillis()
        tier4TrialAcceptedAtMs = now
        tier4TrialExpiresAtMs = now + TIER4_TRIAL_DURATION_MS
        tier4TrialMarqueId = marqueId
    }

    /** Enable Tier 4 programming checklists after trial accept or license redeem. */
    fun enableTier4ProgrammingForTrial() {
        check(hasTier4ProgrammingEntitlement) { "Tier 4 trial or full license required" }
        tier4ProgrammingEnabled = true
        onUserEnabledPlanBTier(4)
    }

    /**
     * MVP operator license gate — **not** cryptographic security; obfuscation for field MVP only.
     * Codes are [normalizeTier4LicenseCode] then djb2-hashed ([tier4LicenseCodeHash]).
     * Operator-issued code (document offline, not in UI): `TCW-T4-OPERATOR`.
     */
    fun redeemTier4LicenseCode(code: String, marques: String? = null): Boolean {
        val normalized = normalizeTier4LicenseCode(code)
        if (normalized.isBlank()) return false
        if (tier4LicenseCodeHash(normalized) != TIER4_LICENSE_CODE_HASH) return false
        tier4FullLicenseEnabled = true
        tier4LicenseIssuedAtMs = System.currentTimeMillis()
        tier4LicenseMarques = marques?.trim()?.takeIf { it.isNotBlank() }
        return true
    }

    fun clearTier4FullLicense() {
        tier4FullLicenseEnabled = false
        tier4LicenseIssuedAtMs = 0L
        tier4LicenseMarques = null
        if (!hasTier4ProgrammingEntitlement) {
            tier4ProgrammingEnabled = false
            onUserDisabledPlanBTier(4)
        }
    }

    fun clearTier4Trial() {
        tier4TrialAcceptedAtMs = 0L
        tier4TrialExpiresAtMs = 0L
        tier4TrialMarqueId = null
        if (!tier4FullLicenseEnabled) {
            tier4ProgrammingEnabled = false
            onUserDisabledPlanBTier(4)
        }
    }

    /** Epoch ms when the operator accepted SKREEM trial terms (0 = never). Separate from Tier 4. */
    var skreemTrialAcceptedAtMs: Long
        get() = prefs.getLong(K_SKREEM_TRIAL_ACCEPTED_AT, 0L)
        private set(value) { prefs.edit().putLong(K_SKREEM_TRIAL_ACCEPTED_AT, value.coerceAtLeast(0L)).apply() }

    /** Epoch ms when the SKREEM trial expires (0 = unset). */
    var skreemTrialExpiresAtMs: Long
        get() = prefs.getLong(K_SKREEM_TRIAL_EXPIRES_AT, 0L)
        private set(value) { prefs.edit().putLong(K_SKREEM_TRIAL_EXPIRES_AT, value.coerceAtLeast(0L)).apply() }

    /** Read-only Stellantis marque list for SKREEM trial (v1). */
    val skreemTrialMarques: String
        get() = prefs.getString(K_SKREEM_TRIAL_MARQUES, SKREEM_TRIAL_MARQUES_DEFAULT) ?: SKREEM_TRIAL_MARQUES_DEFAULT

    /** Primary Stellantis marque picked on the SKREEM trial gate (Jeep/Dodge/Ram/Chrysler). */
    var skreemTrialMarqueId: String?
        get() = prefs.getString(K_SKREEM_TRIAL_MARQUE_ID, null)?.takeIf { it.isNotBlank() }
        private set(value) { prefs.edit().putString(K_SKREEM_TRIAL_MARQUE_ID, value?.trim()).apply() }

    /** Tier 3 SKREEM immo detail enabled after trial accept — independent of Tier 4 programming trial. */
    var skreemImmoInfoEnabled: Boolean
        get() = prefs.getBoolean(K_SKREEM_IMMO_INFO_ENABLED, false)
        private set(value) { prefs.edit().putBoolean(K_SKREEM_IMMO_INFO_ENABLED, value).apply() }

    val skreemTrialAccepted: Boolean
        get() = skreemTrialAcceptedAtMs > 0L

    val skreemTrialActive: Boolean
        get() {
            val acceptedAt = skreemTrialAcceptedAtMs
            if (acceptedAt <= 0L) return false
            val expiresAt = skreemTrialExpiresAtMs
            if (expiresAt <= acceptedAt) return false
            return System.currentTimeMillis() < expiresAt
        }

    /** Immobilizer info routes require an active SKREEM trial and explicit immo enable. */
    fun canAccessSkreemImmoInfo(): Boolean =
        skreemTrialActive && skreemImmoInfoEnabled

    /**
     * Accept SKREEM trial terms for [marqueId] (Stellantis only). Sets trial window; does **not** enable immo UI.
     */
    fun acceptSkreemTrial(marqueId: String) {
        val now = System.currentTimeMillis()
        skreemTrialAcceptedAtMs = now
        skreemTrialExpiresAtMs = now + SKREEM_TRIAL_DURATION_MS
        skreemTrialMarqueId = marqueId
    }

    /** Enable Tier 3 SKREEM immobilizer info after trial accept. */
    fun enableSkreemImmoForTrial() {
        check(skreemTrialActive) { "SKREEM trial is not active" }
        skreemImmoInfoEnabled = true
        planbImmoInfo = true
        onUserEnabledPlanBTier(3)
    }

    fun clearSkreemTrial() {
        skreemTrialAcceptedAtMs = 0L
        skreemTrialExpiresAtMs = 0L
        skreemTrialMarqueId = null
        skreemImmoInfoEnabled = false
    }

    /**
     * When true (default), tiers 1–4 only become effective after a successful native OBD refresh /
     * transport session: they must appear in the snapshot taken at connect, or the user must
     * explicitly toggle them on afterward ([tierSessionUnlockMask]).
     */
    var tierSafetyFirstConnect: Boolean
        get() = prefs.getBoolean(K_TIER_SAFETY_FIRST_CONNECT, true)
        set(value) { prefs.edit().putBoolean(K_TIER_SAFETY_FIRST_CONNECT, value).apply() }

    /**
     * Set true after the first successful [com.caseforge.scanner.oem.OemEngineFacade] OBD refresh
     * (or any held transport connect that records a tier snapshot).
     */
    var hasCompletedFirstConnect: Boolean
        get() = prefs.getBoolean(K_HAS_COMPLETED_FIRST_CONNECT, false)
        set(value) { prefs.edit().putBoolean(K_HAS_COMPLETED_FIRST_CONNECT, value).apply() }

    /**
     * Bitmask (bits 0–4 = tiers) captured at last transport session start.
     * Cleared when a new session records a fresh snapshot.
     */
    var tierSnapshotAtConnect: Int
        get() = prefs.getInt(K_TIER_SNAPSHOT_AT_CONNECT, 0)
        private set(value) { prefs.edit().putInt(K_TIER_SNAPSHOT_AT_CONNECT, value).apply() }

    /**
     * Bitmask of tiers the user explicitly enabled via Settings while a session may be held;
     * ORed with [tierSnapshotAtConnect] when [tierSafetyFirstConnect] is on.
     */
    var tierSessionUnlockMask: Int
        get() = prefs.getInt(K_TIER_SESSION_UNLOCK_MASK, 0)
        private set(value) { prefs.edit().putInt(K_TIER_SESSION_UNLOCK_MASK, value).apply() }

    /** Bits 0–4 set for tiers currently on in settings (intent flags, not gated). */
    fun buildTierBitsFromSettings(): Int {
        var m = 0
        if (nativeObdExperimental) m = m or TIER_BIT_0
        if (planbBodyRead) m = m or TIER_BIT_1
        if (planbCoding) m = m or TIER_BIT_2
        if (planbImmoInfo) m = m or TIER_BIT_3
        if (planbProgramming) m = m or TIER_BIT_4
        return m
    }

    fun snapshotTierIndicesAtLastConnect(): Set<Int> {
        val m = tierSnapshotAtConnect
        return (0..4).filter { (m and (1 shl it)) != 0 }.toSet()
    }

    /**
     * Call when a new OBD transport session opens (connect succeeded, before reads).
     * Resets unlock mask; [hasCompletedFirstConnect] is set only after a successful refresh
     * ([markObdRefreshSucceeded]).
     */
    fun applyTierSnapshotForNewTransportSession() {
        tierSnapshotAtConnect = buildTierBitsFromSettings()
        tierSessionUnlockMask = 0
    }

    /** After [refreshSuspend] / OBD read cycle completes without error. */
    fun markObdRefreshSucceeded() {
        hasCompletedFirstConnect = true
    }

    fun onUserEnabledPlanBTier(tierIndex: Int) {
        if (tierIndex in 1..4) {
            tierSessionUnlockMask = tierSessionUnlockMask or (1 shl tierIndex)
        }
    }

    fun onUserDisabledPlanBTier(tierIndex: Int) {
        if (tierIndex in 1..4) {
            tierSessionUnlockMask = tierSessionUnlockMask and (1 shl tierIndex).inv()
        }
    }

    /**
     * Tier 0 follows [nativeObdExperimental] only. Tiers 1–4 respect [tierSafetyFirstConnect]
     * and snapshot/unlock after [hasCompletedFirstConnect].
     */
    fun isPlanBTierEffective(tierIndex: Int): Boolean {
        require(tierIndex in 0..4)
        if (tierIndex == 0) return nativeObdExperimental
        val want = when (tierIndex) {
            1 -> planbBodyRead
            2 -> planbCoding
            3 -> planbImmoInfo
            4 -> planbProgramming
            else -> false
        }
        if (!want) return false
        if (!tierSafetyFirstConnect) return true
        if (!hasCompletedFirstConnect) return false
        val bit = 1 shl tierIndex
        return (tierSnapshotAtConnect and bit) != 0 || (tierSessionUnlockMask and bit) != 0
    }

    /**
     * Plan B tier 1: read-only body / convenience module access (Jeep-neutral scaffolding).
     * Default off — opt-in only.
     */
    var planbBodyRead: Boolean
        get() = prefs.getBoolean(K_PLANB_BODY_READ, false)
        set(value) { prefs.edit().putBoolean(K_PLANB_BODY_READ, value).apply() }

    /**
     * When true, Plan B gateway DTC lane may return synthetic golden-replay stubs (developer / bench only).
     * Default off — also toggle from Settings → Plan B (Tier 1) **Gateway replay (Ford bench)** when body read is on,
     * or via adb prefs for `planb_gateway_replay`.
     */
    var planbGatewayReplay: Boolean
        get() = prefs.getBoolean(K_PLANB_GATEWAY_REPLAY, false)
        set(value) { prefs.edit().putBoolean(K_PLANB_GATEWAY_REPLAY, value).apply() }

    /**
     * Tier 1 safe-perf scaffold: allow same-ECU gateway-session connection reuse.
     * Default false; enable from Settings once the tablet build is stable on this lane.
     */
    var planbGatewaySessionReuse: Boolean
        get() = prefs.getBoolean(K_PLANB_GATEWAY_SESSION_REUSE, false)
        set(value) { prefs.edit().putBoolean(K_PLANB_GATEWAY_SESSION_REUSE, value).apply() }

    /**
     * Plan B tier 2: reversible coding operations (stub until G4).
     * Default off — opt-in only.
     */
    var planbCoding: Boolean
        get() = prefs.getBoolean(K_PLANB_CODING, false)
        set(value) { prefs.edit().putBoolean(K_PLANB_CODING, value).apply() }

    /**
     * Plan B tier 3: immobilizer info-only (no key programming).
     * Default off — opt-in only.
     */
    var planbImmoInfo: Boolean
        get() = prefs.getBoolean(K_PLANB_IMMO_INFO, false)
        set(value) { prefs.edit().putBoolean(K_PLANB_IMMO_INFO, value).apply() }

    /**
     * Plan B tier 4: programming checklist reference only (partner / manual workflows).
     * Default off — opt-in only. No automated apply path in-app.
     */
    var planbProgramming: Boolean
        get() = prefs.getBoolean(K_PLANB_PROGRAMMING, false)
        set(value) { prefs.edit().putBoolean(K_PLANB_PROGRAMMING, value).apply() }

    // ---- DeepSeek wishlist toggles (UI scaffolding) ----
    /** Enables standalone BLE ELM327 diagnostics entry points in UI. */
    var deepseekBleElmStandalone: Boolean
        get() = prefs.getBoolean(DeepSeekSettingsKeys.BLE_ELM_STANDALONE, false)
        set(value) { prefs.edit().putBoolean(DeepSeekSettingsKeys.BLE_ELM_STANDALONE, value).apply() }

    /** Enables GPS-assisted hardware workflows (stub toggle only). */
    var deepseekGpsEnabled: Boolean
        get() = prefs.getBoolean(DeepSeekSettingsKeys.GPS_ENABLED, false)
        set(value) { prefs.edit().putBoolean(DeepSeekSettingsKeys.GPS_ENABLED, value).apply() }

    /** Enables OCR-assisted hardware workflows (stub toggle only). */
    var deepseekOcrEnabled: Boolean
        get() = prefs.getBoolean(DeepSeekSettingsKeys.OCR_ENABLED, false)
        set(value) { prefs.edit().putBoolean(DeepSeekSettingsKeys.OCR_ENABLED, value).apply() }

    /** Enables gateway pool workflows (stub toggle only). */
    var deepseekGatewayPoolEnabled: Boolean
        get() = prefs.getBoolean(DeepSeekSettingsKeys.GATEWAY_POOL_ENABLED, false)
        set(value) { prefs.edit().putBoolean(DeepSeekSettingsKeys.GATEWAY_POOL_ENABLED, value).apply() }

    /** Enables streaming workflows (stub toggle only). */
    var deepseekStreamingEnabled: Boolean
        get() = prefs.getBoolean(DeepSeekSettingsKeys.STREAMING_ENABLED, true)
        set(value) { prefs.edit().putBoolean(DeepSeekSettingsKeys.STREAMING_ENABLED, value).apply() }

    /** VCI link: `auto` (USB first), `usb`, or `bluetooth`. */
    var vciTransportMode: String
        get() = prefs.getString(K_VCI_TRANSPORT, "auto") ?: "auto"
        set(value) { prefs.edit().putString(K_VCI_TRANSPORT, value.lowercase()).apply() }

    /**
     * Standalone link picker: `auto`, `elm327_usb`, `oem_usb`, `oem_bt`, `elm327_bt`.
     * Legacy reads accept `launch_usb` / `launch_bt` and map to `oem_*`.
     */
    var linkTransport: String
        get() = normalizeLinkTransport(prefs.getString(K_LINK_TRANSPORT, "auto") ?: "auto")
        set(value) {
            prefs.edit().putString(K_LINK_TRANSPORT, normalizeLinkTransport(value.lowercase())).apply()
        }

    private fun normalizeLinkTransport(raw: String): String = when (raw.lowercase()) {
        "launch_usb", "vci_usb" -> "oem_usb"
        "launch_bt", "vci_bt" -> "oem_bt"
        "usb_obd", "usb_cable" -> "elm327_usb"
        "bluetooth", "obd_bt" -> "elm327_bt"
        "auto", "elm327_usb", "oem_usb", "oem_bt", "elm327_bt" -> raw.lowercase()
        else -> raw.lowercase()
    }

    /** When false, the app never scans or connects Bluetooth (USB-only default). */
    var bluetoothTransportEnabled: Boolean
        get() = prefs.getBoolean(K_BT_TRANSPORT_ENABLED, true)
        set(value) { prefs.edit().putBoolean(K_BT_TRANSPORT_ENABLED, value).apply() }

    var bluetoothPairingHintSeen: Boolean
        get() = prefs.getBoolean(K_BT_PAIRING_HINT_SEEN, false)
        set(value) { prefs.edit().putBoolean(K_BT_PAIRING_HINT_SEEN, value).apply() }

    /** User-picked bonded device when name does not match [BluetoothVciClient.VCI_NAME_PREFIXES]. */
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

    val vciProtocolConfirmedFlow: Flow<Boolean> = callbackFlow {
        trySend(vciProtocolConfirmed)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == K_VCI_PROTO_OK) trySend(vciProtocolConfirmed)
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }.distinctUntilChanged()

    /** Phase B: direct VCI read path is armed only when experimental toggle and probe lock both set. */
    fun directVciReady(): Boolean = directVciExperimental && vciProtocolConfirmed

    /**
     * Auto-connect prefers OEM VCI USB/BT when direct VCI is confirmed or native OBD routes via VCI.
     * Does not require [directVciExperimental] when [nativeObdUseVci] alone is set.
     */
    fun preferOemVciTransport(): Boolean =
        (directVciExperimental && vciProtocolConfirmed) || nativeObdUseVci

    /** Reset probe lock — keeps header/hex prefs for the next sweep baseline. */
    fun clearVciProtocolConfirmation() {
        vciProtocolConfirmed = false
    }

    /** Whether the first-launch setup wizard has been completed. */
    var wizardComplete: Boolean
        get() = prefs.getBoolean(K_WIZARD, true)
        set(value) { prefs.edit().putBoolean(K_WIZARD, value).apply() }

    /**
     * Ordered setup/live checklist finished — compact home is the default [MainActivity] `"main"` route.
     */
    var setupLiveComplete: Boolean
        get() = prefs.getBoolean(K_SETUP_LIVE_COMPLETE, false)
        set(value) { prefs.edit().putBoolean(K_SETUP_LIVE_COMPLETE, value).apply() }

    /** Theme mode: "system" | "light" | "dark". Default follows the device. */
    var themeMode: String
        get() = prefs.getString(K_THEME, "system") ?: "system"
        set(value) { prefs.edit().putString(K_THEME, value).apply() }

    /** Free-form notes that get prepended to the agent's system prompt every call. */
    var agentNotes: String
        get() = prefs.getString(K_NOTES, DEFAULT_AGENT_NOTES) ?: DEFAULT_AGENT_NOTES
        set(value) { prefs.edit().putString(K_NOTES, value).apply() }

    // ---- Transfer: receiver PC settings ----

    /** IP or hostname of the office PC running lan-export-receiver.ps1. */
    var receiverPcHost: String
        get() = prefs.getString(K_RECEIVER_PC_HOST, DEFAULT_RECEIVER_HOST) ?: DEFAULT_RECEIVER_HOST
        set(value) { prefs.edit().putString(K_RECEIVER_PC_HOST, value.trim()).apply() }

    /** Port that lan-export-receiver.ps1 is listening on. Default 8765. */
    var receiverPcPort: Int
        get() = prefs.getInt(K_RECEIVER_PC_PORT, DEFAULT_RECEIVER_PORT)
        set(value) { prefs.edit().putInt(K_RECEIVER_PC_PORT, value).apply() }

    /**
     * When true, the uploader falls back to the legacy multipart/form-data body instead of raw
     * Content-Length streaming. Disabled by default — opt-in from Settings for back-compat only.
     */
    var useMultipartFallback: Boolean
        get() = prefs.getBoolean(K_USE_MULTIPART_FALLBACK, false)
        set(value) { prefs.edit().putBoolean(K_USE_MULTIPART_FALLBACK, value).apply() }

    /**
     * Default [TransferDeliveryMode.SHARE] — zip + Android share sheet ($0, no upload API).
     * [TransferDeliveryMode.SELF_HOSTED] posts to [transferDropUrl] you control (your VPS).
     * [TransferDeliveryMode.LAN_PC] is legacy same-Wi‑Fi push to office PC.
     */
    var transferDeliveryMode: String
        get() = TransferDeliveryMode.normalize(
            prefs.getString(K_TRANSFER_DELIVERY_MODE, TransferDeliveryMode.SHARE) ?: TransferDeliveryMode.SHARE,
        )
        set(value) {
            prefs.edit().putString(K_TRANSFER_DELIVERY_MODE, TransferDeliveryMode.normalize(value)).apply()
        }

    /** Full base URL for self-hosted drop, e.g. `http://187.124.246.154:8765`. Empty = not configured. */
    var transferDropUrl: String
        get() = prefs.getString(K_TRANSFER_DROP_URL, "").orEmpty()
        set(value) { prefs.edit().putString(K_TRANSFER_DROP_URL, value.trim()).apply() }

    /**
     * When true, harvest upload also posts to [shopDeskIngestUrl] (HTTP or HTTPS).
     * Default off — opt-in only.
     */
    var shopDeskIngestEnabled: Boolean
        get() = prefs.getBoolean(K_SHOP_DESK_INGEST_ENABLED, false)
        set(value) { prefs.edit().putBoolean(K_SHOP_DESK_INGEST_ENABLED, value).apply() }

    /**
     * When true, setup wizard steps POST to Shop Desk `POST /api/ingest/setup-step` on LAN.
     * Unset pref defaults to on when [isLanShopDeskConfiguration] (HTTP desk, not production HTTPS).
     */
    var shopDeskLanReportingEnabled: Boolean
        get() {
            if (!prefs.contains(K_SHOP_DESK_LAN_REPORTING_ENABLED)) {
                return isLanShopDeskConfiguration()
            }
            return prefs.getBoolean(K_SHOP_DESK_LAN_REPORTING_ENABLED, isLanShopDeskConfiguration())
        }
        set(value) { prefs.edit().putBoolean(K_SHOP_DESK_LAN_REPORTING_ENABLED, value).apply() }

    /** Stable tablet id for Shop Desk live setup panel rows. */
    val setupDeviceId: String
        get() {
            val existing = prefs.getString(K_SETUP_DEVICE_ID, "").orEmpty().trim()
            if (existing.isNotBlank()) return existing
            val generated = "cu1-${UUID.randomUUID()}"
            prefs.edit().putString(K_SETUP_DEVICE_ID, generated).apply()
            return generated
        }

    fun isLanShopDeskConfiguration(): Boolean {
        if (shopDeskUseProductionDesk) return false
        val url = shopDeskIngestUrl.trim().lowercase()
        return url.startsWith("http://")
    }

    fun shouldReportSetupStepsToLan(): Boolean =
        shopDeskLanReportingEnabled && isLanShopDeskConfiguration()

    /**
     * When true, tablet may emit LAN discovery beacons while harvest UI is active.
     * Phase 1 scaffold only; no mDNS/UDP transmitter in-app yet.
     */
    var shopDeskLanBroadcastEnabled: Boolean
        get() = prefs.getBoolean(K_SHOP_DESK_LAN_BROADCAST_ENABLED, false)
        set(value) { prefs.edit().putBoolean(K_SHOP_DESK_LAN_BROADCAST_ENABLED, value).apply() }

    /** Epoch millis of the last manual LAN broadcast tap from Settings (Phase 1 stub). */
    var shopDeskLanBroadcastAtMs: Long
        get() = prefs.getLong(K_SHOP_DESK_LAN_BROADCAST_AT, 0L)
        private set(value) { prefs.edit().putLong(K_SHOP_DESK_LAN_BROADCAST_AT, value.coerceAtLeast(0L)).apply() }

    fun recordShopDeskLanBroadcastTap() {
        shopDeskLanBroadcastAtMs = System.currentTimeMillis()
    }

    /** Operator-entered Shop Desk ingest URL; blank means use [DEFAULT_SHOP_DESK_INGEST_URL] at runtime. */
    val shopDeskIngestUrlStored: String
        get() = prefs.getString(K_SHOP_DESK_INGEST_URL, "").orEmpty().trim()

    /**
     * When true, [shopDeskIngestUrl] resolves to [DEFAULT_SHOP_DESK_INGEST_URL_PROD] (HTTPS VPS desk).
     * When false, uses stored URL or [DEFAULT_SHOP_DESK_INGEST_URL] (localhost dev / LAN).
     * Also auto-detected when [shopDeskIngestUrl] is set to the production host.
     */
    var shopDeskUseProductionDesk: Boolean
        get() = prefs.getBoolean(K_SHOP_DESK_USE_PRODUCTION, false) ||
            isProductionDeskUrl(shopDeskIngestUrlStored)
        set(value) {
            prefs.edit().putBoolean(K_SHOP_DESK_USE_PRODUCTION, value).apply()
            if (value) {
                prefs.edit().putString(K_SHOP_DESK_INGEST_URL, DEFAULT_SHOP_DESK_INGEST_URL_PROD).apply()
            } else {
                val stored = shopDeskIngestUrlStored
                if (stored.isBlank() || isProductionDeskUrl(stored)) {
                    prefs.edit().putString(K_SHOP_DESK_INGEST_URL, DEFAULT_SHOP_DESK_INGEST_URL).apply()
                }
            }
        }

    /**
     * Shop Desk ingest endpoint (HTTP or HTTPS).
     * Defaults to [DEFAULT_SHOP_DESK_INGEST_URL] when unset; production toggle forces [DEFAULT_SHOP_DESK_INGEST_URL_PROD].
     */
    var shopDeskIngestUrl: String
        get() {
            if (shopDeskUseProductionDesk) return DEFAULT_SHOP_DESK_INGEST_URL_PROD
            return shopDeskIngestUrlStored.ifBlank { DEFAULT_SHOP_DESK_INGEST_URL }
        }
        set(value) {
            val trimmed = value.trim()
            prefs.edit().putString(K_SHOP_DESK_INGEST_URL, trimmed).apply()
            prefs.edit().putBoolean(K_SHOP_DESK_USE_PRODUCTION, isProductionDeskUrl(trimmed)).apply()
        }

    /** Last reason VCI/OBD connect was blocked because an OEM diagnostic app held foreground. */
    var lastOemDiagConnectBlockReason: String
        get() = prefs.getString(K_LAST_OEM_DIAG_CONNECT_BLOCK, "").orEmpty()
        set(value) { prefs.edit().putString(K_LAST_OEM_DIAG_CONNECT_BLOCK, value.trim()).apply() }

    var lastOemDiagConnectBlockAtMs: Long
        get() = prefs.getLong(K_LAST_OEM_DIAG_CONNECT_BLOCK_AT, 0L)
        set(value) { prefs.edit().putLong(K_LAST_OEM_DIAG_CONNECT_BLOCK_AT, value).apply() }

    fun recordOemDiagConnectBlock(reason: String) {
        lastOemDiagConnectBlockReason = reason
        lastOemDiagConnectBlockAtMs = System.currentTimeMillis()
    }

    /** Last vehicle-link connect attempt summary (success or failure), for Settings debug. */
    var lastConnectAttemptSummary: String
        get() = prefs.getString(K_LAST_CONNECT_ATTEMPT, "").orEmpty()
        private set(value) {
            prefs.edit().putString(K_LAST_CONNECT_ATTEMPT, value.trim().take(500)).apply()
        }

    var lastConnectAttemptAtMs: Long
        get() = prefs.getLong(K_LAST_CONNECT_ATTEMPT_AT, 0L)
        private set(value) { prefs.edit().putLong(K_LAST_CONNECT_ATTEMPT_AT, value).apply() }

    var lastConnectAttemptSuccess: Boolean
        get() = prefs.getBoolean(K_LAST_CONNECT_ATTEMPT_OK, false)
        private set(value) { prefs.edit().putBoolean(K_LAST_CONNECT_ATTEMPT_OK, value).apply() }

    fun recordConnectAttempt(success: Boolean, summary: String) {
        lastConnectAttemptSummary = summary
        lastConnectAttemptAtMs = System.currentTimeMillis()
        lastConnectAttemptSuccess = success
    }

    /** Epoch ms when a connect was proven by VIN or Mode 03 read (0 = never). */
    var lastWorkingConnectAtMs: Long
        get() = prefs.getLong(K_LAST_WORKING_CONNECT_AT, 0L)
        private set(value) { prefs.edit().putLong(K_LAST_WORKING_CONNECT_AT, value.coerceAtLeast(0L)).apply() }

    /** USB device name from the last proven connect (`/dev/bus/usb/...`). */
    var lastWorkingUsbDeviceId: String?
        get() = prefs.getString(K_LAST_WORKING_USB_DEVICE_ID, null)?.takeIf { it.isNotBlank() }
        private set(value) { prefs.edit().putString(K_LAST_WORKING_USB_DEVICE_ID, value?.trim()).apply() }

    /**
     * Persist adapter + transport after a confirmed VIN or Mode 03 read on a live link.
     * Does not throw — safe to call from connect coroutines.
     */
    fun recordProvenConnect(
        linkKind: String,
        transport: String,
        usbDeviceId: String? = null,
        vin: String? = null,
        protocolConfirmed: Boolean = vciProtocolConfirmed,
    ) {
        val normalizedTransport = normalizeLinkTransport(transport)
        lastWorkingConnectAtMs = System.currentTimeMillis()
        lastWorkingUsbDeviceId = usbDeviceId?.trim()?.takeIf { it.isNotBlank() }
        lastGoodTransport = normalizedTransport
        lastTransportLabel = linkKind.replace('_', ' ').trim()
        linkTransport = normalizedTransport
        if (protocolConfirmed) {
            vciProtocolConfirmed = true
        }
        vin?.trim()?.takeIf { it.isNotBlank() }?.let { lastVin = it }
        recordConnectAttempt(
            success = true,
            summary = "proven $linkKind transport=$normalizedTransport device=${usbDeviceId ?: "n/a"}",
        )
        applyTierSnapshotForNewTransportSession()
        markObdRefreshSucceeded()
    }

    // ---- A6: overlayOnOemDiag ----

    /**
     * When true, [ScannerAccessibilityService] auto-launches [FullScreenOverlayService]
     * the moment any OEM diagnostic package becomes the foreground window.
     * Default false — opt-in only.
     */
    var overlayOnOemDiag: Boolean
        get() = prefs.getBoolean(K_OVERLAY_ON_OEM_DIAG, false)
        set(value) { prefs.edit().putBoolean(K_OVERLAY_ON_OEM_DIAG, value).apply() }

    /**
     * Reactive view of [overlayOnOemDiag]. Backed by a SharedPreferences listener so every
     * collector sees the latest value immediately on subscription and on every change.
     */
    val overlayOnOemDiagFlow: Flow<Boolean> = callbackFlow {
        // Emit the current value immediately so collectors don't wait for the first change.
        trySend(overlayOnOemDiag)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == K_OVERLAY_ON_OEM_DIAG) trySend(overlayOnOemDiag)
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }.distinctUntilChanged()

    /** Coroutine-friendly writer; delegates to the property setter. */
    suspend fun setOverlayOnOemDiag(value: Boolean) {
        overlayOnOemDiag = value
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

    // ---- DX8: fast workflow state (last-good memory) ----

    /** Most recently scanned or entered VIN (17-char when known). */
    var lastVin: String?
        get() = prefs.getString(K_FAST_LAST_VIN, null)?.takeIf { it.isNotBlank() }
        set(value) { prefs.edit().putString(K_FAST_LAST_VIN, value?.trim()).apply() }

    /** Human-readable label for the transport used on the last successful link. */
    var lastTransportLabel: String?
        get() = prefs.getString(K_FAST_LAST_TRANSPORT_LABEL, null)?.takeIf { it.isNotBlank() }
        set(value) { prefs.edit().putString(K_FAST_LAST_TRANSPORT_LABEL, value?.trim()).apply() }

    /** Battery voltage (V) observed on the last successful session, when available. */
    var lastBatteryVoltage: Float?
        get() = if (prefs.contains(K_FAST_LAST_BATTERY_VOLTAGE)) {
            prefs.getFloat(K_FAST_LAST_BATTERY_VOLTAGE, Float.NaN)
        } else {
            null
        }
        set(value) {
            prefs.edit().apply {
                if (value == null) remove(K_FAST_LAST_BATTERY_VOLTAGE) else putFloat(K_FAST_LAST_BATTERY_VOLTAGE, value)
            }.apply()
        }

    /**
     * When true and ACCESS_COARSE_LOCATION is granted, LAN upload harvest manifest may include
     * last-known coarse position (rounded to 2 decimals). Default off.
     */
    var includeCoarseLocationInUpload: Boolean
        get() = prefs.getBoolean(K_INCLUDE_COARSE_LOC_UPLOAD, false)
        set(value) { prefs.edit().putBoolean(K_INCLUDE_COARSE_LOC_UPLOAD, value).apply() }

    /** Receiver PC host that last accepted an export successfully. */
    var lastReceiverHost: String?
        get() = prefs.getString(K_FAST_LAST_RECEIVER_HOST, null)?.takeIf { it.isNotBlank() }
        set(value) { prefs.edit().putString(K_FAST_LAST_RECEIVER_HOST, value?.trim()).apply() }

    /** Epoch millis of the last successful full scan; 0 when never recorded. */
    var lastSuccessfulScanAt: Long
        get() = prefs.getLong(K_FAST_LAST_SUCCESSFUL_SCAN_AT, 0L)
        set(value) { prefs.edit().putLong(K_FAST_LAST_SUCCESSFUL_SCAN_AT, value.coerceAtLeast(0L)).apply() }

    /** Bonded Bluetooth address from the last good VCI connection. */
    var lastGoodBtAddress: String?
        get() = prefs.getString(K_FAST_LAST_GOOD_BT_ADDRESS, null)?.takeIf { it.isNotBlank() }
        set(value) { prefs.edit().putString(K_FAST_LAST_GOOD_BT_ADDRESS, value?.trim()).apply() }

    /** Normalized link transport from the last good connection (`auto`, `oem_usb`, etc.). */
    var lastGoodTransport: String?
        get() = prefs.getString(K_FAST_LAST_GOOD_TRANSPORT, null)?.takeIf { it.isNotBlank() }
        set(value) { prefs.edit().putString(K_FAST_LAST_GOOD_TRANSPORT, value?.trim()?.lowercase()).apply() }

    /** Read/write the full fast-workflow cache as one snapshot. */
    var fastWorkflowState: FastWorkflowState
        get() = FastWorkflowState(
            lastVin = lastVin,
            lastTransportLabel = lastTransportLabel,
            lastBatteryVoltage = lastBatteryVoltage,
            lastReceiverHost = lastReceiverHost,
            lastSuccessfulScanAt = lastSuccessfulScanAt,
            lastGoodBtAddress = lastGoodBtAddress,
            lastGoodTransport = lastGoodTransport,
        )
        set(value) {
            lastVin = value.lastVin
            lastTransportLabel = value.lastTransportLabel
            lastBatteryVoltage = value.lastBatteryVoltage
            lastReceiverHost = value.lastReceiverHost
            lastSuccessfulScanAt = value.lastSuccessfulScanAt
            lastGoodBtAddress = value.lastGoodBtAddress
            lastGoodTransport = value.lastGoodTransport
        }

    /**
     * Home shell: [HOME_SCANNER_CONSOLE] (tile grid) or [HOME_AI_COPILOT] (chat-first).
     * Settings UI for this toggle is owned by C5; persist only here in DX1.
     */
    var homeMode: String
        get() = normalizeHomeMode(prefs.getString(K_HOME_MODE, HOME_SCANNER_CONSOLE) ?: HOME_SCANNER_CONSOLE)
        set(value) { prefs.edit().putString(K_HOME_MODE, normalizeHomeMode(value)).apply() }

    val isAiCopilotHome: Boolean
        get() = homeMode == HOME_AI_COPILOT

    // ---- Session AI cost debug (advisory display only) ----

    var lastSessionAiCostUsd: Double
        get() = Double.fromBits(prefs.getLong(K_LAST_SESSION_AI_COST_BITS, 0L))
        private set(value) {
            prefs.edit().putLong(K_LAST_SESSION_AI_COST_BITS, value.toRawBits()).apply()
        }

    var lastSessionAiEndedAt: Long
        get() = prefs.getLong(K_LAST_SESSION_AI_ENDED_AT, 0L)
        private set(value) { prefs.edit().putLong(K_LAST_SESSION_AI_ENDED_AT, value).apply() }

    var todayAiCostUsd: Double
        get() = Double.fromBits(prefs.getLong(K_TODAY_AI_COST_BITS, 0L))
        private set(value) { prefs.edit().putLong(K_TODAY_AI_COST_BITS, value.toRawBits()).apply() }

    var todayAiCostDayKey: String
        get() = prefs.getString(K_TODAY_AI_COST_DAY, "") ?: ""
        private set(value) { prefs.edit().putString(K_TODAY_AI_COST_DAY, value).apply() }

    /** Most recent ended session costs (newest first), max 5 — for avg label in Settings. */
    var recentSessionCosts: List<Double>
        get() {
            val raw = prefs.getString(K_RECENT_SESSION_COSTS, "") ?: ""
            if (raw.isBlank()) return emptyList()
            return raw.split(",").mapNotNull { it.toDoubleOrNull() }.take(5)
        }
        private set(value) {
            prefs.edit().putString(K_RECENT_SESSION_COSTS, value.take(5).joinToString(",")).apply()
        }

    fun rollTodayIfNeeded() {
        val today = aiCostDayKey()
        if (todayAiCostDayKey != today) {
            todayAiCostDayKey = today
            todayAiCostUsd = 0.0
        }
    }

    fun recordEndedSessionAiCost(totals: SessionTokenAccounting.Totals, endedAt: Long) {
        val cost = totals.estCostUsd()
        lastSessionAiCostUsd = cost
        lastSessionAiEndedAt = endedAt
        rollTodayIfNeeded()
        todayAiCostUsd = todayAiCostUsd + cost
        recentSessionCosts = (listOf(cost) + recentSessionCosts).take(5)
    }

    private fun aiCostDayKey(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    companion object {
        private fun createSecurePrefs(context: Context): SharedPreferences {
            return try {
                val master = MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()
                EncryptedSharedPreferences.create(
                    context,
                    "tcw_secure",
                    master,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
                )
            } catch (e: Exception) {
                android.util.Log.w(
                    "SettingsRepo",
                    "EncryptedSharedPreferences unavailable — falling back to plain prefs: ${e.message}",
                )
                context.getSharedPreferences("tcw_secure_fallback", Context.MODE_PRIVATE)
            }
        }

        const val HOME_SCANNER_CONSOLE = "scanner_console"
        const val HOME_AI_COPILOT = "ai_copilot"

        private fun normalizeHomeMode(raw: String): String = when (raw.lowercase()) {
            HOME_AI_COPILOT -> HOME_AI_COPILOT
            else -> HOME_SCANNER_CONSOLE
        }
        private const val K_API_KEY = "claude_api_key"
        private const val K_MODEL = "claude_model"
        private const val K_AUTONOMOUS = "autonomous_actuation"
        private const val K_AUTOSTART_VIN = "autostart_on_vin"
        private const val K_KILL = "kill_switch"
        private const val K_REQUIRE_APPROVAL = "require_approval"
        private const val K_SPEAK = "speak_enabled"
        private const val K_VOICE = "voice_enabled"
        private const val K_DIRECT_VCI = "direct_vci_experimental"
        private const val K_NATIVE_OBD_EXPERIMENTAL = "native_obd_experimental"
        private const val K_NATIVE_OBD_USE_VCI = "native_obd_use_vci"
        private const val K_LAUNCH_PLAN_A_BRIDGE_ENABLED = "launch_plan_a_bridge_enabled"
        private const val K_PLANB_BODY_READ = "planb_body_read"
        private const val K_PLANB_GATEWAY_REPLAY = "planb_gateway_replay"
        private const val K_PLANB_GATEWAY_SESSION_REUSE = "planb_gateway_session_reuse"
        private const val K_PLANB_CODING = "planb_coding"
        private const val K_PLANB_IMMO_INFO = "planb_immo_info"
        private const val K_PLANB_PROGRAMMING = "planb_programming"
        private const val K_TIER4_TRIAL_ACCEPTED_AT = "tier4_trial_accepted_at_ms"
        private const val K_TIER4_TRIAL_EXPIRES_AT = "tier4_trial_expires_at_ms"
        private const val K_TIER4_TRIAL_MARQUES = "tier4_trial_marques"
        private const val K_TIER4_TRIAL_MARQUE_ID = "tier4_trial_marque_id"
        private const val K_TIER4_FULL_LICENSE_ENABLED = "tier4_full_license_enabled"
        private const val K_TIER4_LICENSE_ISSUED_AT = "tier4_license_issued_at_ms"
        private const val K_TIER4_LICENSE_MARQUES = "tier4_license_marques"
        const val TIER4_TRIAL_DURATION_MS = 14L * 24L * 60L * 60L * 1000L
        const val TIER4_TRIAL_MARQUES_DEFAULT = "ford,jeep,dodge,ram,chrysler"

        /**
         * djb2 hash of normalized operator code `TCW-T4-OPERATOR` — see [redeemTier4LicenseCode].
         * Exposed for unit tests only.
         */
        internal const val TIER4_LICENSE_CODE_HASH: Long = -6704179516806180895L

        internal fun normalizeTier4LicenseCode(raw: String): String =
            raw.trim().uppercase(Locale.US)

        internal fun tier4LicenseCodeHash(normalized: String): Long {
            var h = 5381L
            for (c in normalized) {
                h = ((h shl 5) + h) + c.code
            }
            return h
        }
        private const val K_SKREEM_TRIAL_ACCEPTED_AT = "skreem_trial_accepted_at_ms"
        private const val K_SKREEM_TRIAL_EXPIRES_AT = "skreem_trial_expires_at_ms"
        private const val K_SKREEM_TRIAL_MARQUES = "skreem_trial_marques"
        private const val K_SKREEM_TRIAL_MARQUE_ID = "skreem_trial_marque_id"
        private const val K_SKREEM_IMMO_INFO_ENABLED = "skreem_immo_info_enabled"
        const val SKREEM_TRIAL_DURATION_MS = 14L * 24L * 60L * 60L * 1000L
        const val SKREEM_TRIAL_MARQUES_DEFAULT = "jeep,dodge,ram,chrysler"
        private const val K_TIER_SAFETY_FIRST_CONNECT = "tier_safety_first_connect"
        private const val K_HAS_COMPLETED_FIRST_CONNECT = "has_completed_first_connect"
        private const val K_TIER_SNAPSHOT_AT_CONNECT = "tier_snapshot_at_connect"
        private const val K_TIER_SESSION_UNLOCK_MASK = "tier_session_unlock_mask"

        const val TIER_BIT_0: Int = 1 shl 0
        const val TIER_BIT_1: Int = 1 shl 1
        const val TIER_BIT_2: Int = 1 shl 2
        const val TIER_BIT_3: Int = 1 shl 3
        const val TIER_BIT_4: Int = 1 shl 4
        private const val K_VCI_TRANSPORT = "vci_transport_mode"
        private const val K_LINK_TRANSPORT = "link_transport"
        private const val K_BT_TRANSPORT_ENABLED = "bluetooth_transport_enabled"
        private const val K_BT_PAIRING_HINT_SEEN = "bluetooth_pairing_hint_seen"
        private const val K_VCI_BT_ADDRESS = "vci_bt_address"
        private const val K_VCI_HDR0 = "vci_header_byte0"
        private const val K_VCI_HDR1 = "vci_header_byte1"
        private const val K_VCI_HEX = "vci_use_hex_encoding"
        private const val K_VCI_PROTO_OK = "vci_protocol_confirmed"
        private const val K_NOTES = "agent_notes"
        private const val K_THEME = "theme_mode"
        private const val K_WIZARD = "wizard_complete"
        private const val K_SETUP_LIVE_COMPLETE = "setup_live_complete"
        private const val K_OVERLAY_ON_OEM_DIAG = "overlay_on_oem_diag"   // A6
        private const val K_RECEIVER_PC_HOST = "receiver_pc_host"
        private const val K_RECEIVER_PC_PORT = "receiver_pc_port"
        private const val K_USE_MULTIPART_FALLBACK = "use_multipart_fallback"
        private const val K_TRANSFER_DELIVERY_MODE = "transfer_delivery_mode"
        private const val K_TRANSFER_DROP_URL = "transfer_drop_url"
        private const val K_SHOP_DESK_INGEST_ENABLED = "shop_desk_ingest_enabled"
        private const val K_SHOP_DESK_USE_PRODUCTION = "shop_desk_use_production"
        private const val K_SHOP_DESK_INGEST_URL = "shop_desk_ingest_url"
        private const val K_SHOP_DESK_LAN_BROADCAST_ENABLED = "shop_desk_lan_broadcast_enabled"
        private const val K_SHOP_DESK_LAN_REPORTING_ENABLED = "shop_desk_lan_reporting_enabled"
        private const val K_SETUP_DEVICE_ID = "setup_device_id"
        private const val K_SHOP_DESK_LAN_BROADCAST_AT = "shop_desk_lan_broadcast_at_ms"
        private const val K_LAST_OEM_DIAG_CONNECT_BLOCK = "last_oem_diag_connect_block"
        private const val K_LAST_OEM_DIAG_CONNECT_BLOCK_AT = "last_oem_diag_connect_block_at"
        private const val K_LAST_CONNECT_ATTEMPT = "last_connect_attempt"
        private const val K_LAST_CONNECT_ATTEMPT_AT = "last_connect_attempt_at"
        private const val K_LAST_CONNECT_ATTEMPT_OK = "last_connect_attempt_ok"
        private const val K_LAST_WORKING_CONNECT_AT = "last_working_connect_at_ms"
        private const val K_LAST_WORKING_USB_DEVICE_ID = "last_working_usb_device_id"
        private const val K_OVERLAY_ONBOARDING_SEEN = "overlay_onboarding_seen"   // C2
        private const val K_EMERGENCY_DISMISS_HINT_SEEN = "emergency_dismiss_hint_seen"   // D1
        private const val K_FAST_LAST_VIN = "fast_last_vin"                               // DX8
        private const val K_FAST_LAST_TRANSPORT_LABEL = "fast_last_transport_label"       // DX8
        private const val K_FAST_LAST_BATTERY_VOLTAGE = "fast_last_battery_voltage"       // DX8
        private const val K_FAST_LAST_RECEIVER_HOST = "fast_last_receiver_host"           // DX8
        private const val K_INCLUDE_COARSE_LOC_UPLOAD = "include_coarse_location_upload" // harvest batch
        private const val K_FAST_LAST_SUCCESSFUL_SCAN_AT = "fast_last_successful_scan_at" // DX8
        private const val K_FAST_LAST_GOOD_BT_ADDRESS = "fast_last_good_bt_address"       // DX8
        private const val K_FAST_LAST_GOOD_TRANSPORT = "fast_last_good_transport"         // DX8
        private const val K_HOME_MODE = "home_mode"
        private const val K_LAST_SESSION_AI_COST_BITS = "last_session_ai_cost_bits"
        private const val K_LAST_SESSION_AI_ENDED_AT = "last_session_ai_ended_at"
        private const val K_TODAY_AI_COST_BITS = "today_ai_cost_bits"
        private const val K_TODAY_AI_COST_DAY = "today_ai_cost_day"
        private const val K_RECENT_SESSION_COSTS = "recent_session_costs"
        private const val DEFAULT_MODEL = "claude-sonnet-4-6"
        const val DEFAULT_SHOP_DESK_INGEST_URL = "http://localhost:8791/api/ingest/session"
        const val DEFAULT_SHOP_DESK_INGEST_URL_PROD =
            "https://desk.rickyscontrolcenter.com/api/ingest/session"

        fun isProductionDeskUrl(url: String): Boolean {
            val trimmed = url.trim().lowercase()
            return trimmed.startsWith("https://desk.rickyscontrolcenter.com")
        }

        /** Single source of truth for DeepSeek wishlist settings keys. */
        private object DeepSeekSettingsKeys {
            const val BLE_ELM_STANDALONE = "deepseek_ble_elm_standalone"
            const val GPS_ENABLED = "deepseek_gps_enabled"
            const val OCR_ENABLED = "deepseek_ocr_enabled"
            const val GATEWAY_POOL_ENABLED = "deepseek_gateway_pool_enabled"
            const val STREAMING_ENABLED = "deepseek_streaming_enabled"
        }

        const val DEFAULT_AGENT_NOTES = """About this app
==============
You (the agent) live inside Together Car Works on the technician's OEM diagnostic tablet/PROS/V+ tablet.
The technician uses you to drive the the OEM diagnostic app diagnostic app — you see its UI via Android's
AccessibilityService and operate it via the tools you've been given.

The technician owns this tablet and this VCI. You are NOT operating on a customer's behalf;
you are the tech's copilot. Treat the tech as an experienced automotive professional — be
concise, technical, and decisive. Do not over-explain basics they already know.

What you can do
===============
- read_screen / tap / type / scroll / back / wait_for — drive the OEM diagnostic app UI
- capture_screenshot — for graphical screens (gauges, charts) the accessibility tree misses
- vin_lookup — NHTSA decode + recalls for a 17-char VIN. Always call when you first see one.
- repair_info_lookup — separate Claude call for DTC causes / tests / TSBs / wiring hints
- propose_actuation — only required when the per-action approval mode is on
- finish_session — when the goal is met, with a structured summary

What you can't do
=================
- Bypass OEM licensing / subscriptions. Some advanced functions (key programming on
  newer cars, online ECU programming) require the tech's OEM diagnostic subscription be active.
- Talk to the VCI dongle directly. You always go through the OEM diagnostic app.
- Modify the OEM diagnostic app. You drive it as a user would.

Operating principles
====================
- Always read_screen before acting. Don't tap from memory.
- Take ONE action at a time. Verify with read_screen before the next.
- When a VIN is on screen, call vin_lookup early so you know the vehicle.
- When you see an unfamiliar DTC, call repair_info_lookup before recommending repairs.
- The tech has fully-autonomous actuation enabled by default. Don't ask before each test;
  log a one-line reason in your reasoning instead.
- If a screen looks ambiguous (graphical gauges, charts), call capture_screenshot.
- If the OEM diagnostic app stops responding for 8+ seconds, press back() and try a different path.
- When the goal is met, call finish_session with the structured summary. Don't keep going.

History — what this app has been through
=========================================
- Built as an Android Kotlin/Compose wrapper that uses AccessibilityService to operate the
  OEM diagnostic app. Architecture: AgentRunner is a Claude tool-use loop.
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

