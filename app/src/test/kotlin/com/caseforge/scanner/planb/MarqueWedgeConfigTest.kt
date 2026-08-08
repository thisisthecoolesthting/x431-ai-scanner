import com.caseforge.scanner.data.SettingsRepo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MarqueWedgeConfigTest {

    private val serde = Json { ignoreUnknownKeys = true }

    private fun fordVinModelYear2020(): String = "1FTFW1ET5LF123456"

    /** Ram-line WMI, model-year 2020 (L at index 9). */
    private fun dodgeRamVinModelYear2020(): String = "3C6SRFLT0LJG00000"

    /** Jeep-family WMI `1J4`, MY 2019 (K). */
    private fun jeepVinModelYear2019(): String = "1J4HDAFP9KK000000"

    private fun jeepFordDodgeMatrixInline(): MarqueWedgeMatrix = serde.decodeFromString(
        """
        {
          "schemaVersion": 1,
          "gatewayNote": "g",
          "obdOnlyDefault": true,
          "supportedTiers": [0, 1, 2, 3, 4],
          "platformCards": [
            {
              "id": "ford-sample",
              "marque": "Ford",
              "platformCode": "F",
              "model": "sample",
              "modelYearStart": 2015,
              "modelYearEnd": 2025,
              "obdOnlyDefault": false,
              "supportedTiers": [0, 1, 2, 3, 4],
              "tierNotes": {}
            },
            {
              "id": "dodge-sample",
              "marque": "Dodge",
              "platformCode": "R",
              "model": "sample",
              "modelYearStart": 2015,
              "modelYearEnd": 2025,
              "obdOnlyDefault": false,
              "supportedTiers": [0, 1, 2, 3, 4],
              "tierNotes": {}
            },
            {
              "id": "jeep-sample",
              "marque": "Jeep",
              "platformCode": "J",
              "model": "sample",
              "modelYearStart": 2018,
              "modelYearEnd": 2021,
              "obdOnlyDefault": true,
              "supportedTiers": [0, 1, 2, 3, 4],
              "tierNotes": {}
            }
          ]
        }
        """.trimIndent(),
    )

    @Test
    fun findCardForVin_maps_three_marques_with_model_year() {
        val m = jeepFordDodgeMatrixInline()
        assertEquals(
            "ford-sample",
            MarqueWedgeConfig.findCardForVin(fordVinModelYear2020(), m)!!.id,
        )
        assertEquals(
            "dodge-sample",
            MarqueWedgeConfig.findCardForVin(dodgeRamVinModelYear2020(), m)!!.id,
        )
        assertEquals(
            "jeep-sample",
            MarqueWedgeConfig.findCardForVin(jeepVinModelYear2019(), m)!!.id,
        )
        assertNull(MarqueWedgeConfig.findCardForVin("WBA12345678901234", m))
        assertNull(MarqueWedgeConfig.findCardForVin("", m))
    }

    @Test
    fun bundled_marque_asset_four_cards_including_windstar() {
        val ctx = RuntimeEnvironment.getApplication()
        val m = MarqueWedgeConfig.load(ctx)
        assertNotNull(m)
        val matrix = m!!
        assertEquals(4, matrix.platformCards.size)
        assertTrue(matrix.platformCards.any { it.id == "ford-windstar-2000" })
        assertTrue(matrix.supportedTiers.containsAll(listOf(0, 1, 2, 3, 4)))
        val fordCard = matrix.platformCards.find { it.id == "ford-f150-2015-2020" }!!
        assertFalse(fordCard.effectiveObdOnly(matrix))
        val windstarCard = matrix.platformCards.find { it.id == "ford-windstar-2000" }!!
        assertTrue(windstarCard.effectiveObdOnly(matrix))
        val jeepCard = matrix.platformCards.find { it.marque.equals("Jeep", ignoreCase = true) }!!
        assertTrue(jeepCard.effectiveObdOnly(matrix))
        assertEquals(
            listOf(0, 1, 2, 3, 4),
            fordCard.effectiveTiers(matrix).sorted(),
        )
        val compactAuditLine = matrix.platformCards.joinToString(" · ") { card ->
            val tiers = card.effectiveTiers(matrix).sorted().joinToString(",")
            val mode =
                if (card.effectiveObdOnly(matrix)) "OBD-default" else "full-diag-default"
            "${card.marque.lowercase()}[T$tiers;$mode]"
        }
        assertFalse(compactAuditLine.isBlank())
        assertTrue(compactAuditLine.contains("jeep[") && compactAuditLine.contains("ford["))
        assertTrue(compactAuditLine.contains("dodge["))
    }

    /** 2000 Ford Windstar — model year Y at index 9 → card ford-windstar-2000 before F-150 band. */
    @Test
    fun findCardForVin_windstar_2000_ford_minivan() {
        val ctx = RuntimeEnvironment.getApplication()
        val m = MarqueWedgeConfig.load(ctx)!!
        val vin2000 = "2FMZA5140YBA12345"
        assertEquals("ford-windstar-2000", MarqueWedgeConfig.findCardForVin(vin2000, m)!!.id)
    }

    @Test
    fun cardForMarque_id_or_marque_name() {
        val m = jeepFordDodgeMatrixInline()
        assertEquals(
            "ford-sample",
            MarqueWedgeConfig.cardForMarque(m, "ford-sample")!!.id,
        )
        assertEquals(
            "jeep-sample",
            MarqueWedgeConfig.cardForMarque(m, "Jeep")!!.id,
        )
    }

    @Test
    fun tierEnabled_three_marques_respects_supportedTiers() {
        val m = jeepFordDodgeMatrixInline()
        for (key in listOf("ford-sample", "dodge-sample", "jeep-sample")) {
            val c = MarqueWedgeConfig.cardForMarque(m, key)!!
            for (t in 0..4) {
                assertTrue(
                    "${c.marque} tier $t",
                    MarqueWedgeConfig.tierEnabled(c, t, m),
                )
            }
            assertFalse(MarqueWedgeConfig.tierEnabled(c, 5, m))
        }
        val matrixOnlyTiers = MarquePlatformCard(
            id = "x",
            marque = "X",
            platformCode = "?",
            model = "?",
            modelYearStart = 2020,
            modelYearEnd = 2025,
            supportedTiers = null,
        )
        val tierMatrix = serde.decodeFromString<MarqueWedgeMatrix>(
            """
            {"schemaVersion":1,"gatewayNote":"","obdOnlyDefault":false,
            "supportedTiers":[0,2],
            "platformCards":[
               {"id":"x","marque":"X","platformCode":"?","model":"?","modelYearStart":2020,"modelYearEnd":2025}
             ]}
            """.trimIndent(),
        )
        assertTrue(
            MarqueWedgeConfig.tierEnabled(matrixOnlyTiers, 0, tierMatrix),
        )
        assertFalse(MarqueWedgeConfig.tierEnabled(matrixOnlyTiers, 1, tierMatrix))
        assertTrue(MarqueWedgeConfig.tierEnabled(matrixOnlyTiers, 2, tierMatrix))
    }

    @Test
    fun fullTierSummary_matches_three_marques() {
        val m = jeepFordDodgeMatrixInline()
        val summary = MarqueWedgeConfig.fullTierSummary(m)
        assertTrue(summary.contains("ford[T0,1,2,3,4"))
        assertTrue(summary.contains("dodge[T0,1,2,3,4"))
        assertTrue(summary.contains("jeep[T0,1,2,3,4"))
        assertEquals(summary, MarqueWedgeConfig.fullTierSummary(m))
        assertEquals(compactAuditFromMatrix(m), summary)
    }

    @Test
    fun planbEngine_planbProgramming_emits_partner_gate_line() {
        val ctx = RuntimeEnvironment.getApplication()
        val settings = SettingsRepo(ctx)
        settings.planbBodyRead = false
        settings.planbCoding = false
        settings.planbImmoInfo = false
        settings.planbProgramming = true
        val lines = PlanBEngine(settings).tierStatusLines(showMarqueSuffix = false)
        assertEquals(listOf("Programming: partner gate (Tier 4)"), lines)
    }

    /** Ensures TieringSemantics deserializes with bundled asset. */
    @Test
    fun tieringSemantics_loaded_from_bundled_marque_asset() {
        val ctx = RuntimeEnvironment.getApplication()
        val m = MarqueWedgeConfig.load(ctx)!!
        assertNotNull(m.tieringSemantics)
        assertEquals(listOf(0, 1, 2, 3, 4), m.tieringSemantics!!.tierIndices.sorted())
        assertTrue(m.tieringSemantics!!.tier0.isNotBlank())
        assertTrue(m.tieringSemantics!!.tier4.isNotBlank())
    }

    /** Same shape as legacy inline audit rollup — must stay aligned with [MarqueWedgeConfig.fullTierSummary]. */
    private fun compactAuditFromMatrix(matrix: MarqueWedgeMatrix): String =
        matrix.platformCards.joinToString(" · ") { card ->
            val tiers = card.effectiveTiers(matrix).sorted().joinToString(",")
            val mode =
                if (card.effectiveObdOnly(matrix)) "OBD-default" else "full-diag-default"
            "${card.marque.lowercase()}[T$tiers;$mode]"
        }
}
