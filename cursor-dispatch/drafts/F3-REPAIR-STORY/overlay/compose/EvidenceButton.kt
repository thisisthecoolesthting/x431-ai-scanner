package com.caseforge.scanner.overlay.compose

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.caseforge.scanner.engine.EngineState
import com.caseforge.scanner.evidence.EvidenceType

/**
 * Floating "Capture Evidence" button displayed during active diagnostics.
 *
 * Mount inside OverlayRoot (or DiagnosisPane) at the bottom-end corner.
 * The button expands to a type-selector sheet on tap; each option either
 * snapshots engine state (BEFORE/FIX/AFTER) or opens the camera.
 *
 * @param engineState  Current EngineState from the StateFlow — snapshotted on confirm.
 * @param ticketId     Active repair ticket id (from TicketViewModel or ambient).
 * @param onBookmark   Called when the user confirms a data bookmark.
 *                     Params: (type, label, state). Caller delegates to EvidenceCapture.
 * @param onCamera     Called when the user wants to take a photo.
 *                     Params: (type, label). Caller launches PhotoCapture.
 * @param modifier     Optional Modifier; default positions to bottom-end.
 */
@Composable
fun EvidenceButton(
    engineState: EngineState,
    ticketId: String,
    onBookmark: (type: EvidenceType, label: String, state: EngineState) -> Unit,
    onCamera: (type: EvidenceType, label: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var sheetVisible by remember { mutableStateOf(false) }

    Box(modifier = modifier) {

        // -----------------------------------------------------------------
        // FAB — always visible during diag
        // -----------------------------------------------------------------
        FloatingActionButton(
            onClick = { sheetVisible = !sheetVisible },
            shape = CircleShape,
            containerColor = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 12.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.CameraAlt,
                    contentDescription = "Capture evidence",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "Capture",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        // -----------------------------------------------------------------
        // Type-selector bottom sheet (slides up from FAB)
        // -----------------------------------------------------------------
        AnimatedVisibility(
            visible = sheetVisible,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 }),
            modifier = Modifier.align(Alignment.BottomEnd),
        ) {
            EvidenceTypeSheet(
                engineState = engineState,
                onDismiss = { sheetVisible = false },
                onBookmark = { type, label ->
                    sheetVisible = false
                    onBookmark(type, label, engineState)
                },
                onCamera = { type, label ->
                    sheetVisible = false
                    onCamera(type, label)
                },
            )
        }
    }
}

// -------------------------------------------------------------------------
// Type-selector sheet
// -------------------------------------------------------------------------

/**
 * Bottom-sheet content listing BEFORE / FIX / AFTER choices.
 * Each row has two actions: "Bookmark data" and "Take photo".
 */
@Composable
private fun EvidenceTypeSheet(
    engineState: EngineState,
    onDismiss: () -> Unit,
    onBookmark: (EvidenceType, String) -> Unit,
    onCamera: (EvidenceType, String) -> Unit,
) {
    val options = listOf(
        EvidenceOption(
            type = EvidenceType.BEFORE,
            emoji = "🔍",
            title = "Before repair",
            subtitle = "Record fault codes & sensor readings at intake",
            defaultLabel = "Before repair — intake scan",
        ),
        EvidenceOption(
            type = EvidenceType.FIX,
            emoji = "🔧",
            title = "During repair",
            subtitle = "Checkpoint while work is in progress",
            defaultLabel = "During repair",
        ),
        EvidenceOption(
            type = EvidenceType.AFTER,
            emoji = "✅",
            title = "After repair",
            subtitle = "Verify codes cleared and readings normalised",
            defaultLabel = "After repair — post-check",
        ),
    )

    Surface(
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        tonalElevation = 8.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 80.dp), // clears the FAB
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "What are you capturing?",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "${engineState.dtcs.size} code(s) active • " +
                    "${engineState.liveData.size} live pid(s)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))

            options.forEach { opt ->
                EvidenceOptionRow(
                    option = opt,
                    onBookmark = { onBookmark(opt.type, opt.defaultLabel) },
                    onCamera   = { onCamera(opt.type, opt.defaultLabel) },
                )
                Spacer(Modifier.height(8.dp))
            }

            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Cancel")
            }
        }
    }
}

@Composable
private fun EvidenceOptionRow(
    option: EvidenceOption,
    onBookmark: () -> Unit,
    onCamera: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "${option.emoji}  ${option.title}",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = option.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Button(
                    onClick = onBookmark,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                ) {
                    Text("Bookmark data", fontSize = 12.sp)
                }
                Button(
                    onClick = onCamera,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    ),
                ) {
                    Icon(
                        Icons.Filled.CameraAlt,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Take photo", fontSize = 12.sp)
                }
            }
        }
    }
}

private data class EvidenceOption(
    val type: EvidenceType,
    val emoji: String,
    val title: String,
    val subtitle: String,
    val defaultLabel: String,
)
