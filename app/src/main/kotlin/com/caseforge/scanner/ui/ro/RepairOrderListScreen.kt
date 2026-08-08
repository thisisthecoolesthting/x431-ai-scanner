@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.caseforge.scanner.ui.ro

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.caseforge.scanner.data.AppDatabase
import com.caseforge.scanner.data.RepairOrderEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun RepairOrderListScreen(
    db: AppDatabase,
    onBack: () -> Unit,
    onOpenRO: (Long) -> Unit,
    onNewRO: () -> Unit,
) {
    var allOrders by remember { mutableStateOf(emptyList<RepairOrderEntity>()) }
    var loading by remember { mutableStateOf(true) }
    var tabIndex by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        loading = true
        allOrders = withContext(Dispatchers.IO) { db.sessionDao().listRepairOrders() }
        loading = false
    }

    val (openOrders, completedOrders) = remember(allOrders) {
        allOrders.partition { it.status != "completed" && it.status != "invoiced" }
    }
    val visible = if (tabIndex == 0) openOrders else completedOrders
    val fmt = remember { SimpleDateFormat("MMM d", Locale.US) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Repair orders") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNewRO,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("New RO") },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            TabRow(selectedTabIndex = tabIndex) {
                Tab(
                    selected = tabIndex == 0,
                    onClick = { tabIndex = 0 },
                    text = { Text("Open (${openOrders.size})") },
                )
                Tab(
                    selected = tabIndex == 1,
                    onClick = { tabIndex = 1 },
                    text = { Text("Completed (${completedOrders.size})") },
                )
            }

            when {
                loading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                visible.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                if (tabIndex == 0) "No open repair orders" else "No completed repair orders",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Spacer(Modifier.height(8.dp))
                            if (tabIndex == 0) {
                                Text(
                                    "Tap \"New RO\" to start one.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(visible, key = { it.id }) { ro ->
                            ElevatedCard(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onOpenRO(ro.id) },
                            ) {
                                Column(Modifier.padding(14.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            "RO #${ro.id}",
                                            style = MaterialTheme.typography.titleSmall,
                                            modifier = Modifier.weight(1f),
                                        )
                                        StatusBadge(ro.status)
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    val vehicle = ro.vehicleSummary?.takeIf { it.isNotBlank() }
                                        ?: ro.vin?.takeIf { it.isNotBlank() }
                                        ?: "Unknown vehicle"
                                    Text(vehicle, style = MaterialTheme.typography.bodyMedium)
                                    if (!ro.symptom.isNullOrBlank()) {
                                        Text(
                                            ro.symptom,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 2,
                                        )
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        "Opened ${fmt.format(Date(ro.createdAt))}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(status: String) {
    val (bg, fg, label) = when (status) {
        "open" -> Triple(Color(0xFF1565C0), Color.White, "OPEN")
        "in_progress" -> Triple(Color(0xFFEF6C00), Color.White, "IN PROGRESS")
        "completed" -> Triple(Color(0xFF2E7D32), Color.White, "COMPLETED")
        "invoiced" -> Triple(Color(0xFF6A1B9A), Color.White, "INVOICED")
        else -> Triple(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant, status.uppercase(Locale.US))
    }
    Surface(
        color = bg,
        contentColor = fg,
        shape = RoundedCornerShape(50),
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
        )
    }
}
