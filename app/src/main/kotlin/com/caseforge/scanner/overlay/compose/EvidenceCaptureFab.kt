package com.caseforge.scanner.overlay.compose

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.caseforge.scanner.engine.EngineState
import com.caseforge.scanner.evidence.EvidenceType

@Composable
fun EvidenceCaptureFab(
    engineState: EngineState,
    onBookmark: (EvidenceType, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        if (expanded) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = Spacing.Space16, bottom = Spacing.Space72),
                verticalArrangement = Arrangement.spacedBy(Spacing.Space8),
            ) {
                listOf(
                    EvidenceType.BEFORE to "Before repair",
                    EvidenceType.FIX to "During repair",
                    EvidenceType.AFTER to "After repair",
                ).forEach { (type, label) ->
                    FilledTonalButton(onClick = {
                        expanded = false
                        onBookmark(type, label)
                    }) {
                        Text(label, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
        FloatingActionButton(
            onClick = { expanded = !expanded },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(Spacing.Space16),
        ) {
            Icon(Icons.Default.CameraAlt, contentDescription = "Capture evidence")
        }
    }
}
