@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.caseforge.scanner.ui.theme

import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * CaseForge Material3 theme with dynamic color support on Android 12+.
 *
 * On Android 12+: Uses system dynamic color via dynamicLightColorScheme() / dynamicDarkColorScheme().
 * On Android < 12: Falls back to brand color scheme defined in Color.kt.
 *
 * @param mode "light" or "dark" (default "dark" for overlay context)
 * @param isDarkMode If true, applies dark theme; if false, applies light theme.
 *   For backward compat, [mode] still works, but [isDarkMode] takes precedence if explicitly passed.
 * @param content Composable content to wrap with theme.
 */
@Composable
fun CaseForgeTheme(
    mode: String = "dark",
    isDarkMode: Boolean? = null,
    content: @Composable () -> Unit,
) {
    val effectiveIsDark = isDarkMode
        ?: (mode.lowercase() == "dark")

    val colorScheme: ColorScheme = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        // Android 12+ with dynamic color
        val context = LocalContext.current
        if (effectiveIsDark) {
            dynamicDarkColorScheme(context)
        } else {
            dynamicLightColorScheme(context)
        }
    } else {
        // Fallback for older Android: brand colors from Color.kt
        if (effectiveIsDark) {
            com.caseforge.scanner.ui.theme.darkColorScheme
        } else {
            com.caseforge.scanner.ui.theme.lightColorScheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = CaseForgeTypography,
        content = content,
    )
}
