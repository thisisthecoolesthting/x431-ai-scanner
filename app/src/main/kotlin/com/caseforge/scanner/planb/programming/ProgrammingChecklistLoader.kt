package com.caseforge.scanner.planb.programming

import android.content.Context
import com.caseforge.scanner.planb.PlanbMarque
import com.caseforge.scanner.update.AssetOverlay
import com.caseforge.scanner.planb.immo.SkreemModule
import java.util.concurrent.ConcurrentHashMap
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/**
 * Loads `planb/programming-checklist-<marque>.json` from assets.
 * For Stellantis marques (Jeep, Dodge), merges [SKREEM_ASSET] runbook rows (Tier 4 partner/manual only).
 */
object ProgrammingChecklistLoader {

    const val ASSET_DIR = "planb"
    const val SKREEM_ASSET = "$ASSET_DIR/programming-checklist-skreem.json"
    const val IMMO_INFO_PREFIX = "$ASSET_DIR/immo-info-"

    /** Bundled Tier 3 immo-info asset path — aligned with [com.caseforge.scanner.planb.immo.ImmoInfoService.assetPath]. */
    fun immoInfoAssetPath(marque: PlanbMarque): String =
        "$IMMO_INFO_PREFIX${marque.id}.json"

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
    }
    private val checklistCache = ConcurrentHashMap<PlanbMarque, ProgrammingChecklist?>()
    private val skreemOverlayCache = object {
        @Volatile
        var value: ProgrammingChecklist? = null
        @Volatile
        var loaded: Boolean = false
    }

    fun assetPath(marque: PlanbMarque): String =
        "$ASSET_DIR/programming-checklist-${marque.id}.json"

    fun load(context: Context, marque: PlanbMarque): ProgrammingChecklist? {
        val appContext = context.applicationContext
        val base = loadBase(appContext, marque)
        if (!SkreemModule.isStellantisMarque(marque)) return base
        val skreem = loadSkreemOverlay(appContext) ?: return base
        return mergeChecklists(base, skreem, marque)
    }

    fun loadSkreemOverlay(context: Context): ProgrammingChecklist? =
        if (skreemOverlayCache.loaded) {
            skreemOverlayCache.value
        } else {
            val loaded = loadRaw(context.applicationContext, SKREEM_ASSET)
            skreemOverlayCache.value = loaded
            skreemOverlayCache.loaded = true
            loaded
        }

    fun preload(context: Context, marque: PlanbMarque) {
        val appContext = context.applicationContext
        loadBase(appContext, marque)
        if (SkreemModule.isStellantisMarque(marque)) {
            loadSkreemOverlay(appContext)
        }
    }

    private fun loadBase(context: Context, marque: PlanbMarque): ProgrammingChecklist? =
        if (checklistCache.containsKey(marque)) {
            checklistCache[marque]
        } else {
            val loaded = loadRaw(context, assetPath(marque))
            checklistCache[marque] = loaded
            loaded
        }

    private fun loadRaw(context: Context, path: String): ProgrammingChecklist? = runCatching {
        val text = AssetOverlay.readText(context, path) ?: return@runCatching null
        json.decodeFromString<ProgrammingChecklist>(text)
    }.getOrNull()

    private fun mergeChecklists(
        base: ProgrammingChecklist?,
        skreem: ProgrammingChecklist,
        marque: PlanbMarque,
    ): ProgrammingChecklist {
        val baseEntries = base?.entries.orEmpty()
        val skreemEntries = skreem.entries
        val mergedIds = baseEntries.map { it.id }.toMutableSet()
        val appended = skreemEntries.filter { it.id.isNotBlank() && mergedIds.add(it.id) }
        return ProgrammingChecklist(
            schemaVersion = base?.schemaVersion ?: skreem.schemaVersion,
            marqueId = base?.marqueId?.takeIf { it.isNotBlank() } ?: marque.id,
            entries = baseEntries + appended,
            capabilityId = skreem.capabilityId ?: base?.capabilityId,
        )
    }

    internal fun clearCacheForTest() {
        checklistCache.clear()
        skreemOverlayCache.value = null
        skreemOverlayCache.loaded = false
    }
}
