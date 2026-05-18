package com.caseforge.scanner.engine

import com.caseforge.scanner.agent.ScreenSnapshot

/**
 * Pattern-match an X431 accessibility ScreenSnapshot into a typed [EngineState].
 *
 * Pure function. Never throws. Returns `ScreenKind.Unknown(hint)` for anything we
 * don't recognize, so the BehindCurtainPane can still help the user.
 *
 * Heuristics live here intentionally — when X431 ships an update and a menu changes,
 * this file is the only one we patch.
 */
object EngineScraper {

    private val VIN_REGEX = Regex("\\b[A-HJ-NPR-Z0-9]{17}\\b")
    private val DTC_REGEX = Regex("\\b([PCBU][0-9A-F]{4})\\b")

    fun scrape(snapshot: ScreenSnapshot, vinHint: String? = null): EngineState {
        val pkg = snapshot.pkg.orEmpty()
        if (pkg.isBlank() || pkg !in KNOWN_X431_PKGS) {
            return EngineState(
                screen = ScreenKind.NoEngine,
                updatedAtMs = System.currentTimeMillis(),
                raw = snapshot,
            )
        }

        val text = snapshot.text
        val low = text.lowercase()
        val vin = vinHint ?: VIN_REGEX.find(text)?.value
        val dtcs = DTC_REGEX.findAll(text).map { Dtc(it.groupValues[1]) }.distinctBy { it.code }.toList()
        val busy = anyOf(low, "scanning", "communicating", "please wait", "loading")

        val kind: ScreenKind = when {
            anyOf(low, "auto scan complete", "scan complete", "scan finished") -> ScreenKind.FullScanResults
            busy && anyOf(low, "auto scan", "full scan", "scanning all") -> ScreenKind.FullScanProgress
            anyOf(low, "live data", "data stream") -> ScreenKind.LiveDataView
            anyOf(low, "actuation", "active test", "actuator test") -> ScreenKind.ActuationTest
            anyOf(low, "diagnostic trouble code", "fault code") -> ScreenKind.DtcDetail
            anyOf(low, "diagnose", "system selection", "control unit") -> ScreenKind.DiagnoseMenu
            anyOf(low, "vehicle selection", "select vehicle", "choose model") -> ScreenKind.VehicleSelect
            anyOf(low, "settings", "configure") && !anyOf(low, "vehicle") -> ScreenKind.Settings
            isDialog(snapshot) -> ScreenKind.Dialog(snapshot.text.lineSequence().firstOrNull().orEmpty().take(80))
            anyOf(low, "diagnose", "service", "module program", "remote diagnosis") -> ScreenKind.HomeMenu
            else -> ScreenKind.Unknown(hint = snapshot.text.take(60))
        }

        return EngineState(
            screen = kind,
            vehicleVin = vin,
            currentMenuPath = inferMenuPath(snapshot),
            dtcs = dtcs,
            busy = busy,
            updatedAtMs = System.currentTimeMillis(),
            raw = snapshot,
        )
    }

    private fun anyOf(haystack: String, vararg needles: String): Boolean =
        needles.any { haystack.contains(it) }

    private fun isDialog(snap: ScreenSnapshot): Boolean {
        // X431 dialogs are short, often contain "OK" / "Cancel" / "Confirm" buttons.
        val buttonish = snap.nodes.count { it.clickable && it.text.length in 1..20 }
        val low = snap.text.lowercase()
        return buttonish <= 4 && (
            anyOf(low, "ok", "cancel", "confirm", "yes", "no") && snap.text.length < 400
        )
    }

    /**
     * Best-effort breadcrumb: top-of-screen text that looks like a back-arrow + title.
     */
    private fun inferMenuPath(snap: ScreenSnapshot): List<String> {
        // Heuristic: take the top-most short non-button text rows.
        return snap.nodes
            .asSequence()
            .filter { it.text.isNotBlank() && it.text.length in 2..40 && !it.clickable }
            .sortedBy { it.bounds.getOrElse(1) { Int.MAX_VALUE } } // by top coordinate
            .map { it.text.trim() }
            .distinct()
            .take(3)
            .toList()
    }

    private val KNOWN_X431_PKGS = setOf(
        "com.cnlaunch.x431padv",
        "com.cnlaunch.x431padv2",
        "com.cnlaunch.diagnose.x431pro",
        "com.cnlaunch.diagnosemodule",
        "com.cnlaunch.x431pro",
        "com.x431.diagnose",
        // Phase-2 clone package — will be populated when we ship the rebrand.
        "com.caseforge.launchai.engine",
    )
}
