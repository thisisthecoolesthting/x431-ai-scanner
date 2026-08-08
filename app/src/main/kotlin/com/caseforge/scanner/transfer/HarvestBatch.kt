package com.caseforge.scanner.transfer

import com.caseforge.scanner.agent.discovery.DiscoveryReport
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class TabletBuildHarvest(
    val model: String,
    val sdkInt: Int,
    val fingerprintSanitized: String,
)

@Serializable
data class BatteryHarvestSnapshot(
    /** 0–100 when known. */
    val chargePercent: Float? = null,
    /** [android.os.BatteryManager] status code from sticky [android.content.Intent.ACTION_BATTERY_CHANGED]. */
    val status: Int? = null,
    val isCharging: Boolean? = null,
    /** [android.os.BatteryManager.EXTRA_PLUGGED]. */
    val plugged: Int? = null,
    val health: Int? = null,
    /** Battery temperature in tenths °C. */
    val temperatureTenthsC: Int? = null,
    /** Tablet-side reported voltage in mV when present. */
    val tabletVoltageMv: Int? = null,
    /** Last vehicle bus/battery voltage from [com.caseforge.scanner.data.SettingsRepo.lastBatteryVoltage]. */
    val vehicleVoltageVolts: Float? = null,
)

@Serializable
data class CoarseLocationHarvest(
    val latitudeRounded: Double,
    val longitudeRounded: Double,
)

/**
 * Sidecar manifest included in every LAN upload zip under `harvest-batch/manifest.json`.
 * Driver / adapter discovery is always attached when the operator harvests or sends data.
 */
@Serializable
data class HarvestBatchManifest(
    val schemaVersion: Int = SCHEMA_VERSION,
    val timestampMs: Long,
    val versionCode: Int,
    val versionName: String,
    val vehicleProfileId: String,
    val discoveryReport: DiscoveryReport,
    val tabletBuild: TabletBuildHarvest? = null,
    val battery: BatteryHarvestSnapshot? = null,
    /** Present only when operator opt-in is on and coarse location permission is granted. */
    val coarseLocation: CoarseLocationHarvest? = null,
    /** Boolean flags per X431-family package (allowlist only). */
    val x431PackagesInstalled: Map<String, Boolean> = emptyMap(),
) {
    companion object {
        const val SCHEMA_VERSION = 2
        const val ZIP_ENTRY = "harvest-batch/manifest.json"

        private val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            prettyPrint = true
        }

        fun toJsonBytes(manifest: HarvestBatchManifest): ByteArray =
            json.encodeToString(manifest).toByteArray(Charsets.UTF_8)
    }
}

data class HarvestBatch(
    val manifest: HarvestBatchManifest,
) {
    fun asZipSidecars(): Map<String, ByteArray> = mapOf(
        HarvestBatchManifest.ZIP_ENTRY to HarvestBatchManifest.toJsonBytes(manifest),
    )
}
