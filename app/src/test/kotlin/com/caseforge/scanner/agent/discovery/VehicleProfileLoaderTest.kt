package com.caseforge.scanner.agent.discovery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class VehicleProfileLoaderTest {

    @Test
    fun load_ford_windstar_2000_recommends_tier0() {
        val ctx = RuntimeEnvironment.getApplication()
        val profile = VehicleProfileLoader.load(ctx, VehicleProfileLoader.DEFAULT_WINDSTAR_ID)
        assertNotNull(profile)
        assertEquals("ford-windstar-2000", profile!!.id)
        assertEquals(0, profile.recommendedTier)
        assertTrue(profile.supportedTiers.contains(0))
        assertEquals("ford-windstar-2000", profile.wedgeCardId)
    }

    @Test
    fun load_ford_windstar_has_elm327_usb_link_hints() {
        val ctx = RuntimeEnvironment.getApplication()
        val profile = VehicleProfileLoader.load(ctx, VehicleProfileLoader.DEFAULT_WINDSTAR_ID)!!
        assertEquals("elm327_usb", profile.linkHints.transportMode)
        assertEquals(115200, profile.linkHints.serial.baud)
        assertTrue(profile.adapterClasses.any { it.id == "elm327_usb" && it.recommended })
        assertTrue(profile.notSupported.contains("DoIP"))
    }

    @Test
    fun load_ford_windstar_notes_pats_not_skreem() {
        val ctx = RuntimeEnvironment.getApplication()
        val profile = VehicleProfileLoader.load(ctx, VehicleProfileLoader.DEFAULT_WINDSTAR_ID)!!
        assertTrue(profile.immoNotes.contains("PATS", ignoreCase = true))
        assertTrue(profile.immoNotes.contains("SKREEM", ignoreCase = true))
    }

    @Test
    fun listProfileIds_includes_windstar() {
        val ctx = RuntimeEnvironment.getApplication()
        val ids = VehicleProfileLoader.listProfileIds(ctx)
        assertTrue(ids.contains(VehicleProfileLoader.DEFAULT_WINDSTAR_ID))
    }

    @Test
    fun profileIdForVin_resolves_windstar_card_when_my_in_band() {
        val ctx = RuntimeEnvironment.getApplication()
        val vin2000 = "2FMZA5140YBA12345"
        assertEquals(VehicleProfileLoader.DEFAULT_WINDSTAR_ID, VehicleProfileLoader.profileIdForVin(ctx, vin2000))
    }

    @Test
    fun profileIdForVin_defaults_when_vin_blank() {
        val ctx = RuntimeEnvironment.getApplication()
        assertEquals(VehicleProfileLoader.DEFAULT_WINDSTAR_ID, VehicleProfileLoader.profileIdForVin(ctx, null))
        assertEquals(VehicleProfileLoader.DEFAULT_WINDSTAR_ID, VehicleProfileLoader.profileIdForVin(ctx, "  "))
    }
}
