package com.caseforge.scanner.oem

import com.caseforge.scanner.transfer.VehicleDatabasePathResolver
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

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

    /** Resolves the best data root and runs a shallow inventory. */
    fun scan(): OemDataSummary {
        val started = System.currentTimeMillis()
        val inventory = runCatching { VehicleDatabasePathResolver.scan() }
            .getOrElse { err ->
                val summary = OemDataSummary.error(
                    message = "Could not locate vehicle data: ${err.message ?: "unknown"}",
                    durationMs = System.currentTimeMillis() - started,
                )
                lastSummary = summary
                return summary
            }

        val summary = if (!inventory.hasData) {
            OemDataSummary.notFound(
                rootsChecked = inventory.pathsTried.size,
                durationMs = System.currentTimeMillis() - started,
            )
        } else {
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

    /** Scan an explicit root (tests or dev hooks) without touching the resolver cache. */
    fun scanRoot(root: File): OemDataSummary {
        val summary = OemDataMiner.mine(root).copy(rootsChecked = 1)
        lastSummary = summary
        return summary
    }

    fun toJson(summary: OemDataSummary = lastSummary ?: scan()): String =
        json.encodeToString(summary)

    fun fromJson(raw: String): OemDataSummary =
        json.decodeFromString(raw)
}
