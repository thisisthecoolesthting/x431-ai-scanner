package com.caseforge.scanner.overlay.compose

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.caseforge.scanner.ai.RecallMatch

@Composable
fun RecallBanner(recalls: List<RecallMatch>, modifier: Modifier = Modifier) {
    if (recalls.isEmpty()) return
    var isExpanded by remember { mutableStateOf(false) }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.errorContainer, MaterialTheme.shapes.medium)
            .clickable { isExpanded = !isExpanded }
            .padding(Spacing.Space12),
        verticalArrangement = Arrangement.spacedBy(Spacing.Space8),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.Space8),
        ) {
            Icon(Icons.Default.WarningAmber, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(Spacing.Space20))
            Text(
                "${recalls.size} open recall${if (recalls.size != 1) "s" else ""} related to this fault",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
        AnimatedVisibility(isExpanded, enter = expandVertically(), exit = shrinkVertically()) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.Space8)) {
                recalls.forEach { r ->
                    Text("Campaign ${r.campaignId}: ${r.summary}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
