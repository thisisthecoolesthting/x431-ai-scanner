package com.caseforge.scanner.planb.coding

import android.content.Context
import com.caseforge.scanner.planb.PlanbMarque
import com.caseforge.scanner.update.AssetOverlay
import java.util.concurrent.ConcurrentHashMap
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/**
 * Loads `planb/coding-checklist-<marque>.json` from assets.
 */
object CodingChecklistLoader {

    const val ASSET_DIR = "planb"

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
    }
    private val checklistCache = ConcurrentHashMap<PlanbMarque, CodingChecklist?>()

    fun assetPath(marque: PlanbMarque): String =
        "$ASSET_DIR/coding-checklist-${marque.id}.json"

    fun load(context: Context, marque: PlanbMarque): CodingChecklist? =
        if (checklistCache.containsKey(marque)) {
            checklistCache[marque]
        } else {
            val loaded = runCatching {
                val text = AssetOverlay.readText(context, assetPath(marque)) ?: return@runCatching null
                json.decodeFromString<CodingChecklist>(text)
            }.getOrNull()
            checklistCache[marque] = loaded
            loaded
        }

    fun preload(context: Context, marque: PlanbMarque) {
        load(context.applicationContext, marque)
    }

    internal fun clearCacheForTest() {
        checklistCache.clear()
    }
}
