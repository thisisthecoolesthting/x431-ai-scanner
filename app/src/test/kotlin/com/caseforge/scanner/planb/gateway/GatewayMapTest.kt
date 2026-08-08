package com.caseforge.scanner.planb.gateway

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GatewayMapTest {

    @Test
    fun fordWindstarWedgeDefaults_labels_pcm() {
        val pcm = fordWindstarWedgeDefaults().single { it.id == "pcm" }
        assertEquals(0x7E0, pcm.reqId)
        assertEquals(0x7E8, pcm.respId)
        assertTrue(pcm.name.contains("Windstar", ignoreCase = true))
    }

    @Test
    fun jeepWedgeDefaults_includes_pcm_7e0_7e8() {
        val pcm = jeepWedgeDefaults().single { it.id == "pcm" }
        assertEquals(0x7E0, pcm.reqId)
        assertEquals(0x7E8, pcm.respId)
    }

    @Test
    fun fordWedgeDefaults_includes_pcm_7e0_7e8() {
        val pcm = fordWedgeDefaults().single { it.id == "pcm" }
        assertEquals(0x7E0, pcm.reqId)
        assertEquals(0x7E8, pcm.respId)
    }

    @Test
    fun dodgeWedgeDefaults_includes_pcm_7e0_7e8() {
        val pcm = dodgeWedgeDefaults().single { it.id == "pcm" }
        assertEquals(0x7E0, pcm.reqId)
        assertEquals(0x7E8, pcm.respId)
    }

    @Test
    fun forMarque_ford_and_dodge_match_wedge_defaults() {
        val fordPcm = GatewayMap.forMarque(GatewayMap.MARQUE_FORD).single { it.id == "pcm" }
        assertEquals(fordWedgeDefaults().single { it.id == "pcm" }, fordPcm)

        val dodgePcm = GatewayMap.forMarque(GatewayMap.MARQUE_DODGE).single { it.id == "pcm" }
        assertEquals(dodgeWedgeDefaults().single { it.id == "pcm" }, dodgePcm)
    }

    @Test
    fun forMarque_is_case_insensitive_for_ford() {
        val a = GatewayMap.forMarque("FoRd")
        val b = GatewayMap.forMarque(GatewayMap.MARQUE_FORD)
        assertEquals(a, b)
    }
}
