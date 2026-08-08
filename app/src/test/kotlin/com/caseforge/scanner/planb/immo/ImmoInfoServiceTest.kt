package com.caseforge.scanner.planb.immo

import com.caseforge.scanner.planb.PlanbMarque
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ImmoInfoServiceTest {

    @Test
    fun readState_includesRiskCopyBundledBannerAndInfoOnlyMessaging() {
        val ctx = RuntimeEnvironment.getApplication()
        val info = ImmoInfoService(ctx).readState(PlanbMarque.JEEP)
        assertTrue(info.stateSummary.isNotBlank())
        assertEquals(ImmoRiskCopy.infoOnlyBanner, info.riskBanner)
        assertEquals(ImmoRiskCopy.fullDisclaimer, info.disclaimer)
        assertNotNull(info.banner)
        assertNotNull(info.banner?.skreemModule)
    }

    @Test
    fun readState_dodge_mentions_skreem_module() {
        val ctx = RuntimeEnvironment.getApplication()
        val info = ImmoInfoService(ctx).readState(PlanbMarque.DODGE)
        assertTrue(
            info.stateSummary.contains("SKREEM", ignoreCase = true) ||
                info.stateSummary.contains("SKIM", ignoreCase = true),
        )
        assertNotNull(info.banner?.skreemModule)
    }

    @Test
    fun readState_ford_notes_skreem_not_applicable() {
        val ctx = RuntimeEnvironment.getApplication()
        val info = ImmoInfoService(ctx).readState(PlanbMarque.FORD)
        assertTrue(info.stateSummary.contains("SKREEM", ignoreCase = true))
        assertTrue(info.banner?.skreemModule == null)
    }
}
