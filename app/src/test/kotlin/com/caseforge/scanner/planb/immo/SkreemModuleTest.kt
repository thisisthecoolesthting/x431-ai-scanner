package com.caseforge.scanner.planb.immo

import com.caseforge.scanner.planb.PlanbMarque
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SkreemModuleTest {

    @Test
    fun capability_constants_match_catalog() {
        assertEquals("programming_skim_key_learn", SkreemModule.CAPABILITY_ID)
        assertEquals(
            listOf("programming", "immobilizer", "skim_key_learn"),
            SkreemModule.CAPABILITY_PATH,
        )
    }

    @Test
    fun isStellantisMarque_covers_jeep_and_dodge_only() {
        assertTrue(SkreemModule.isStellantisMarque(PlanbMarque.JEEP))
        assertTrue(SkreemModule.isStellantisMarque(PlanbMarque.DODGE))
        assertFalse(SkreemModule.isStellantisMarque(PlanbMarque.FORD))
    }
}
