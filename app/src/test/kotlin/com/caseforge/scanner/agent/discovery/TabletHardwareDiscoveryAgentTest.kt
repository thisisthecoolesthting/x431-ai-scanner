package com.caseforge.scanner.agent.discovery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class TabletHardwareDiscoveryAgentTest {

    @Test
    fun scan_windstar_profile_includes_operator_steps_and_tier0() {
        val ctx = RuntimeEnvironment.getApplication()
        val agent = TabletHardwareDiscoveryAgent(ctx)
        val report = agent.scan(VehicleProfileLoader.DEFAULT_WINDSTAR_ID)
        assertEquals(VehicleProfileLoader.DEFAULT_WINDSTAR_ID, report.windstarProfileId)
        assertEquals(0, report.recommendedTier)
        assertTrue(report.vehicleLabel!!.contains("Windstar"))
        assertTrue(report.operatorSteps.isNotEmpty())
        assertTrue(report.recommendedAction.isNotBlank())
        assertTrue(report.linkHintsSummary.contains("115200"))
    }

    @Test
    fun formatForAgent_includes_android_limits() {
        val ctx = RuntimeEnvironment.getApplication()
        val agent = TabletHardwareDiscoveryAgent(ctx)
        val report = agent.scan(VehicleProfileLoader.DEFAULT_WINDSTAR_ID)
        val text = agent.formatForAgent(report)
        assertTrue(text.contains("Connection readiness"))
        assertTrue(text.contains("Android limits"))
        assertTrue(text.contains("Windstar") || text.contains("ford-windstar-2000"))
    }
}
