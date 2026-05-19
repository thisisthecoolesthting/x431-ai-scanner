package com.caseforge.scanner.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PidCatalogTest {

    @Test
    fun fastDefault_hasSixCorePids() {
        assertEquals(6, PidCatalog.FAST_DEFAULT.size)
        assertEquals(listOf("0C", "05", "0D", "06", "07", "42"), PidCatalog.FAST_DEFAULT_POLL_IDS)
    }

    @Test
    fun resolve_matchesHexAliasAndLabel() {
        assertNotNull(PidCatalog.resolve("0c"))
        assertEquals("Engine RPM", PidCatalog.resolve("RPM")?.label)
        assertEquals("Engine RPM", PidCatalog.resolve("Engine RPM")?.label)
        assertEquals("°C", PidCatalog.resolve("Coolant Temp (°C)")?.unit)
    }

    @Test
    fun resolve_unknownReturnsNull() {
        assertNull(PidCatalog.resolve("MAP (kPa)"))
    }

    @Test
    fun formatValue_respectsDecimalRules() {
        assertEquals("1250", PidCatalog.formatValue("0C", 1250.0))
        assertEquals("12.5", PidCatalog.formatValue("STFT B1", 12.48))
    }

    @Test
    fun orderedLiveEntries_putsFastDefaultsFirst() {
        val ordered = PidCatalog.orderedLiveEntries(
            mapOf(
                "MAP (kPa)" to 45.0,
                "0D" to 35.0,
                "Engine RPM" to 820.0,
            ),
        )
        assertEquals("Engine RPM", PidCatalog.canonicalLabel(ordered[0].first))
        assertEquals("Vehicle speed", PidCatalog.canonicalLabel(ordered[1].first))
        assertTrue(ordered.last().first.contains("MAP"))
    }
}
