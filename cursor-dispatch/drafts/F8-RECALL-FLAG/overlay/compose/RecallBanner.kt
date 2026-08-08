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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.caseforge.scanner.ai.RecallMatch
import com.caseforge.scanner.ui.theme.CaseForgeTheme

/**
 * Recall banner shown on ReportScreen when NHTSA recalls are detected.
 *
 * Displays a collapsed summary ("X open recalls related to this fault") that expands
 * on tap to show full recall details including campaign ID, defect summary, and
 * related DTC codes.
 *
 * Uses MaterialTheme colors: warning color for the banner background, primary text.
 * Fully integrated with Compose Material 3 theming.
 */
@Composable
fun RecallBanner(
    recalls: List<RecallMatch>,
    modifier: Modifier = Modifier,
) {
    if (recalls.isEmpty()) return

    var isExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = MaterialTheme.shapes.medium
            )
            .clickable { isExpanded = !isExpanded }
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Collapsed summary row
        Row(
            modifier = Modifier
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Default.WarningAmber,
                contentDescription = "Recalls found",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(20.dp),
            )
            Column(Modifier.weight(1f)) {
                Text(
                    "${recalls.size} open recall${if (recalls.size != 1) "s" else ""} related to this fault",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                Text(
                    if (isExpanded) "Tap to collapse" else "Tap to expand",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }

        // Expanded details
        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically(),
            exit = shrinkVertically(),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Divider(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.2f),
                )

                recalls.forEach { recall ->
                    RecallDetailCard(recall)
                }
            }
        }
    }
}

/**
 * Individual recall detail card shown when banner is expanded.
 */
@Composable
private fun RecallDetailCard(recall: RecallMatch) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.05f),
                shape = MaterialTheme.shapes.small
            )
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // Campaign ID header
        Text(
            "Campaign ${recall.campaignId}",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onErrorContainer,
        )

        // Defect summary
        if (recall.summary.isNotEmpty()) {
            Text(
                recall.summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }

        // Related DTC codes
        if (recall.relatedDtcs.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    "Related codes:",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                Text(
                    recall.relatedDtcs.joinToString(", "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }

        // Manufacturer/Model info (optional)
        if (recall.make.isNotEmpty() && recall.model.isNotEmpty()) {
            Text(
                "${recall.modelYear} ${recall.make} ${recall.model}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Previews
// ---------------------------------------------------------------------------

@Preview(showBackground = true)
@Composable
private fun RecallBannerCollapsedPreview() {
    CaseForgeTheme(isDarkMode = false) {
        Surface(color = MaterialTheme.colorScheme.background) {
            RecallBanner(
                recalls = listOf(
                    RecallMatch(
                        campaignId = "07E055000",
                        summary = "Faulty transmission control module may cause loss of power",
                        consequence = "Vehicle may stall without warning",
                        relatedDtcs = listOf("P0700", "P0705"),
                        isOpenForVin = true,
                        manufacturer = "Honda",
                        modelYear = 2020,
                        make = "Honda",
                        model = "Accord",
                    ),
                    RecallMatch(
                        campaignId = "19E042000",
                        summary = "Engine control software issue affecting emission system",
                        consequence = "Check engine light may illuminate",
                        relatedDtcs = listOf("P0101"),
                        isOpenForVin = true,
                        manufacturer = "Honda",
                        modelYear = 2020,
                        make = "Honda",
                        model = "Accord",
                    ),
                ),
                modifier = Modifier.padding(12.dp),
            )
        }
    }
}

@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun RecallBannerExpandedPreview() {
    CaseForgeTheme(isDarkMode = true) {
        Surface(color = MaterialTheme.colorScheme.background) {
            var isExpanded by remember { mutableStateOf(true) }
            RecallBanner(
                recalls = listOf(
                    RecallMatch(
                        campaignId = "07E055000",
                        summary = "Faulty transmission control module may cause loss of power",
                        consequence = "Vehicle may stall without warning",
                        relatedDtcs = listOf("P0700", "P0705"),
                        isOpenForVin = true,
                        manufacturer = "Honda",
                        modelYear = 2020,
                        make = "Honda",
                        model = "Accord",
                    ),
                ),
                modifier = Modifier.padding(12.dp),
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun RecallBannerEmptyPreview() {
    CaseForgeTheme(isDarkMode = false) {
        Surface(color = MaterialTheme.colorScheme.background) {
            RecallBanner(
                recalls = emptyList(),
                modifier = Modifier.padding(12.dp),
            )
        }
    }
}
