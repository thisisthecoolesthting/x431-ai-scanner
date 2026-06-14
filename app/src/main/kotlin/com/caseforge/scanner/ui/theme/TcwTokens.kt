package com.caseforge.scanner.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

object TcwTokens {

    // Brand palette
    val Amber = Color(0xFFE07A1F)
    val OnAmber = Color(0xFF412402)
    val AmberSubtle = Color(0xFFFAEEDA)

    // Blue
    val Blue = Color(0xFF185FA5)
    val OnBlue = Color(0xFFFFFFFF)
    val BlueSubtle = Color(0xFFE6F1FB)

    // Green
    val Green = Color(0xFF1D9E75)
    val OnGreen = Color(0xFFFFFFFF)
    val GreenSubtle = Color(0xFFE1F5EE)

    // Red
    val Red = Color(0xFFD64545)
    val OnRed = Color(0xFFFFFFFF)
    val RedSubtle = Color(0xFFFCEBEB)

    // Neutral
    val Ink = Color(0xFF1A1A1A)
    val OnInk = Color(0xFFFFFFFF)
    val Muted = Color(0xFF7A7A7A)

    // Shape tokens
    val RadiusSmall = 12.dp
    val RadiusMedium = 16.dp
    val RadiusLarge = 20.dp

    // Spacing
    val PadScreen = 16.dp
    val PadCard = 14.dp
    val Gap = 10.dp

    fun statusColor(ok: Boolean): Color = if (ok) Green else Red

    fun dtcSeverityColor(severity: String): Color = when (severity.lowercase()) {
        "fault" -> Red
        "warning" -> Amber
        "pass" -> Green
        else -> Blue
    }
}
