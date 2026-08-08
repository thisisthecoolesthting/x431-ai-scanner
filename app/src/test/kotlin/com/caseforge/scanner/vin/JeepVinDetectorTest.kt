package com.caseforge.scanner.vin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JeepVinDetectorTest {

    @Test
    fun isLikelyJeepVin_true_for_known_wmi() {
        assertTrue(JeepVinDetector.isLikelyJeepVin("1J4RJFAAXXXXXXXX"))
        assertTrue(JeepVinDetector.isLikelyJeepVin("1C4RJFAGXXXXXXXX"))
        assertTrue(JeepVinDetector.isLikelyJeepVin("3C4XXXXXXXXXXXXXX"))
    }

    @Test
    fun isLikelyJeepVin_false_for_non_jeep_wmi() {
        assertFalse(JeepVinDetector.isLikelyJeepVin("1HGCM82633A004352"))
        assertFalse(JeepVinDetector.isLikelyJeepVin("5FNRL6H73LB123456"))
    }

    @Test
    fun isLikelyJeepVin_normalizes_case_and_noise() {
        assertTrue(JeepVinDetector.isLikelyJeepVin("1j4-rjf-aaxxxxxxx"))
        assertTrue(JeepVinDetector.isLikelyJeepVin("  1C4  "))
    }

    @Test
    fun isLikelyJeepVin_false_when_too_short() {
        assertFalse(JeepVinDetector.isLikelyJeepVin("1J"))
        assertFalse(JeepVinDetector.isLikelyJeepVin(""))
    }

    @Test
    fun marqueHint_returns_constant_or_null() {
        assertEquals(
            JeepVinDetector.JEEP_WEDGE_HINT,
            JeepVinDetector.marqueHint("1J8XXXXXXXXXXXXXX"),
        )
        assertNull(JeepVinDetector.marqueHint("1HGCM82633A004352"))
    }

    @Test
    fun vinNormalizer_delegates_marque_hint() {
        assertEquals(
            JeepVinDetector.JEEP_WEDGE_HINT,
            VinNormalizer.marqueHint("1J4RJFAAXXXXXXXX"),
        )
        assertNull(VinNormalizer.marqueHint("1HGCM82633A004352"))
    }
}
