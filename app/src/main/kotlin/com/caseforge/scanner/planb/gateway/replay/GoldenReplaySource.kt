package com.caseforge.scanner.planb.gateway.replay

import android.content.Context
import com.caseforge.scanner.planb.PlanbMarque
import com.caseforge.scanner.planb.body.BodyDtc
import com.caseforge.scanner.planb.golden.GoldenLogParser
import com.caseforge.scanner.obd.ObdDtcReader
import java.util.concurrent.ConcurrentHashMap

/**
 * Golden CAN / gateway log replay scaffold: retains raw lines from an asset or test fixture until
 * ISO-TP response framing maps them to real decoded rows.
 *
 * When [GatewaySession][com.caseforge.scanner.planb.gateway.GatewaySession] runs in replay mode and
 * the session marque matches [marque], [syntheticReplayDtcs] supplies OBD scaffold rows.
 *
 * - **Stellantis-style marques** (Jeep, Dodge/Ram/Chrysler and newly-added non-Ford wedges): generic P0128 + U0100 stubs.
 * - **Ford:** decodes Mode **43** payloads from bundled JSONL (see `planb/gateway/replay/ford_golden.log`).
 *   [fordGatewayDefaultsForVin][com.caseforge.scanner.planb.gateway.fordGatewayDefaultsForVin] picks the Windstar-labelled
 *   PCM scaffold when the wedge resolves **`ford-windstar-2000`**. Replay DTC scaffolding is shared across Ford cards until Windstar splits its oracle.
 */
class GoldenReplaySource(
    val lines: List<String>,
    val marque: PlanbMarque,
) {

    companion object {
        private val sourceCache = ConcurrentHashMap<PlanbMarque, GoldenReplaySource>()

        /** Asset convention: optional per-marque golden snippet under `planb/gateway/replay/`. */
        fun defaultAssetPath(marque: PlanbMarque): String =
            "planb/gateway/replay/${marque.id}_golden.log"

        /** True when the bundled replay log exists for [marque] (Ford bench: `ford_golden.log`). */
        fun hasBundledAsset(context: Context, marque: PlanbMarque): Boolean =
            runCatching {
                context.applicationContext.assets.open(defaultAssetPath(marque)).close()
                true
            }.getOrDefault(false)

        fun fromAsset(context: Context, assetPath: String, marque: PlanbMarque): GoldenReplaySource =
            context.assets.open(assetPath).bufferedReader().use { reader ->
                GoldenReplaySource(reader.readLines().map { it.trimEnd() }, marque)
            }

        /** Test / JVM fixture without assets. */
        fun fromFixtureLines(lines: List<String>, marque: PlanbMarque): GoldenReplaySource =
            GoldenReplaySource(lines, marque)

        /**
         * Load bundled golden lines when present; otherwise keep an empty replay buffer (stub DTC path still works).
         */
        fun loadForMarqueOrEmpty(context: Context, marque: PlanbMarque): GoldenReplaySource =
            sourceCache[marque]
                ?: runCatching { fromAsset(context.applicationContext, defaultAssetPath(marque), marque) }
                    .getOrElse { GoldenReplaySource(emptyList(), marque) }
                    .also { sourceCache[marque] = it }

        /**
         * Returns null when [planbGatewayReplay] is on but the asset file is absent — callers should surface a banner
         * instead of silently using generic stub DTCs.
         */
        fun loadForMarqueIfPresent(context: Context, marque: PlanbMarque): GoldenReplaySource? {
            if (!hasBundledAsset(context.applicationContext, marque)) return null
            return loadForMarqueOrEmpty(context, marque)
        }

        fun preload(context: Context, marque: PlanbMarque) {
            loadForMarqueOrEmpty(context.applicationContext, marque)
        }

        internal fun clearCacheForTest() {
            sourceCache.clear()
        }
    }

    /**
     * Replay DTC scaffold: Ford reads Mode 03 positives from JSONL hex; all other marques use the generic stub path.
     */
    fun syntheticReplayDtcs(): List<BodyDtc> =
        when (marque) {
            PlanbMarque.FORD -> fordReplayBodiesFromGoldenJsonl(lines)
            else -> stellantisStyleReplayStubDtcs()
        }

    /**
     * Single-frame ISO-15765 CAN payload hex (no separators) → OBD-layer byte slice (`0x43`/`0x47` first byte…).
     * Multi-frame scaffold is out of scope for this helper.
     */
    internal fun isoTpSfObdSlice(payloadHex: String): ByteArray? {
        val trimmed = payloadHex.trim().removePrefix("0x").removePrefix("0X")
        if (trimmed.length % 2 != 0 || trimmed.any { !it.isAsciiHexDigit() }) return null
        val full = trimmed.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        if (full.isEmpty()) return null
        val pci = full[0].toInt() and 0xFF
        if (pci and 0xF0 != 0) return null // not SF scaffolding
        val n = pci and 0x0F
        if (n <= 0 || 1 + n > full.size) return null
        return full.copyOfRange(1, 1 + n)
    }

    private fun fordReplayBodiesFromGoldenJsonl(rawLines: List<String>): List<BodyDtc> {
        if (rawLines.isEmpty()) return fordReplayScaffoldFallback()
        val parsed = runCatching { GoldenLogParser.parse(rawLines.joinToString("\n")) }.getOrNull()
            ?: return fordReplayScaffoldFallback()
        for (row in parsed) {
            if (!row.dir.equals("RX", ignoreCase = true)) continue
            val obd = isoTpSfObdSlice(row.payload) ?: continue
            val dtcs = runCatching { ObdDtcReader.parseStored(obd) }.getOrNull()?.takeIf { it.isNotEmpty() }
                ?: continue
            return dtcs.map { d ->
                BodyDtc(code = d.code, description = fordReplayDescription(d.code))
            }
        }
        return fordReplayScaffoldFallback()
    }

    /** Last-resort scaffold when parsing fails — keeps Tier 1 Ford replay non-empty. */
    private fun fordReplayScaffoldFallback(): List<BodyDtc> =
        listOf(BodyDtc(code = "P0102", description = fordReplayDescription("P0102")))

    private fun fordReplayDescription(code: String): String =
        when (code) {
            "P0102" -> "Mass or Volume Air Flow \"A\" Circuit Low (Ford tier-1 replay scaffold)"
            else -> "Gateway replay scaffold ($code)"
        }

    private fun stellantisStyleReplayStubDtcs(): List<BodyDtc> =
        listOf(
            BodyDtc(code = "P0128", description = "Coolant thermostat (generic)"),
            BodyDtc(code = "U0100", description = "Lost communication with ECM/PCM \"A\" (generic)"),
        )

    private fun Char.isAsciiHexDigit(): Boolean =
        this in '0'..'9' || this in 'A'..'F' || this in 'a'..'f'
}
