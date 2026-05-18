@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.caseforge.scanner.ui.fullscan

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.caseforge.scanner.data.AppDatabase
import com.caseforge.scanner.data.DtcEntity
import com.caseforge.scanner.data.SessionEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Renders the DTCs collected by the most recent Full-Scan-all-modules session, grouped by
 * module, with severity color-coding:
 *   • red   — current / active / confirmed codes
 *   • amber — pending codes
 *   • gray  — history / stored / permanent codes
 *
 * Pulls data from Room (`SessionDao.latestByScope("fullscan")` + `dtcsFor(sessionId)`),
 * the same way HistoryScreen reads sessions.
 */
@Composable
fun FullScanResultsScreen(db: AppDatabase, onBack: () -> Unit) {
    var session by remember { mutableStateOf<SessionEntity?>(null) }
    var dtcs by remember { mutableStateOf<List<DtcEntity>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val (s, d) = withContext(Dispatchers.IO) {
            val latest = db.sessionDao().latestByScope("fullscan")
            val codes = latest?.let { db.sessionDao().dtcsFor(it.id) } ?: emptyList()
            latest to codes
        }
        session = s
        dtcs = d
        loaded = true
    }

    val fmt = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US) }

    Column(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Full scan results", style = MaterialTheme.typography.titleLarge)

        when {
            !loaded -> {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            session == null -> {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Text(
                        "No full-scan session yet. Tap \"Full Scan All Modules\" on Home to run one.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            else -> {
                val s = session!!
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text("VIN: ${s.vin ?: "—"}", style = MaterialTheme.typography.titleSmall)
                        Text("Scanned: ${fmt.format(Date(s.startedAt))}")
                        Text("Total DTCs: ${dtcs.size}")
                        val counts = dtcs.groupingBy { severityOf(it.status) }.eachCount()
                        val current = counts[Severity.CURRENT] ?: 0
                        val pending = counts[Severity.PENDING] ?: 0
                        val history = counts[Severity.HISTORY] ?: 0
                        Text(
                            "Current: $current   Pending: $pending   History: $history",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }

                val grouped = remember(dtcs) {
                    dtcs.groupBy { it.module ?: "Unknown module" }
                        .toSortedMap(compareBy { it.lowercase(Locale.US) })
                }

                if (dtcs.isEmpty()) {
                    Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        Text("No DTCs were captured in this scan.")
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        grouped.forEach { (module, codes) ->
                            item(key = "header-$module") {
                                ModuleHeader(module = module, count = codes.size)
                            }
                            items(codes, key = { "${it.id}" }) { dtc ->
                                DtcRow(dtc)
                            }
                        }
                    }
                }
            }
        }

        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Back") }
    }
}

@Composable
private fun ModuleHeader(module: String, count: Int) {
    Row(
        Modifier.fillMaxWidth().padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(module, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        AssistChip(onClick = {}, label = { Text("$count") }, enabled = false)
    }
}

@Composable
private fun DtcRow(dtc: DtcEntity) {
    val sev = severityOf(dtc.status)
    val (badgeColor, badgeText) = when (sev) {
        Severity.CURRENT -> Color(0xFFD32F2F) to "ACTIVE"
        Severity.PENDING -> Color(0xFFF9A825) to "PENDING"
        Severity.HISTORY -> Color(0xFF757575) to "HISTORY"
    }
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier
                    .background(badgeColor, RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    badgeText,
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    dtc.code,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                dtc.description?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium)
                }
                dtc.status?.takeIf { it.isNotBlank() }?.let {
                    Text("Status: $it", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

private enum class Severity { CURRENT, PENDING, HISTORY }

private fun severityOf(status: String?): Severity {
    val s = status?.lowercase(Locale.US)?.trim().orEmpty()
    return when {
        s.contains("pending") -> Severity.PENDING
        s.contains("history") || s.contains("stored") || s.contains("permanent") -> Severity.HISTORY
        // Default unknown → treat as active so the tech sees it.
        else -> Severity.CURRENT
    }
}
