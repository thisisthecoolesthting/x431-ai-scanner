package com.caseforge.scanner.planb

import com.caseforge.scanner.engine.CapabilityEntry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CapabilityWedgeFilterTest {

    @Test
    fun fordCardMatchesLincolnScopedCapability() {
        val card = MarquePlatformCard(marque = "Ford")
        val entry =
            CapabilityEntry(id = "t", label = "Test", category = "Codes", oemScope = listOf("lincoln"))
        assertTrue(CapabilityWedgeFilter.matchesWedge(card, entry))
    }

    @Test
    fun jeepCardDoesNotMatchDodgeOnlyScope() {
        val card = MarquePlatformCard(marque = "Jeep")
        val entry = CapabilityEntry(id = "t", label = "Test", category = "Codes", oemScope = listOf("dodge"))
        assertFalse(CapabilityWedgeFilter.matchesWedge(card, entry))
    }

    @Test
    fun programmingSkimKeyLearnIsSkreemRow() {
        val row = CapabilityEntry(
            id = "programming_skim_key_learn",
            label = "SKIM/SKREEM Immobilizer Key Learning (Dealer-Level)",
            category = "Programming",
            oemScope = listOf("jeep"),
        )
        assertTrue(CapabilityWedgeFilter.isSkreemImmobilizerRow(row))
    }
}
