package com.caseforge.scanner.agent.discovery

import android.content.Context
import com.caseforge.scanner.update.AssetOverlay
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/**
 * Loads bundled vehicle connection profiles from `planb/vehicle-profiles/{id}.json`.
 */
object VehicleProfileLoader {

    const val ASSET_DIR = "planb/vehicle-profiles"
    const val DEFAULT_WINDSTAR_ID = "ford-windstar-2000"

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
    }

    fun load(context: Context, profileId: String): VehicleProfile? = runCatching {
        val path = "$ASSET_DIR/$profileId.json"
        val text = AssetOverlay.readText(context, path) ?: return@runCatching null
        json.decodeFromString<VehicleProfile>(text)
    }.getOrNull()

    fun listProfileIds(context: Context): List<String> {
        val fromAssets = runCatching {
            context.assets.list(ASSET_DIR)?.filter { it.endsWith(".json") }
                ?.map { it.removeSuffix(".json") }
                ?: emptyList()
        }.getOrDefault(emptyList())
        val overlayDir = AssetOverlay.overlayFile(context, ASSET_DIR)
        val fromOverlay = if (overlayDir.isDirectory) {
            overlayDir.listFiles()?.filter { it.extension == "json" }
                ?.map { it.nameWithoutExtension }
                ?: emptyList()
        } else {
            emptyList()
        }
        return (fromAssets + fromOverlay).distinct().sorted()
    }

    /**
     * Pick bundled profile for [vin]: wedge card id when a matching profile JSON exists,
     * else [DEFAULT_WINDSTAR_ID].
     */
    fun profileIdForVin(context: Context, vin: String?): String {
        val v = vin?.trim()?.takeIf { it.isNotEmpty() } ?: return DEFAULT_WINDSTAR_ID
        val cardId = com.caseforge.scanner.planb.MarqueWedgeConfig.findCardForVin(context, v)?.id
            ?: return DEFAULT_WINDSTAR_ID
        return if (load(context, cardId) != null) cardId else DEFAULT_WINDSTAR_ID
    }
}
