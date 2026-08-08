package com.caseforge.scanner.agent.accessibility

import android.content.Context
import com.caseforge.scanner.update.AssetOverlay
import kotlinx.serialization.json.Json

/**
 * Loads [AccessibilityBidiConfig] from bundled or live-update overlay assets.
 */
object AccessibilityBidiLoader {

    const val ASSET_PATH = "planb/accessibility-bidi-v1.json"

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
    }

    @Volatile
    private var cached: AccessibilityBidiConfig? = null

    fun load(context: Context): AccessibilityBidiConfig? {
        cached?.let { return it }
        val loaded = runCatching {
            val text = AssetOverlay.readText(context.applicationContext, ASSET_PATH)
                ?: return@runCatching null
            json.decodeFromString<AccessibilityBidiConfig>(text)
        }.getOrNull()
        cached = loaded
        return loaded
    }

    fun resolveTestId(config: AccessibilityBidiConfig, rawTestId: String): String {
        val trimmed = rawTestId.trim()
        if (trimmed.isEmpty()) return trimmed
        config.aliases[trimmed]?.let { return it }
        val normalized = trimmed.lowercase().replace(' ', '_').replace('-', '_')
        config.aliases[normalized]?.let { return it }
        if (normalized in config.tests) return normalized
        return trimmed
    }

    internal fun clearCacheForTest() {
        cached = null
    }
}
