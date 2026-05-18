@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.caseforge.scanner.ui.main

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.caseforge.scanner.ai.NhtsaLookup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun RecallsScreen(
    vin: String?,
    onBack: () -> Unit,
) {
    var text by remember { mutableStateOf("Loading NHTSA data…") }
    var busy by remember { mutableStateOf(true) }

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
            Text(
                text,
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
