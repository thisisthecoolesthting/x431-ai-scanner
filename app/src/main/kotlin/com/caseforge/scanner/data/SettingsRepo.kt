package com.caseforge.scanner.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.caseforge.scanner.BuildConfig

/**
 * Stores secrets (Claude API key) in EncryptedSharedPreferences, and non-secret prefs
 * (model choice, kill switch state, autonomous mode toggle) in plain SharedPreferences.
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
        get() = prefs.getString(K_MODEL, "claude-sonnet-4-6").orEmpty()
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

    companion object {
        private const val K_API_KEY = "claude_api_key"
        private const val K_MODEL = "claude_model"
        private const val K_AUTONOMOUS = "autonomous_actuation"
        private const val K_AUTOSTART_VIN = "autostart_on_vin"
        private const val K_KILL = "kill_switch"
        private const val K_REQUIRE_APPROVAL = "require_approval"
    }
}
