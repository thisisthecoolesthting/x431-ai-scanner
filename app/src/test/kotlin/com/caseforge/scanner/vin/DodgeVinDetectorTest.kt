package com.caseforge.scanner.vin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DodgeVinDetectorTest {

    @Test
    fun isLikelyDodgeVin_true_for_listed_wmis() {
        assertTrue(DodgeVinDetector.isLikelyDodgeVin("1B3xxxxxxxxxxxxxxx"))
        assertTrue(DodgeVinDetector.isLikelyDodgeVin("1B4xxxxxxxxxxxxxxx"))
        assertTrue(DodgeVinDetector.isLikelyDodgeVin("1B6xxxxxxxxxxxxxxx"))
        assertTrue(DodgeVinDetector.isLikelyDodgeVin("1B7xxxxxxxxxxxxxxx"))
        assertTrue(DodgeVinDetector.isLikelyDodgeVin("2B3xxxxxxxxxxxxxxx"))
        assertTrue(DodgeVinDetector.isLikelyDodgeVin("3B7xxxxxxxxxxxxxxx"))
        assertTrue(DodgeVinDetector.isLikelyDodgeVin("3C6xxxxxxxxxxxxxxx"))
        assertTrue(DodgeVinDetector.isLikelyDodgeVin("1C6xxxxxxxxxxxxxxx"))
    }

    @Test
    fun isLikelyDodgeVin_false_for_jeep_or_ford() {
        assertFalse(DodgeVinDetector.isLikelyDodgeVin("1C4RJFAGXXXXXXXX"))
        assertFalse(DodgeVinDetector.isLikelyDodgeVin("1FTBR34EXXXXXXXXX"))
        assertFalse(DodgeVinDetector.isLikelyDodgeVin("WBA12345678901234"))
    }

    @Test
    fun marque_hint_and_vinNormalizer_respects_prior_marques() {
        assertEquals(
            DodgeVinDetector.DODGE_WEDGE_HINT,
            DodgeVinDetector.marqueHint("1B3HJXXXXXXXXXXXX"),
        )
        assertNull(DodgeVinDetector.marqueHint("1FAxxxxxxxxxxxxxxx"))
        assertEquals(
            JeepVinDetector.JEEP_WEDGE_HINT,
            VinNormalizer.marqueHint("1C6XXXXXXXXXXXXXX"),
        )
        assertEquals(
            DodgeVinDetector.DODGE_WEDGE_HINT,
            VinNormalizer.marqueHint("1B7XXXXXXXXXXXXXX"),
        )
    }
}
