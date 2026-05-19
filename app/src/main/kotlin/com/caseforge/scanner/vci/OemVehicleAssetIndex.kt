package com.caseforge.scanner.vci

import android.content.Context
import android.util.Log
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

/**
 * Offline DTC descriptions and menu trees from exported OEM vehicle database (Stream E).
 */
object OemVehicleAssetIndex {

    private const val TAG = "OemVehicleAssetIndex"
    private const val ASSET_DIR = "oem-vehicle-db"
    private val json = Json { ignoreUnknownKeys = true }

    private var dtcByOem: Map<String, Map<String, String>> = emptyMap()
    private var menuTrees: Map<String, List<String>> = emptyMap()

    @Volatile
    var loaded: Boolean = false
        private set

    fun load(context: Context) {
        val dir = File(context.filesDir, "oem-vehicle-assets")
        val dtcFile = File(dir, "dtc-descriptions.json")
        val menuFile = File(dir, "menu-trees.json")
        dtcByOem = if (dtcFile.isFile) {
            parseDtcFile(dtcFile.readText())
        } else {
            parseDtcAsset(context, "$ASSET_DIR/dtc-descriptions.json")
        }
        menuTrees = if (menuFile.isFile) {
            parseMenuFile(menuFile.readText())
        } else {
            parseMenuAsset(context, "$ASSET_DIR/menu-trees.json")
        }
        loaded = true
        Log.i(TAG, "oem vehicle assets: dtc_oems=${dtcByOem.size} menus=${menuTrees.size}")
    }

    fun describeDtc(oem: String, code: String): String? {
        val normalized = code.uppercase().removePrefix("PENDING:")
        return dtcByOem[oem]?.get(normalized)
            ?: dtcByOem["OBD-II"]?.get(normalized)
            ?: dtcByOem["*"]?.get(normalized)
    }

    fun menuPath(capabilityId: String): List<String>? = menuTrees[capabilityId]

    private fun parseDtcAsset(context: Context, assetPath: String): Map<String, Map<String, String>> =
        runCatching {
            context.assets.open(assetPath).bufferedReader().use { parseDtcFile(it.readText()) }
        }.getOrElse { emptyMap() }

    private fun parseMenuAsset(context: Context, assetPath: String): Map<String, List<String>> =
        runCatching {
            context.assets.open(assetPath).bufferedReader().use { parseMenuFile(it.readText()) }
        }.getOrElse { emptyMap() }

    internal fun parseDtcFile(raw: String): Map<String, Map<String, String>> {
        if (raw.isBlank()) return emptyMap()
        val root = json.parseToJsonElement(raw).jsonObject
        return root.mapValues { (_, v) ->
            v.jsonObject.mapValues { it.value.jsonPrimitive.content }
        }
    }

    internal fun parseMenuFile(raw: String): Map<String, List<String>> {
        if (raw.isBlank()) return emptyMap()
        val root = json.parseToJsonElement(raw).jsonObject
        return root.mapValues { (_, v) ->
            v.jsonArray.map { it.jsonPrimitive.content }
        }
    }
}
