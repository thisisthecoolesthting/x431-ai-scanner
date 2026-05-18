package com.caseforge.scanner.overlay.compose

import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CardElevation
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Shape

/**
 * Unified card styling for the overlay UI.
 *
 * All Card composables use:
 * - shape = TogetherCardShape
 * - elevation = togetherCardElevation()
 * - colors = togetherCardColors()
 */

val TogetherCardShape: Shape = RoundedCornerShape(Spacing.Space12)

@Composable
fun togetherCardElevation(): CardElevation {
    return CardDefaults.cardElevation(defaultElevation = 2.dp)
}

@Composable
fun togetherCardColors() = CardDefaults.cardColors(
    containerColor = MaterialTheme.colorScheme.surface,
    contentColor = MaterialTheme.colorScheme.onSurface,
)
