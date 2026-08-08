package com.caseforge.scanner.planb

import kotlinx.serialization.json.Json
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
class JeepWedgeConfigTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun parses_asset_shape_inline() {
        val raw =
            """
            {
              "schemaVersion": 1,
              "gatewayNote": "note",
              "obdOnlyDefault": true,
              "supportedTiers": [0, 1, 2, 3],
              "platformCards": [
                {
                  "id": "jl-wrangler-2018-2021",
                  "marque": "Jeep",
                  "platformCode": "JL",
                  "model": "Wrangler",
                  "modelYearStart": 2018,
                  "modelYearEnd": 2021
                }
              ]
            }
            """.trimIndent()

        val m = json.decodeFromString<MarqueWedgeMatrix>(raw)
        assertEquals(1, m.schemaVersion)
        assertTrue(m.obdOnlyDefault)
        assertEquals(listOf(0, 1, 2, 3), m.supportedTiers)
        assertEquals(1, m.platformCards.size)
        assertEquals("JL", m.platformCards[0].platformCode)
        assertEquals("Wrangler", m.platformCards[0].model)
    }

    @Test
    fun matrixSummaryLine_includes_tiers_and_mode() {
        val m = MarqueWedgeMatrix(
            gatewayNote = "x",
            obdOnlyDefault = true,
            supportedTiers = listOf(1, 0, 2, 3),
            platformCards = listOf(
                MarquePlatformCard(
                    id = "jl",
                    marque = "Jeep",
                    platformCode = "JL",
                    model = "Wrangler",
                    modelYearStart = 2018,
                    modelYearEnd = 2021,
                ),
            ),
        )
        val line = m.matrixSummaryLine()
        assertTrue(line.contains("Jeep JL Wrangler 2018-2021"))
        assertTrue(line.contains("tiers"))
        assertTrue(line.contains("OBD-only default"))
    }

    @Test
    fun isJeepVin_delegates_to_jeep_family_wmis() {
        assertTrue(JeepWedgeConfig.isJeepVin("1J4HDAFP8KL123456"))
        assertTrue(JeepWedgeConfig.isJeepVin("1c4hjxen5kw123456"))
        assertTrue(JeepWedgeConfig.isJeepVin("3C4XXXXX000000000"))
        assertFalse(JeepWedgeConfig.isJeepVin("WBA12345678901234"))
        assertFalse(JeepWedgeConfig.isJeepVin("12"))
        assertFalse(JeepWedgeConfig.isJeepVin(""))
    }

    @Test
    fun load_reads_bundled_asset() {
        val ctx = RuntimeEnvironment.getApplication()
        val m = JeepWedgeConfig.load(ctx)
        assertNotNull(m)
        assertEquals(3, m!!.platformCards.size)
        val jeepCard = m.platformCards.find { it.marque.equals("Jeep", ignoreCase = true) }
        assertNotNull(jeepCard)
        assertEquals("JL", jeepCard!!.platformCode)
        assertTrue(m.matrixSummaryLine().contains("Jeep JL Wrangler"))
        assertEquals(listOf(0, 1, 2, 3, 4), m.supportedTiers.sorted())
    }
}
