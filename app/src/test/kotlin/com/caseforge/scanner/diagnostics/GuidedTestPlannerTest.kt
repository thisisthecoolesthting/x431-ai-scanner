package com.caseforge.scanner.diagnostics

import com.caseforge.scanner.vci.DiagnosticConnector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GuidedTestPlannerTest {

    @Test
    fun catalog_containsSixCorePlans() {
        assertEquals(6, GuidedTestCatalog.ALL.size)
        assertNotNull(GuidedTestCatalog.byId("guided_misfire"))
        assertNotNull(GuidedTestCatalog.byId("guided_evap_leak"))
    }

    @Test
    fun suggest_matchesMisfireBySymptom() {
        val matches = GuidedTestPlanner.suggest(symptomQuery = "rough idle and P0302")
        assertTrue(matches.isNotEmpty())
        assertEquals("guided_misfire", matches.first().test.id)
        assertTrue(matches.first().matchedAliases.isNotEmpty() || matches.first().matchedDtcPrefixes.isNotEmpty())
    }

    @Test
    fun suggest_matchesEvapByDtc() {
        val matches = GuidedTestPlanner.suggest(dtcCodes = listOf("P0442"))
        assertEquals("guided_evap_leak", matches.first().test.id)
    }

    @Test
    fun suggest_filtersEvapWhenOnlyElmTransport() {
        val matches = GuidedTestPlanner.suggest(
            dtcCodes = listOf("P0442"),
            activeTransport = GuidedTransportRequirement.ELM327,
        )
        assertTrue(matches.none { it.test.id == "guided_evap_leak" })
    }

    @Test
    fun suggest_allowsEvapOnOemTransport() {
        val matches = GuidedTestPlanner.suggest(
            dtcCodes = listOf("P0442"),
            activeTransport = GuidedTransportRequirement.OEM,
        )
        assertEquals("guided_evap_leak", matches.first().test.id)
    }

    @Test
    fun planForId_returnsCatalogEntry() {
        val plan = GuidedTestPlanner.planForId("guided_charging_low_voltage")
        assertNotNull(plan)
        assertEquals(GuidedTransportRequirement.ELM327, plan?.requiredTransport)
        assertTrue(plan!!.steps.isNotEmpty())
        assertTrue(plan.reportWording.isNotBlank())
    }

    @Test
    fun transportFromLink_mapsElmAndOem() {
        assertEquals(
            GuidedTransportRequirement.ELM327,
            GuidedTestPlanner.transportFromLink(DiagnosticConnector.LinkKind.ELM327_USB),
        )
        assertEquals(
            GuidedTransportRequirement.OEM,
            GuidedTestPlanner.transportFromLink(DiagnosticConnector.LinkKind.OEM_BT),
        )
    }

    @Test
    fun bestMatch_returnsNullForUnrelatedQuery() {
        assertNull(GuidedTestPlanner.bestMatch(symptomQuery = "wiper blade size"))
    }
}
