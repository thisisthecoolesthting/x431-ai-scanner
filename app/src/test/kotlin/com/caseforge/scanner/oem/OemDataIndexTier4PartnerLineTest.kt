package com.caseforge.scanner.oem

import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class OemDataIndexTier4PartnerLineTest {

    @Test
    fun tier4PartnerOnlySummaryLine_nullOrNonWedgeVin_returnsNull() {
        val ctx = RuntimeEnvironment.getApplication()
        assertNull(OemDataIndex.tier4PartnerOnlySummaryLine(ctx, null))
        assertNull(OemDataIndex.tier4PartnerOnlySummaryLine(ctx, "   "))
        assertNull(OemDataIndex.tier4PartnerOnlySummaryLine(ctx, "WBAZZZ12345678901"))
    }

    @Test
    fun tier4PartnerOnlySummaryLine_matchingFordCard_returnsPartnerLineWhenTierFourInMatrix() {
        val ctx = RuntimeEnvironment.getApplication()
        val vin = "1FTFW1ET5LF123456" // WMI 1FT Ford truck, MY 2020 matches bundled F150 2015–2020 row
        val line = OemDataIndex.tier4PartnerOnlySummaryLine(ctx, vin)
        assertTrue(line!!.startsWith("Tier 4: partner only — "))
        assertTrue(line.contains("Ford", ignoreCase = true))
        assertTrue(line.contains("F-150"))
    }
}
