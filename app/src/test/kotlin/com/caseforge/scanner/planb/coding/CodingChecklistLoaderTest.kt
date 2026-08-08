package com.caseforge.scanner.planb.coding

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
@Config(sdk = [33])
class CodingChecklistLoaderTest {

    @Test
    fun load_parses_jeep_ford_and_dodge_bundled_assets() {
        val ctx = RuntimeEnvironment.getApplication()
        val jeep = CodingChecklistLoader.load(ctx, PlanbMarque.JEEP)
        val ford = CodingChecklistLoader.load(ctx, PlanbMarque.FORD)
        val dodge = CodingChecklistLoader.load(ctx, PlanbMarque.DODGE)

        assertNotNull(jeep)
        assertNotNull(ford)
        assertNotNull(dodge)

        assertEquals("jeep", jeep!!.marqueId)
        assertTrue(jeep.entries.size in 3..5)
        assertTrue(jeep.entries.all { it.rollbackSupported })
        assertTrue(jeep.entries.all { it.applyMode == "stub" })

        assertEquals("ford", ford!!.marqueId)
        assertTrue(ford.entries.size in 3..5)

        assertEquals("dodge", dodge!!.marqueId)
        assertTrue(dodge.entries.size in 3..5)
    }
}
