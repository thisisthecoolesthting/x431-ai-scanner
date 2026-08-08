package com.caseforge.scanner.vin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FordVinDetectorTest {

    @Test
    fun isLikelyFordVin_true_for_common_wmis() {
        assertTrue(FordVinDetector.isLikelyFordVin("1FTRxxxxxxxxxxxxxx"))
        assertTrue(FordVinDetector.isLikelyFordVin("1FABPxxxxxxxxxxxxx"))
        assertTrue(FordVinDetector.isLikelyFordVin("3FABPxxxxxxxxxxxxx"))
        assertTrue(FordVinDetector.isLikelyFordVin("1FDxxxxxxxxxxxxxxx"))
        assertTrue(FordVinDetector.isLikelyFordVin("2FDxxxxxxxxxxxxxxx"))
        assertTrue(FordVinDetector.isLikelyFordVin("1FMZKxxxxxxxxxxxxx"))
    }

    @Test
    fun isLikelyFordVin_false_for_non_ford_wmi() {
        assertFalse(FordVinDetector.isLikelyFordVin("1J4RJFAAXXXXXXXX"))
        assertFalse(FordVinDetector.isLikelyFordVin("1HGCM82633A004352"))
        assertFalse(FordVinDetector.isLikelyFordVin("1B3xxxxxxxxxxxxxxx"))
    }

    @Test
    fun isLikelyFordVin_normalizes_case_and_noise() {
        assertTrue(FordVinDetector.isLikelyFordVin("  1ft  "))
        assertTrue(FordVinDetector.isLikelyFordVin("3fa-bp-xxxx-xxxxx-xx"))
    }

    @Test
    fun marque_hint_and_vinNormalizer_order_after_jeep() {
        assertEquals(
            FordVinDetector.FORD_WEDGE_HINT,
            FordVinDetector.marqueHint("1FTBR34EXXXXXXXXX"),
        )
        assertNull(FordVinDetector.marqueHint("WBAxxxxxxxxxxxxxxx"))
        // Jeep WMI wins globally in VinNormalizer
        assertEquals(
            JeepVinDetector.JEEP_WEDGE_HINT,
            VinNormalizer.marqueHint("1J4RJFAAXXXXXXXX"),
        )
        assertEquals(
            FordVinDetector.FORD_WEDGE_HINT,
            VinNormalizer.marqueHint("1FTBR34EXXXXXXXXX"),
        )
    }
}
