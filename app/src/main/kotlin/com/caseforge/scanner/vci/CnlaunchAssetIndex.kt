package com.caseforge.scanner.vci

import android.content.Context
import android.util.Log
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

/**
 * Offline DTC descriptions and menu trees from exported cnlaunch data (Stream E).
 */
object CnlaunchAssetIndex {

    private const val TAG = "CnlaunchAssetIndex"
    private val json = Json { ignoreUnknownKeys = true }

    private var dtcByOem: Map<String, Map<String, String>> = emptyMap()
    private var menuTrees: Map<String, List<String>> = emptyMap()

    @Volatile
    var loaded: Boolean = false
        private set

    fun load(context: Context) {
        val dir = File(context.filesDir, "cnlaunch-assets")
        val dtcFile = File(dir, "dtc-descriptions.json")
        val menuFile = File(dir, "menu-trees.json")
        dtcByOem = if (dtcFile.isFile) {
            parseDtcFile(dtcFile.readText())
        } else {
            parseDtcAsset(context, "cnlaunch/dtc-descriptions.json")
        }
        menuTrees = if (menuFile.isFile) {
            parseMenuFile(menuFile.readText())
        } else {
            parseMenuAsset(context, "cnlaunch/menu-trees.json")
        }
        loaded = true
        Log.i(TAG, "cnlaunch assets: dtc_oems=${dtcByOem.size} menus=${menuTrees.size}")
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
