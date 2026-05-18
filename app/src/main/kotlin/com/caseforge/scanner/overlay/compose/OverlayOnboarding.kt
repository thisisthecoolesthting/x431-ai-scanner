@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.caseforge.scanner.overlay.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * First-launch onboarding overlay pager. Shows 3-4 steps introducing Together Scanners AI
 * and how to drive the X431 overlay.
 *
 * - Step 1: Intro — "Together Scanners AI is now driving X431 underneath this UI"
 * - Step 2: How to drive — "Tap categories → tap capability cards → watch the action log"
 * - Step 3: Emergency dismiss — "Hold any empty area for 3 seconds to dismiss the overlay"
 * - Step 4 (optional): Peek mode — "Tap the eye icon to peek at X431 directly"
 *
 * "Got it" button on the final step persists the flag and dismisses.
 * "Don't show again" checkbox on every step for early exit.
 */
@Composable
fun OverlayOnboarding(
    onComplete: (dontShowAgain: Boolean) -> Unit,
) {
    val pagerState = rememberPagerState(initialPage = 0) { 4 }
    var dontShowAgain by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.9f),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            // Pager with all steps
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) { page ->
                OnboardingStep(page = page)
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Page indicators (dots)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                repeat(4) { index ->
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(
                                color = if (index == pagerState.currentPage) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                },
                                shape = MaterialTheme.shapes.small,
                            ),
                    )
                    if (index < 3) Spacer(modifier = Modifier.width(8.dp))
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // "Don't show again" checkbox
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start,
            ) {
                Checkbox(
                    checked = dontShowAgain,
                    onCheckedChange = { dontShowAgain = it },
                    modifier = Modifier.size(24.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Don't show again",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Navigation buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Back button (hidden on first page)
                if (pagerState.currentPage > 0) {
                    Button(
                        onClick = {
                            // Navigate to previous page
                            // Note: In a real impl, use scope.launch { pagerState.animateScrollToPage(...) }
                            // For now, synchronous is acceptable in tests; in production, wrap in LaunchedEffect
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(),
                    ) {
                        Text("Back")
                    }
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                }

                // Next or Got it button
                Button(
                    onClick = {
                        if (pagerState.currentPage < 3) {
                            // Move to next page (should be driven by scope.launch { pagerState.animateScrollToPage(...) })
                        } else {
                            // Final page: complete onboarding
                            onComplete(dontShowAgain)
                        }
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        if (pagerState.currentPage < 3) "Next" else "Got it",
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/**
 * Individual onboarding step content. Each page shows a title and description.
 */
@Composable
private fun OnboardingStep(page: Int) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        when (page) {
            0 -> {
                // Step 1: Intro
                Text(
                    text = "Together Scanners AI",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "is now driving X431 underneath this UI",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.fillMaxWidth(0.85f),
                )
            }

            1 -> {
                // Step 2: How to drive
                Text(
                    text = "How to Drive It",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.height(16.dp))
                Column(
                    modifier = Modifier.fillMaxWidth(0.85f),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    BulletPoint("Tap categories")
                    BulletPoint("Tap capability cards")
                    BulletPoint("Watch the action log")
                }
            }

            2 -> {
                // Step 3: Emergency dismiss
                Text(
                    text = "Emergency Dismiss",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Hold any empty area for 3 seconds to dismiss the overlay",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.fillMaxWidth(0.85f),
                )
            }

            3 -> {
                // Step 4: Peek mode
                Icon(
                    imageVector = Icons.Default.Visibility,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Peek Mode",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Tap the eye icon to peek at X431 directly",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.fillMaxWidth(0.85f),
                )
            }
        }
    }
}

/**
 * Helper composable for bullet points in the onboarding steps.
 */
@Composable
private fun BulletPoint(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "•",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
