package com.caseforge.scanner.planb.gateway

import com.caseforge.scanner.planb.PlanbMarque
import com.caseforge.scanner.planb.body.BodyDtc
import com.caseforge.scanner.planb.gateway.replay.GoldenReplaySource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GatewaySessionReplayTest {

    private val jeepGolden = GoldenReplaySource.fromFixtureLines(
        lines = listOf("# jeep gateway golden", "STUB_FRAME 01"),
        marque = PlanbMarque.JEEP,
    )

    private val stubList = listOf(
        BodyDtc(code = "P0128", description = "Coolant thermostat (generic)"),
        BodyDtc(code = "U0100", description = "Lost communication with ECM/PCM \"A\" (generic)"),
    )

    /** Mode 03 positive response — SF PCI + `43 01 02` padded (matches bundled Ford tier-1 replay scaffold hex). */
    private val fordMode03Golden =
        GoldenReplaySource.fromFixtureLines(
            lines = listOf(
                "{\"ts\":\"2026-05-20T18:00:05.010Z\",\"dir\":\"RX\",\"canId\":\"0x7E8\"," +
                    "\"payload\":\"0543010200000000\",\"uiContext\":\"Mode 03 scaffold P0102\"}",
            ),
            marque = PlanbMarque.FORD,
        )

    @Test
    fun fordReplay_fullGoldenJsonlSnippet_returnsP0102() {
        val snippet = listOf(
            "{\"ts\": \"2026-05-20T18:00:00.000Z\", \"dir\": \"TX\", \"canId\": \"0x7E0\", \"payload\": \"0209020000000000\", \"uiContext\": \"pad\"}",
            "{\"ts\": \"2026-05-20T18:00:05.010Z\", \"dir\": \"RX\", \"canId\": \"0x7E8\", \"payload\": \"0543010200000000\", \"uiContext\": \"Mode 03\"}",
        )
        val golden = GoldenReplaySource.fromFixtureLines(snippet, PlanbMarque.FORD)
        val gw = GatewaySession(
            marque = PlanbMarque.FORD.id,
            gatewayReplayEnabled = true,
            goldenReplaySource = golden,
        )
        assertTrue(gw.connect("pcm").isSuccess)
        val dtcs = gw.readDtcs().getOrThrow()
        assertEquals(1, dtcs.size)
        assertEquals("P0102", dtcs.single().code)
    }

    @Test
    fun replay_matchesMarque_returnsSyntheticDtcs() {
        val gw = GatewaySession(
            marque = PlanbMarque.JEEP.id,
            gatewayReplayEnabled = true,
            goldenReplaySource = jeepGolden,
        )
        assertTrue(gw.connect("pcm").isSuccess)
        val dtcs = gw.readDtcs().getOrThrow()
        assertEquals(stubList, dtcs)
    }

    @Test
    fun replay_wrongMarque_returnsEmpty() {
        val gw = GatewaySession(
            marque = PlanbMarque.FORD.id,
            gatewayReplayEnabled = true,
            goldenReplaySource = jeepGolden,
        )
        assertTrue(gw.connect("pcm").isSuccess)
        assertEquals(emptyList<BodyDtc>(), gw.readDtcs().getOrThrow())
    }

    @Test
    fun fordReplay_matchesMarque_returnsNonEmptyDecodedDtcsFromGoldenJson() {
        val gw = GatewaySession(
            marque = PlanbMarque.FORD.id,
            gatewayReplayEnabled = true,
            goldenReplaySource = fordMode03Golden,
        )
        assertTrue(gw.connect("pcm").isSuccess)
        val dtcs = gw.readDtcs().getOrThrow()
        assertEquals(1, dtcs.size)
        assertEquals("P0102", dtcs.single().code)
        assertTrue(dtcs.single().description.contains("replay", ignoreCase = true))
    }

    @Test
    fun fordReplay_emptyGolden_fallbackStillReturnsP0102() {
        val emptyFord = GoldenReplaySource.fromFixtureLines(emptyList(), PlanbMarque.FORD)
        val gw = GatewaySession(
            marque = PlanbMarque.FORD.id,
            gatewayReplayEnabled = true,
            goldenReplaySource = emptyFord,
        )
        assertTrue(gw.connect("pcm").isSuccess)
        val dtcs = gw.readDtcs().getOrThrow()
        assertEquals(emptyFord.syntheticReplayDtcs(), dtcs)
    }

    @Test
    fun replay_disabled_returnsEmptyEvenWhenGoldenPresent() {
        val gw = GatewaySession(
            marque = PlanbMarque.JEEP.id,
            gatewayReplayEnabled = false,
            goldenReplaySource = jeepGolden,
        )
        assertTrue(gw.connect("pcm").isSuccess)
        assertEquals(emptyList<BodyDtc>(), gw.readDtcs().getOrThrow())
    }

    @Test
    fun replay_enabled_noGolden_returnsEmpty() {
        val gw = GatewaySession(
            marque = PlanbMarque.JEEP.id,
            gatewayReplayEnabled = true,
            goldenReplaySource = null,
        )
        assertTrue(gw.connect("pcm").isSuccess)
        assertEquals(emptyList<BodyDtc>(), gw.readDtcs().getOrThrow())
    }

    @Test
    fun goldenSource_loadFixtureLines_preservesPayload() {
        assertEquals(listOf("# jeep gateway golden", "STUB_FRAME 01"), jeepGolden.lines)
        assertEquals(PlanbMarque.JEEP, jeepGolden.marque)
    }
}
