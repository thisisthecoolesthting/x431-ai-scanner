package com.caseforge.scanner.oem

import android.content.Context
import com.caseforge.scanner.update.AssetOverlay
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/**
 * Loads static OEM decompile metadata from assets ([ASSET_NAME]); safe to call on a background thread.
 */
object OemDecompileBundleLoader {

    const val ASSET_NAME = "oem-decompile-bundle.json"

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
    }

    @Volatile
    private var cached: OemDecompileBundle? = null

    /** Parses assets bundle or returns null if missing / invalid. */
    fun load(context: Context): OemDecompileBundle? {
        cached?.let { return it }
        val parsed = runCatching {
            val text = AssetOverlay.readText(context, ASSET_NAME)
                ?: return@runCatching null
            json.decodeFromString<OemDecompileBundle>(text)
        }.getOrNull()
        cached = parsed
        return parsed
    }

    fun invalidateCache() {
        cached = null
    }

    fun getCachedOrNull(): OemDecompileBundle? = cached

    fun brands(): List<DecompileBrandEntry> = cached?.brands.orEmpty()

    fun findBrand(name: String): DecompileBrandEntry? =
        cached?.brands?.firstOrNull { it.brand.equals(name, ignoreCase = true) }

    fun sqliteSchemas(): List<DecompileSqliteDatabaseSummary> =
        cached?.sqliteDatabases.orEmpty()

    fun formatHints(): Map<String, String> = cached?.formatHints.orEmpty()
}
