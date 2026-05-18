package com.caseforge.scanner.ui.history

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.caseforge.scanner.data.AppDatabase
import com.caseforge.scanner.data.SessionEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(db: AppDatabase, onBack: () -> Unit) {
    var sessions by remember { mutableStateOf(emptyList<SessionEntity>()) }
    LaunchedEffect(Unit) {
        sessions = withContext(Dispatchers.IO) { db.sessionDao().listAll() }
    }
    val fmt = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US) }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Session history", style = MaterialTheme.typography.titleMedium)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
            items(sessions) { s ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text("VIN: ${s.vin ?: "—"}", style = MaterialTheme.typography.titleSmall)
                        Text("Started: ${fmt.format(Date(s.startedAt))}")
                        s.rootCause?.let { Text("Root cause: $it") }
                        s.recommendedRepair?.let { Text("Repair: $it") }
                    }
                }
            }
        }
        Button(onClick = onBack) { Text("Back") }
    }
}
