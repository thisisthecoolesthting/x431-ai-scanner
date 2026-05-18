package com.caseforge.scanner.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

/**
 * CaseForge theme. Accepts an optional mode override:
 *   - "system" (default) → follows device
 *   - "light"  → always light
 *   - "dark"   → always dark
 */
@Composable
fun CaseForgeTheme(mode: String = "system", content: @Composable () -> Unit) {
    val dark = when (mode.lowercase()) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    }
    val colors = if (dark) darkColorScheme() else lightColorScheme()
    MaterialTheme(colorScheme = colors, content = content)
}
