package com.caseforge.scanner.transfer

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.BatteryManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.caseforge.scanner.BuildConfig
import com.caseforge.scanner.agent.X431InstalledProbe
import com.caseforge.scanner.agent.discovery.DiscoveryReport
import com.caseforge.scanner.agent.discovery.TabletHardwareDiscoveryAgent
import com.caseforge.scanner.agent.discovery.VehicleProfileLoader
import com.caseforge.scanner.data.SettingsRepo
import kotlin.math.round

/**
 * Collects tablet USB/BT driver discovery into a [HarvestBatch] for the LAN upload zip.
 */
object TabletDataHarvester {

    fun build(
        context: Context,
        vehicleProfileId: String = VehicleProfileLoader.DEFAULT_WINDSTAR_ID,
        discoveryReport: DiscoveryReport? = null,
        settings: SettingsRepo,
    ): HarvestBatch {
        val agent = TabletHardwareDiscoveryAgent(context)
        val report = discoveryReport ?: agent.scan(vehicleProfileId)
        val profileId = report.windstarProfileId?.takeIf { it.isNotBlank() } ?: vehicleProfileId
        val manifest = HarvestBatchManifest(
            timestampMs = System.currentTimeMillis(),
            versionCode = BuildConfig.VERSION_CODE,
            versionName = BuildConfig.VERSION_NAME,
            vehicleProfileId = profileId,
            discoveryReport = report,
            tabletBuild = collectTabletBuild(),
            battery = collectBatterySnapshot(context, settings),
            coarseLocation = maybeCoarseLocation(context, settings),
            x431PackagesInstalled = X431InstalledProbe.installedFlags(context.packageManager),
        )
        return HarvestBatch(manifest)
    }

    fun resolveProfileId(context: Context, vinHint: String?, explicitProfileId: String?): String {
        explicitProfileId?.takeIf { it.isNotBlank() }?.let { return it }
        vinHint?.takeIf { it.isNotBlank() }?.let { vin ->
            VehicleProfileLoader.profileIdForVin(context, vin)?.let { return it }
        }
        return VehicleProfileLoader.DEFAULT_WINDSTAR_ID
    }

    private fun collectTabletBuild(): TabletBuildHarvest =
        TabletBuildHarvest(
            model = Build.MODEL.orEmpty(),
            sdkInt = Build.VERSION.SDK_INT,
            fingerprintSanitized = BuildFingerprintSanitizer.sanitize(Build.FINGERPRINT),
        )

    private fun collectBatterySnapshot(context: Context, settings: SettingsRepo): BatteryHarvestSnapshot {
        val sticky = registerStickyBatteryIntent(context) ?: return BatteryHarvestSnapshot(
            vehicleVoltageVolts = settings.lastBatteryVoltage,
        )
        val scale = sticky.getIntExtra(BatteryManager.EXTRA_SCALE, -1).takeIf { it > 0 }
        val level = sticky.getIntExtra(BatteryManager.EXTRA_LEVEL, -1).takeIf { it >= 0 }
        val pct = if (scale != null && level != null) (100f * level) / scale else null
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val charging = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            bm?.isCharging
        } else {
            null
        }
        return BatteryHarvestSnapshot(
            chargePercent = pct,
            status = sticky.getIntExtra(BatteryManager.EXTRA_STATUS, Int.MIN_VALUE)
                .takeIf { it != Int.MIN_VALUE },
            isCharging = charging,
            plugged = sticky.getIntExtra(BatteryManager.EXTRA_PLUGGED, Int.MIN_VALUE)
                .takeIf { it != Int.MIN_VALUE },
            health = sticky.getIntExtra(BatteryManager.EXTRA_HEALTH, Int.MIN_VALUE)
                .takeIf { it != Int.MIN_VALUE },
            temperatureTenthsC = sticky.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
                .takeIf { it != Int.MIN_VALUE },
            tabletVoltageMv = sticky.getIntExtra(BatteryManager.EXTRA_VOLTAGE, Int.MIN_VALUE)
                .takeIf { it != Int.MIN_VALUE },
            vehicleVoltageVolts = settings.lastBatteryVoltage,
        )
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private fun registerStickyBatteryIntent(context: Context): Intent? {
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(null, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(null, filter)
        }
    }

    private fun maybeCoarseLocation(context: Context, settings: SettingsRepo): CoarseLocationHarvest? {
        if (!settings.includeCoarseLocationInUpload) return null
        if (!settings.deepseekGpsEnabled) return null
        val ok = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        if (!ok) return null
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        val loc = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            ?: lm.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER)
            ?: return null
        val lat = roundCoord(loc.latitude)
        val lon = roundCoord(loc.longitude)
        if (!lat.isFinite() || !lon.isFinite()) return null
        return CoarseLocationHarvest(latitudeRounded = lat, longitudeRounded = lon)
    }

    private fun roundCoord(value: Double): Double = round(value * 100.0) / 100.0
}
