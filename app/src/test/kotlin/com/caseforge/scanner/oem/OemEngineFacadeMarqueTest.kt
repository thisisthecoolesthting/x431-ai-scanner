package com.caseforge.scanner.oem

import com.caseforge.scanner.planb.MarqueWedgeConfig
import com.caseforge.scanner.planb.detectPlanbMarque
import com.caseforge.scanner.vin.VinNormalizer
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
class OemEngineFacadeMarqueTest {

    @Test
    fun decodeVinModelYear_mapsPosition10() {
        assertEquals(2018, MarqueWedgeConfig.decodeVinModelYear("123456789JABCDEFG"))
        assertEquals(2005, MarqueWedgeConfig.decodeVinModelYear("1234567895ABCDEFG"))
    }

    @Test
    fun findCardForVin_matchesBundledJl_whenYearInRange() {
        val ctx = RuntimeEnvironment.getApplication()
        val vin = jlTestVinModelYear2019()
        val matrix = MarqueWedgeConfig.load(ctx) ?: error("missing wedge asset")
        val card = MarqueWedgeConfig.findCardForVin(vin, matrix)
        assertNotNull(card)
        assertEquals("JL", card!!.platformCode)
        assertEquals("Wrangler", card.model)
    }

    @Test
    fun marqueWedgeLines_jeepWithCard_mentionsMatrixTiers() {
        val ctx = RuntimeEnvironment.getApplication()
        val settings = com.caseforge.scanner.data.SettingsRepo(ctx)
        val facade = OemEngineFacade(ctx, settings)
        val vin = jlTestVinModelYear2019()
        val lines = facade.marqueWedgeLines(vin)
        assertTrue(lines.any { it.contains("Jeep (matrix card)") })
        assertTrue(lines.any { it.contains("JL") && it.contains("Wrangler") })
        assertTrue(lines.any { it.contains("0=on") && it.contains("1=on") && it.contains("4=on") })
    }

    @Test
    fun marqueWedgeLines_fordVin_alwaysShowsBundledTierBits() {
        val ctx = RuntimeEnvironment.getApplication()
        val settings = com.caseforge.scanner.data.SettingsRepo(ctx)
        val facade = OemEngineFacade(ctx, settings)
        val lines = facade.marqueWedgeLines("1FTFW1ET5EFA00000")
        assertTrue(lines[0].contains("Ford"))
        assertTrue(lines.any { it.contains("0=on") && it.contains("1=on") && it.contains("4=on") })
    }

    @Test
    fun marqueWedgeLines_f150InFordBand_showsFordMatrixCard() {
        val ctx = RuntimeEnvironment.getApplication()
        val settings = com.caseforge.scanner.data.SettingsRepo(ctx)
        val facade = OemEngineFacade(ctx, settings)
        val lines = facade.marqueWedgeLines("1FT000000FK000000")
        assertTrue(lines.any { it.contains("Ford (matrix card)") })
        assertTrue(lines.any { it.contains("Card:") && it.contains("F150") && it.contains("F-150") })
    }

    @Test
    fun detectPlanbMarque_fordUsesMatrix_whenCardMatched() {
        val ctx = RuntimeEnvironment.getApplication()
        assertEquals(com.caseforge.scanner.planb.PlanbMarque.FORD, detectPlanbMarque(ctx, "1FT000000FK000000"))
    }

    @Test
    fun detectPlanbMarque_fordBeforeJeepWmi() {
        val ctx = RuntimeEnvironment.getApplication()
        val ford = "1FTFW1ET5EFA00000"
        assertEquals(com.caseforge.scanner.planb.PlanbMarque.FORD, detectPlanbMarque(ctx, ford))
    }

    @Test
    fun detectPlanbMarque_matrixCardBeatsPlainWmi() {
        val ctx = RuntimeEnvironment.getApplication()
        val vin = jlTestVinModelYear2019()
        assertEquals(com.caseforge.scanner.planb.PlanbMarque.JEEP, detectPlanbMarque(ctx, vin))
    }

    @Test
    fun marqueWedgeStatusBanner_matchesFordFallBackWhenVinMissesBand() {
        val ctx = RuntimeEnvironment.getApplication()
        val matrix = MarqueWedgeConfig.load(ctx) ?: error("missing wedge asset")
        val engine = com.caseforge.scanner.planb.PlanBEngine(com.caseforge.scanner.data.SettingsRepo(ctx))
        val fordOutOfBandVin = "1FTFW1ET5EFA00000" // position-10 encodes MY outside Ford F150 2015–2020 band
        val banner = engine.marqueWedgeStatusBanner(matrix, fordOutOfBandVin)
        assertNotNull(banner)
        assertEquals("Marque: Ford F-150 wedge (beta)", banner)
    }

    /** Jeep-family WMI, model year 2019 (K) at position 10, valid check digit at position 9. */
    private fun jlTestVinModelYear2019(): String {
        val head = "1C4HJXEN"
        val year = "K" // 2019 ∈ 2018–2021 JL card in bundled asset
        val serial = "1234567"
        val chars = (head + "0" + year + serial).toCharArray()
        require(chars.size == 17)
        val check = VinNormalizer.computeCheckDigit(String(chars))
        chars[8] = check
        return String(chars)
    }
}
