package com.caseforge.scanner.transfer

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.caseforge.scanner.agent.ScannerAccessibilityService
import com.caseforge.scanner.agent.discovery.VehicleProfileLoader
import com.caseforge.scanner.data.SettingsRepo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TabletDataHarvesterTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun build_includesDiscoveryReportAndProfileId() {
        val settings = SettingsRepo(context)
        val batch =
            TabletDataHarvester.build(context, VehicleProfileLoader.DEFAULT_WINDSTAR_ID, settings = settings)
        assertEquals(HarvestBatchManifest.SCHEMA_VERSION, batch.manifest.schemaVersion)
        assertEquals(VehicleProfileLoader.DEFAULT_WINDSTAR_ID, batch.manifest.vehicleProfileId)
        assertTrue(batch.manifest.versionCode > 0)
        assertTrue(batch.manifest.timestampMs > 0L)
        assertTrue(batch.asZipSidecars().containsKey(HarvestBatchManifest.ZIP_ENTRY))
        val json = batch.asZipSidecars()[HarvestBatchManifest.ZIP_ENTRY]!!.decodeToString()
        assertTrue(json.contains("discoveryReport"))
        assertTrue(json.contains("devices"))
        assertTrue(json.contains("tabletBuild"))
        assertTrue(json.contains("fingerprintSanitized"))
        assertTrue(json.contains("\"sdkInt\""))
        assertTrue(json.contains("battery"))
        assertTrue(json.contains("x431PackagesInstalled"))
        assertEquals(ScannerAccessibilityService.OEM_DIAG_PACKAGES.size, batch.manifest.x431PackagesInstalled.size)
    }
}
