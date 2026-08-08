package com.caseforge.scanner.planb.gateway

import com.caseforge.scanner.agent.discovery.VehicleProfileLoader
import com.caseforge.scanner.planb.MarqueWedgeConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class FordGatewayVinRoutingTest {

    /** Canada Ford MPV WMI + MY 2001 (digit `1` at index 9) → bundled Windstar wedge band (1999–2003). */
    private val windstarVinPattern = "2FMZZZZZZ1ZZZZZZZ"

    private val f150SmokeVin = "1FTFW1ET5LF123456"

    @Test
    fun vinRoutes_toWindstarCard_andUsesWindstarGatewayDefaults() {
        val ctx = RuntimeEnvironment.getApplication()
        val card = MarqueWedgeConfig.findCardForVin(ctx, windstarVinPattern)
        assertEquals(VehicleProfileLoader.DEFAULT_WINDSTAR_ID, card?.id)
        val defs = fordGatewayDefaultsForVin(ctx, windstarVinPattern)
        assertEquals(fordWindstarWedgeDefaults(), defs)
        assertTrue(defs.single().name.contains("Windstar", ignoreCase = true))
    }

    @Test
    fun vinRoutes_toF150Card_andUsesGenericFordGatewayDefaults() {
        val ctx = RuntimeEnvironment.getApplication()
        assertEquals("ford-f150-2015-2020", MarqueWedgeConfig.findCardForVin(ctx, f150SmokeVin)?.id)
        assertEquals(fordWedgeDefaults(), fordGatewayDefaultsForVin(ctx, f150SmokeVin))
    }

    @Test
    fun blankVin_fallsBackToFordWedgeDefaults() {
        val ctx = RuntimeEnvironment.getApplication()
        assertEquals(fordWedgeDefaults(), fordGatewayDefaultsForVin(ctx, null))
        assertEquals(fordWedgeDefaults(), fordGatewayDefaultsForVin(ctx, "   "))
    }
}
