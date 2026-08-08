@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.caseforge.scanner.ui.main

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.caseforge.scanner.ai.NhtsaLookup
import com.caseforge.scanner.transfer.SessionEventLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun RecallsScreen(
    vin: String?,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var text by remember { mutableStateOf("Loading NHTSA data…") }
    var busy by remember { mutableStateOf(true) }
    var stubLogged by remember(vin) { mutableStateOf(false) }

    fun logStubEventIfNeeded() {
        if (stubLogged) return
        val effectiveVin = vin?.trim().orEmpty().ifBlank { "unknown_vin" }
        val sessionId = "recall_stub_${effectiveVin}_${System.currentTimeMillis()}"
        SessionEventLogger.log(
            context = context,
            sessionId = sessionId,
            kind = "recall_tsb_lookup_stub_opened",
            detail = effectiveVin,
            extra = mapOf("source" to "recalls_screen"),
        )
        stubLogged = true
    }

    LaunchedEffect(vin) {
        busy = true
        text = if (vin.isNullOrBlank()) {
            "Connect VCI and read a VIN first."
        } else {
            withContext(Dispatchers.IO) {
                NhtsaLookup().decodeAndRecalls(vin)
            }
        }
        busy = false
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Recalls / TSB") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            },
        )
        if (busy) {
            Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Card(Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("Check recalls", style = MaterialTheme.typography.titleSmall)
                        Text(
                            if (vin.isNullOrBlank()) {
                                "VIN required for direct recall search."
                            } else {
                                "Open NHTSA recall search for this VIN and review bulletin coverage."
                            },
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Button(
                            onClick = {
                                logStubEventIfNeeded()
                                val target = if (vin.isNullOrBlank()) {
                                    "https://www.nhtsa.gov/recalls"
                                } else {
                                    "https://www.nhtsa.gov/recalls?vin=${Uri.encode(vin)}"
                                }
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(target)),
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Open NHTSA recalls")
                        }
                        TextButton(
                            onClick = { logStubEventIfNeeded() },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Log session event only")
                        }
                    }
                }
            }
        }
    }
}
