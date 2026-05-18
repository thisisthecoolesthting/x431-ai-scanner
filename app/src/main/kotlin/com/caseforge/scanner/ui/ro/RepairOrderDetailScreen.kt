@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.caseforge.scanner.ui.ro

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import com.caseforge.scanner.data.AppDatabase
import com.caseforge.scanner.data.CustomerEntity
import com.caseforge.scanner.data.RepairOrderEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun RepairOrderDetailScreen(
    db: AppDatabase,
    roId: Long,
    onBack: () -> Unit,
    onGenerateInvoice: (Long) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var ro by remember { mutableStateOf<RepairOrderEntity?>(null) }
    var customer by remember { mutableStateOf<CustomerEntity?>(null) }
    var loading by remember { mutableStateOf(true) }
    var working by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("open") }

    var laborHoursStr by remember { mutableStateOf("0.0") }
    var partsTotalStr by remember { mutableStateOf("0.0") }
    var laborRateStr by remember { mutableStateOf("0.0") }

    fun reload(roLocal: Long) {
        scope.launch {
            loading = true
            val (loadedRo, loadedCustomer) = withContext(Dispatchers.IO) {
                val r = db.sessionDao().listRepairOrders().firstOrNull { it.id == roLocal }
                val c = r?.customerId?.let { cid ->
                    db.sessionDao().listCustomers().firstOrNull { it.id == cid }
                }
                r to c
            }
            ro = loadedRo
            customer = loadedCustomer
            if (loadedRo != null) {
                status = loadedRo.status
                laborHoursStr = formatNum(loadedRo.laborHours)
                partsTotalStr = formatNum(loadedRo.partsTotal)
                laborRateStr = formatNum(loadedRo.laborRate)
            }
            loading = false
        }
    }

    LaunchedEffect(roId) { reload(roId) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Repair order #$roId") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        if (loading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
            return@Scaffold
        }

        val current = ro
        if (current == null) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("Repair order not found.")
            }
            return@Scaffold
        }

        val laborHoursNum = laborHoursStr.toDoubleOrNull() ?: 0.0
        val laborRateNum = laborRateStr.toDoubleOrNull() ?: 0.0
        val partsTotalNum = partsTotalStr.toDoubleOrNull() ?: 0.0
        val laborCost = laborHoursNum * laborRateNum
        val totalCost = laborCost + partsTotalNum
        val isClosed = status == "completed" || status == "invoiced"

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Header card: vehicle + customer + symptom + status
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(
                    Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "RO #${current.id}",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f),
                        )
                        StatusPill(status)
                    }
                    val vehicle = current.vehicleSummary?.takeIf { it.isNotBlank() }
                        ?: current.vin?.takeIf { it.isNotBlank() }
                        ?: "Unknown vehicle"
                    LabelRow("Vehicle", vehicle)
                    LabelRow("Customer", customer?.name ?: "Walk-in / unassigned")
                    if (!customer?.phone.isNullOrBlank()) {
                        LabelRow("Phone", customer!!.phone!!)
                    }
                    LabelRow("Symptom", current.symptom?.takeIf { it.isNotBlank() } ?: "—")
                    val fmt = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US) }
                    LabelRow("Opened", fmt.format(Date(current.createdAt)))
                    current.closedAt?.let {
                        LabelRow("Closed", fmt.format(Date(it)))
                    }
                }
            }

            // Editable charges
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(
                    Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("Charges", style = MaterialTheme.typography.titleSmall)
                    OutlinedTextField(
                        value = laborHoursStr,
                        onValueChange = { laborHoursStr = it },
                        label = { Text("Labor hours") },
                        singleLine = true,
                        enabled = !isClosed,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = laborRateStr,
                        onValueChange = { laborRateStr = it },
                        label = { Text("Labor rate ($/hr)") },
                        singleLine = true,
                        enabled = !isClosed,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = partsTotalStr,
                        onValueChange = { partsTotalStr = it },
                        label = { Text("Parts total ($)") },
                        singleLine = true,
                        enabled = !isClosed,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Divider()
                    LabelRow("Labor cost", "$${"%.2f".format(laborCost)}")
                    LabelRow("Parts", "$${"%.2f".format(partsTotalNum)}")
                    LabelRow("Total", "$${"%.2f".format(totalCost)}")
                    if (isClosed) {
                        Text(
                            "This RO is closed. Charges can't be edited; the existing DAO has no updateRepairOrder.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Text(
                            "Note: charge edits are kept in this screen only — the existing DAO doesn't expose updateRepairOrder, so values aren't persisted until \"Mark Complete\" closes the RO.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // Action buttons
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    enabled = !working && !isClosed,
                    onClick = {
                        working = true
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                db.sessionDao().closeRepairOrder(
                                    id = current.id,
                                    status = "completed",
                                    closedAt = System.currentTimeMillis(),
                                    invoice = null,
                                )
                            }
                            working = false
                            reload(current.id)
                        }
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(if (isClosed) "Completed" else "Mark Complete")
                }
                OutlinedButton(
                    enabled = !working,
                    onClick = { onGenerateInvoice(current.id) },
                    modifier = Modifier.weight(1f),
                ) { Text("Generate Invoice") }
            }

            if (!current.invoiceText.isNullOrBlank()) {
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        Text("Invoice", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(6.dp))
                        Text(current.invoiceText, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun LabelRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(110.dp),
        )
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun StatusPill(status: String) {
    val (bg, fg, label) = when (status) {
        "open" -> Triple(Color(0xFF1565C0), Color.White, "OPEN")
        "in_progress" -> Triple(Color(0xFFEF6C00), Color.White, "IN PROGRESS")
        "completed" -> Triple(Color(0xFF2E7D32), Color.White, "COMPLETED")
        "invoiced" -> Triple(Color(0xFF6A1B9A), Color.White, "INVOICED")
        else -> Triple(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant, status.uppercase(Locale.US))
    }
    Surface(color = bg, contentColor = fg, shape = RoundedCornerShape(50)) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

private fun formatNum(d: Double): String {
    return if (d == d.toLong().toDouble()) d.toLong().toString() else "%.2f".format(d)
}
