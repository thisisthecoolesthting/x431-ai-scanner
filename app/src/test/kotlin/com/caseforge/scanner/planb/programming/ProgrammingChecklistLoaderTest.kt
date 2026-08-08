package com.caseforge.scanner.planb.programming

import com.caseforge.scanner.planb.PlanbMarque
import com.caseforge.scanner.planb.immo.SkreemModule
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
class ProgrammingChecklistLoaderTest {

    @Test
    fun load_parses_jeep_ford_and_dodge_bundled_assets() {
        val ctx = RuntimeEnvironment.getApplication()
        val jeep = ProgrammingChecklistLoader.load(ctx, PlanbMarque.JEEP)
        val ford = ProgrammingChecklistLoader.load(ctx, PlanbMarque.FORD)
        val dodge = ProgrammingChecklistLoader.load(ctx, PlanbMarque.DODGE)

        assertNotNull(jeep)
        assertNotNull(ford)
        assertNotNull(dodge)

        assertEquals("jeep", jeep!!.marqueId)
        assertTrue(jeep.entries.size >= 7)
        assertTrue(jeep.entries.all { it.applyMode == "blocked" })
        assertTrue(jeep.entries.all { it.partnerHandoff })

        assertEquals("ford", ford!!.marqueId)
        assertTrue(ford.entries.size in 3..5)
        assertTrue(ford.entries.all { it.partnerHandoff })
        assertFalse(ford.entries.any { it.id.startsWith("skreem-") })

        assertEquals("dodge", dodge!!.marqueId)
        assertTrue(dodge.entries.size >= 7)
    }

    @Test
    fun load_jeep_merges_skreem_overlay_with_capability_id() {
        val ctx = RuntimeEnvironment.getApplication()
        val jeep = ProgrammingChecklistLoader.load(ctx, PlanbMarque.JEEP)!!

        assertTrue(jeep.entries.any { it.id == "skreem-key-learn-procedure" })
        assertTrue(
            jeep.entries.any {
                it.capabilityId == SkreemModule.CAPABILITY_ID && it.isSkreemEntry()
            },
        )
        assertEquals(SkreemModule.CAPABILITY_ID, jeep.capabilityId)
    }

    @Test
    fun loadSkreemOverlay_parses_stellantis_asset() {
        val ctx = RuntimeEnvironment.getApplication()
        val overlay = ProgrammingChecklistLoader.loadSkreemOverlay(ctx)
        assertNotNull(overlay)
        assertEquals("stellantis", overlay!!.marqueId)
        assertTrue(overlay.entries.isNotEmpty())
    }
}
