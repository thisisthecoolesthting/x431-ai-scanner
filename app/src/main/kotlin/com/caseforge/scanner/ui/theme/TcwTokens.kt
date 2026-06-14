package com.caseforge.scanner.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Premium design token object for Together Car Works (TCW) UI.
 *
 * Usage: import com.caseforge.scanner.ui.theme.TcwTokens
 * Then reference TcwTokens.Amber, TcwTokens.Blue, etc.
 *
 * These tokens sit on top of Material3 colorScheme — use them for
 * brand-specific accents (amber, charcoal) and semantic status colours.
 * For general surface/background, defer to MaterialTheme.colorScheme.
 */
object TcwTokens {

    // ── Brand palette ──────────────────────────────────────────────────────────

    /** Primary brand accent: warm amber / workshop sodium-light tone. */
    val Amber = Color(0xFFE07A1F)

    /** On-amber text / icon (high contrast on Amber fill). */
    val OnAmber = Color(0xFF1A1A1A)

    /** Amber at reduced opacity — used for tinted backgrounds. */
    val AmberSubtle = Color(0x1FE07A1F)

    // ── Supporting semantic palette ────────────────────────────────────────────

    /** Technical / informational blue. */
    val Blue = Color(0xFF185FA5)

    /** On-blue (white) */
    val OnBlue = Color(0xFFFFFFFF)

    /** Blue at reduced opacity — used for info card tints. */
    val BlueSubtle = Color(0x1F185FA5)

    /** Success / pass green. */
    val Green = Color(0xFF1D9E75)

    /** On-green (white) */
    val OnGreen = Color(0xFFFFFFFF)

    /** Green at reduced opacity — used for pass badge tints. */
    val GreenSubtle = Color(0x1F1D9E75)

    /** Danger / fault red. */
    val Red = Color(0xFFD64545)

    /** On-red (white) */
    val OnRed = Color(0xFFFFFFFF)

    /** Red at reduced opacity — used for fault badge tints. */
    val RedSubtle = Color(0x1FD64545)

    // ── Neutral / ink ─────────────────────────────────────────────────────────

    /** Deep charcoal — used for dark card fills. */
    val Ink = Color(0xFF1A1A1A)

    /** On-ink (white) */
    val OnInk = Color(0xFFFFFFFF)

    /** Muted label colour — secondary text on light surfaces. */
    val Muted = Color(0xFF7A7A7A)

    // ── Shape tokens ──────────────────────────────────────────────────────────

    /** Small corner radius: chips, badges, tool buttons. */
    val RadiusSmall = 12.dp

    /** Medium corner radius: cards, metric tiles. */
    val RadiusMedium = 16.dp

    /** Large corner radius: sheets, header bars, full-bleed cards. */
    val RadiusLarge = 20.dp

    // ── Spacing / layout tokens ───────────────────────────────────────────────

    /** Horizontal/vertical screen-edge padding. */
    val PadScreen = 16.dp

    /** Internal card padding. */
    val PadCard = 14.dp

    /** Gap between sibling elements in a row or column. */
    val Gap = 10.dp

    // ── DTC severity colours ──────────────────────────────────────────────────

    /**
     * Returns the appropriate colour for a DTC severity level.
     *
     * @param severity  "fault"    → danger red   (active DTC, MIL-on)
     *                  "warning"  → amber         (pending / history DTC)
     *                  "info"     → technical blue (not-scanned / info code)
     *                  "pass"     → success green  (module passed / no DTC)
     *                  else       → muted grey     (unknown)
     */
    fun dtcSeverityColor(severity: String): Color = when (severity.lowercase()) {
        "fault"   -> Red
        "warning" -> Amber
        "info"    -> Blue
        "pass"    -> Green
        else      -> Muted
    }

    /**
     * Simple boolean helper: green when ok, red when not.
     */
    fun statusColor(ok: Boolean): Color = if (ok) Green else Red
}
