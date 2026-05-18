@file:Suppress("unused")

package com.caseforge.scanner.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Brand fallback colors for Material3 ColorScheme.
 * Used on Android versions < 12 that don't support dynamic color.
 *
 * Palette: Together Scanners AI brand with warm amber/orange accent
 * (workshop sodium-light tone).
 * Dynamic color (Android 12+) will override these via dynamicLightColorScheme() / dynamicDarkColorScheme()
 * with the Together brand seed color.
 */

// Brand colors: Together Scanners AI with warm amber/orange primary
val BrandPrimary = Color(0xFFE07A1F)           // Warm amber/orange (Together brand seed)
val BrandOnPrimary = Color(0xFFFFFFFF)         // White
val BrandPrimaryContainer = Color(0xFFFCD5B2) // Light warm tone
val BrandOnPrimaryContainer = Color(0xFF3E2416) // Dark brown

val BrandSecondary = Color(0xFF5A6A6B)       // Slate gray
val BrandOnSecondary = Color(0xFFFFFFFF)     // White
val BrandSecondaryContainer = Color(0xFFDDE5E5) // Light gray
val BrandOnSecondaryContainer = Color(0xFF1B2425) // Dark gray

val BrandTertiary = Color(0xFF7D5D7F)        // Muted purple
val BrandOnTertiary = Color(0xFFFFFFFF)      // White
val BrandTertiaryContainer = Color(0xFFF0DDF5) // Light purple
val BrandOnTertiaryContainer = Color(0xFF2A1A2C) // Dark purple

val BrandError = Color(0xFFD32F2F)           // Material red
val BrandOnError = Color(0xFFFFFFFF)         // White
val BrandErrorContainer = Color(0xFFF8D7DA) // Light red
val BrandOnErrorContainer = Color(0xFF5F0B0F) // Dark red

val BrandBackground = Color(0xFFFAFAFA)      // Off-white
val BrandOnBackground = Color(0xFF1A1A1A)    // Almost black
val BrandSurface = Color(0xFFFFFFFF)         // Pure white
val BrandOnSurface = Color(0xFF1A1A1A)       // Almost black
val BrandSurfaceVariant = Color(0xFFE8E8E8) // Light gray
val BrandOnSurfaceVariant = Color(0xFF4A4A4A) // Medium gray
val BrandOutline = Color(0xFF7A7A7A)        // Dark gray
val BrandOutlineVariant = Color(0xFFC5C5C5) // Light outline

// Dark theme color scheme (Material3 fallback)
val darkColorScheme = darkColorScheme(
    primary = BrandPrimary,
    onPrimary = BrandOnPrimary,
    primaryContainer = BrandPrimaryContainer,
    onPrimaryContainer = BrandOnPrimaryContainer,
    secondary = BrandSecondary,
    onSecondary = BrandOnSecondary,
    secondaryContainer = BrandSecondaryContainer,
    onSecondaryContainer = BrandOnSecondaryContainer,
    tertiary = BrandTertiary,
    onTertiary = BrandOnTertiary,
    tertiaryContainer = BrandTertiaryContainer,
    onTertiaryContainer = BrandOnTertiaryContainer,
    error = BrandError,
    onError = BrandOnError,
    errorContainer = BrandErrorContainer,
    onErrorContainer = BrandOnErrorContainer,
    background = Color(0xFF121212),
    onBackground = Color(0xFFE0E0E0),
    surface = Color(0xFF1E1E1E),
    onSurface = Color(0xFFE0E0E0),
    surfaceVariant = Color(0xFF3A3A3A),
    onSurfaceVariant = Color(0xFFB0B0B0),
    outline = Color(0xFF7A7A7A),
    outlineVariant = Color(0xFF4A4A4A),
)

// Light theme color scheme (Material3 fallback)
val lightColorScheme = lightColorScheme(
    primary = BrandPrimary,
    onPrimary = BrandOnPrimary,
    primaryContainer = BrandPrimaryContainer,
    onPrimaryContainer = BrandOnPrimaryContainer,
    secondary = BrandSecondary,
    onSecondary = BrandOnSecondary,
    secondaryContainer = BrandSecondaryContainer,
    onSecondaryContainer = BrandOnSecondaryContainer,
    tertiary = BrandTertiary,
    onTertiary = BrandOnTertiary,
    tertiaryContainer = BrandTertiaryContainer,
    onTertiaryContainer = BrandOnTertiaryContainer,
    error = BrandError,
    onError = BrandOnError,
    errorContainer = BrandErrorContainer,
    onErrorContainer = BrandOnErrorContainer,
    background = BrandBackground,
    onBackground = BrandOnBackground,
    surface = BrandSurface,
    onSurface = BrandOnSurface,
    surfaceVariant = BrandSurfaceVariant,
    onSurfaceVariant = BrandOnSurfaceVariant,
    outline = BrandOutline,
    outlineVariant = BrandOutlineVariant,
)
