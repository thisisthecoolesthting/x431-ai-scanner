package com.caseforge.scanner.ui.history

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.caseforge.scanner.data.AppDatabase
import com.caseforge.scanner.data.SessionEntity
import com.caseforge.scanner.ui.theme.TcwTokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(db: AppDatabase, onBack: () -> Unit) {
    var sessions by remember { mutableStateOf(emptyList<SessionEntity>()) }
    var loaded by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        sessions = withContext(Dispatchers.IO) { db.sessionDao().listAll() }
        loaded = true
    }
    val fmt = remember { SimpleDateFormat("MMM d, yyyy  h:mm a", Locale.US) }
    Column(
        Modifier.fillMaxSize().padding(TcwTokens.PadScreen),
        verticalArrangement = Arrangement.spacedBy(TcwTokens.Gap),
    ) {
        Text(
            "Session history",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        if (loaded && sessions.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    "No sessions yet. Run a scan to see it here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TcwTokens.Muted,
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(TcwTokens.Gap),
                modifier = Modifier.weight(1f),
            ) {
                items(sessions) { s ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(TcwTokens.RadiusMedium),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(TcwTokens.PadCard), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Box(
                                    Modifier.size(8.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Surface(color = TcwTokens.Amber, shape = RoundedCornerShape(50)) { Box(Modifier.size(8.dp)) }
                                }
                                Text(
                                    s.vin ?: "Unknown vehicle",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                            Text(
                                fmt.format(Date(s.startedAt)),
                                style = MaterialTheme.typography.bodySmall,
                                color = TcwTokens.Muted,
                            )
                            s.rootCause?.let {
                                Text("Root cause: $it", style = MaterialTheme.typography.bodySmall)
                            }
                            s.recommendedRepair?.let {
                                Text("Repair: $it", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Back") }
    }
}
