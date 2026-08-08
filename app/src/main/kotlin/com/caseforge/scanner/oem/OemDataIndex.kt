package com.caseforge.scanner.oem

import android.content.Context
import com.caseforge.scanner.planb.MarqueWedgeConfig
import com.caseforge.scanner.transfer.VehicleDatabasePathResolver
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.Locale

/**
 * Entry point for OEM vehicle data discovery — used by Diagnostics and AI Copilot.
 * Locates storage via [VehicleDatabasePathResolver]; surfaces only neutral [OemDataSummary] fields.
 */
object OemDataIndex {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Volatile
    var lastSummary: OemDataSummary? = null
        private set

    @Volatile
    private var lastScanRoot: File? = null

    fun loadBundle(context: Context): OemDecompileBundle? =
        OemDecompileBundleLoader.load(context)

    /** Resolves the best data root and runs a shallow inventory. */
    fun scan(): OemDataSummary {
        val started = System.currentTimeMillis()
        val inventory = runCatching { VehicleDatabasePathResolver.scan() }
            .getOrElse { err ->
                lastScanRoot = null
                val summary = OemDataSummary.error(
                    message = "Could not locate vehicle data: ${err.message ?: "unknown"}",
                    durationMs = System.currentTimeMillis() - started,
                )
                lastSummary = summary
                return summary
            }

        val summary = if (!inventory.hasData) {
            lastScanRoot = null
            OemDataSummary.notFound(
                rootsChecked = inventory.pathsTried.size,
                durationMs = System.currentTimeMillis() - started,
            )
        } else {
            lastScanRoot = inventory.root
            val mined = OemDataMiner.mine(inventory.root)
            mined.copy(
                rootsChecked = inventory.pathsTried.size,
                scanDurationMs = System.currentTimeMillis() - started,
                notes = mined.notes + "Resolver matched ${inventory.fileCount} file(s) in store.",
            )
        }

        lastSummary = summary
        return summary
    }

    /**
     * Like [scan], then merges offline decompile metadata (when present) into summary fields and notes.
     */
    fun scanWithBundle(context: Context): OemDataSummary {
        val bundle = OemDecompileBundleLoader.load(context)
        val summary = scan()
        if (bundle == null) return summary
        val matched = lastScanRoot?.let { matchLocalBrand(bundle, it) }
        val metaNote =
            "Reference metadata bundle v${bundle.bundleVersion} (${bundle.generatedAt.take(10)})."
        return summary.copy(
            decompileBrandCount = bundle.summary.brandCount,
            decompileCatalogTotals = bundle.summary.catalogTotals,
            matchedLocalBrand = matched,
            notes = summary.notes + metaNote,
        )
    }

    fun enrichedDisplayLines(
        summary: OemDataSummary,
        context: Context? = null,
        wedgeVinHint: String? = null,
    ): List<String> {
        val lines = summary.displayLines().toMutableList()
        context?.let { ctx ->
            tier4PartnerOnlySummaryLine(ctx, wedgeVinHint)?.let { wedgeLine ->
                val idx = lines.indexOfFirst { it.startsWith("Vehicle data store:") }
                    .takeIf { it >= 0 } ?: 0
                lines.add(idx + 1, wedgeLine)
            }
        }
        val bundle = OemDecompileBundleLoader.getCachedOrNull()
            ?: context?.let { OemDecompileBundleLoader.load(it) }
        val dbCount = bundle?.sqliteDatabases?.size ?: 0
        if (dbCount > 0) {
            lines.add("Reference SQL layouts: $dbCount databases (metadata)")
        }
        return lines
    }

    /**
     * Extra line for vehicle summaries when [vinHint] resolves to a wedge [MarquePlatformCard]
     * and [MarqueWedgeConfig.tierEnabled] is true for tier 4.
     */
    internal fun tier4PartnerOnlySummaryLine(context: Context, vinHint: String?): String? {
        val v = vinHint?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val matrix = MarqueWedgeConfig.load(context) ?: return null
        val card = MarqueWedgeConfig.findCardForVin(v, matrix) ?: return null
        if (!MarqueWedgeConfig.tierEnabled(card, 4, matrix)) return null
        return "Tier 4: partner only — ${card.marque} ${card.model}"
    }

    /** Scan an explicit root (tests or dev hooks) without touching the resolver cache. */
    fun scanRoot(root: File): OemDataSummary {
        lastScanRoot = if (root.isDirectory) root else null
        val summary = OemDataMiner.mine(root).copy(rootsChecked = 1)
        lastSummary = summary
        return summary
    }

    fun toJson(summary: OemDataSummary = lastSummary ?: scan()): String =
        json.encodeToString(summary)

    fun fromJson(raw: String): OemDataSummary =
        json.decodeFromString(raw)

    private fun matchLocalBrand(bundle: OemDecompileBundle, root: File): String? {
        val candidates = listOf(
            File(root, "DIAGNOSTIC${File.separator}VEHICLES"),
            File(root, "oem${File.separator}DIAGNOSTIC${File.separator}VEHICLES"),
        ).filter { it.isDirectory }
        if (candidates.isEmpty()) return null
        val names = bundle.brands.map { it.brand.uppercase(Locale.US) }.toSet()
        var best: Pair<String, Int>? = null
        for (dir in candidates) {
            val kids = dir.listFiles()?.filter { it.isDirectory } ?: continue
            for (child in kids) {
                val n = child.name.uppercase(Locale.US)
                if (n !in names) continue
                var cnt = 0
                child.walkTopDown().maxDepth(14).onFail { _, _ -> }.forEach { f ->
                    if (f.isFile) cnt++
                }
                if (best == null || cnt > best.second) best = n to cnt
            }
        }
        return best?.first
    }
}
