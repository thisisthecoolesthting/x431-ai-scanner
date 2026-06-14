@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.caseforge.scanner.ui.diag

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.caseforge.scanner.diagnostics.UsbConnectLab
import com.caseforge.scanner.ui.theme.TcwTokens
import kotlinx.coroutines.launch

class ConnectLabActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ConnectLabScreen(onBack = { finish() }) }
    }
}

private data class LiveRun(
    val transport: UsbConnectLab.Transport,
    val steps: MutableList<UsbConnectLab.Step>,
    var report: UsbConnectLab.Report? = null,
    var running: Boolean = false,
)

@Composable
fun ConnectLabScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val runs = remember {
        mutableStateListOf(
            LiveRun(UsbConnectLab.Transport.USB, mutableStateListOf()),
            LiveRun(UsbConnectLab.Transport.BLUETOOTH, mutableStateListOf()),
            LiveRun(UsbConnectLab.Transport.OEM_VCI, mutableStateListOf()),
        )
    }
    var anyRunning by remember { mutableStateOf(false) }
    var tick by remember { mutableStateOf(0) }

    fun runOne(idx: Int) {
        val r = runs[idx]
        r.steps.clear(); r.report = null; r.running = true; anyRunning = true; tick++
        scope.launch {
            val rep = when (r.transport) {
                UsbConnectLab.Transport.USB ->
                    UsbConnectLab.runUsb(context) { r.steps.add(it); tick++ }
                UsbConnectLab.Transport.BLUETOOTH ->
                    UsbConnectLab.runBluetooth(context) { r.steps.add(it); tick++ }
                UsbConnectLab.Transport.OEM_VCI ->
                    UsbConnectLab.runOemVci(context) { r.steps.add(it); tick++ }
            }
            r.report = rep; r.running = false
            anyRunning = runs.any { it.running }
            tick++
        }
    }

    fun runAll() {
        runs.forEach { it.steps.clear(); it.report = null; it.running = true }
        anyRunning = true; tick++
        scope.launch {
            for (i in runs.indices) {
                val r = runs[i]
                val rep = when (r.transport) {
                    UsbConnectLab.Transport.USB ->
                        UsbConnectLab.runUsb(context) { r.steps.add(it); tick++ }
                    UsbConnectLab.Transport.BLUETOOTH ->
                        UsbConnectLab.runBluetooth(context) { r.steps.add(it); tick++ }
                    UsbConnectLab.Transport.OEM_VCI ->
                        UsbConnectLab.runOemVci(context) { r.steps.add(it); tick++ }
                }
                r.report = rep; r.running = false; tick++
            }
            anyRunning = false; tick++
        }
    }

    fun shareAll() {
        val sb = StringBuilder()
        runs.forEach { r ->
            sb.appendLine("######## ${r.transport.label} ########")
            sb.appendLine(r.report?.rawLog ?: "(not run)")
            r.report?.let { rep ->
                sb.appendLine("VERDICT: ${rep.verdict}")
                rep.nextActions.forEach { sb.appendLine(" - $it") }
            }
            sb.appendLine()
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "TCW Connect Lab report")
            putExtra(Intent.EXTRA_TEXT, sb.toString())
        }
        context.startActivity(Intent.createChooser(intent, "Share Connect Lab log"))
    }

    @Suppress("UNUSED_EXPRESSION") tick // recompose trigger

    Column(Modifier.fillMaxSize().background(Color(0xFFF4F4F2))) {
        TopAppBar(
            title = { Text("USB / Connect Lab") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            },
            actions = {
                IconButton(onClick = { shareAll() }) {
                    Icon(Icons.Default.Share, contentDescription = "Share log")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = TcwTokens.Ink,
                titleContentColor = TcwTokens.OnInk,
                navigationIconContentColor = TcwTokens.Amber,
                actionIconContentColor = TcwTokens.Amber,
            ),
        )

        Column(
            Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                "Tests all three ways the scanner can reach your car and shows exactly where each one " +
                    "succeeds or fails. Plug in / pair your adapter with the ignition ON, then run a test.",
                style = MaterialTheme.typography.bodyMedium,
                color = TcwTokens.Muted,
            )

            Button(
                onClick = { runAll() },
                enabled = !anyRunning,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(TcwTokens.RadiusMedium),
                colors = ButtonDefaults.buttonColors(containerColor = TcwTokens.Amber, contentColor = TcwTokens.OnAmber),
            ) {
                if (anyRunning) {
                    CircularProgressIndicator(Modifier.size(18.dp), color = TcwTokens.OnAmber, strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                }
                Text(if (anyRunning) "Testing…" else "Test all 3 connections", fontWeight = FontWeight.Bold)
            }

            runs.forEachIndexed { idx, run ->
                TransportCard(run = run, onRun = { runOne(idx) }, enabled = !anyRunning)
            }
        }
    }
}

@Composable
private fun TransportCard(run: LiveRun, onRun: () -> Unit, enabled: Boolean) {
    Surface(
        color = Color.White,
        shape = RoundedCornerShape(TcwTokens.RadiusMedium),
        shadowElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    run.transport.label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TcwTokens.Ink,
                    modifier = Modifier.weight(1f),
                )
                if (run.running) {
                    CircularProgressIndicator(Modifier.size(18.dp), color = TcwTokens.Amber, strokeWidth = 2.dp)
                } else {
                    OutlinedButton(
                        onClick = onRun,
                        enabled = enabled,
                        shape = RoundedCornerShape(TcwTokens.RadiusSmall),
                    ) { Text("Test") }
                }
            }

            run.steps.forEach { step -> StepRow(step) }

            run.report?.let { rep ->
                Spacer(Modifier.height(2.dp))
                VerdictBlock(rep)
            }
        }
    }
}

@Composable
private fun StepRow(step: UsbConnectLab.Step) {
    val (color, glyph) = when (step.result) {
        UsbConnectLab.Result.PASS -> TcwTokens.Green to "✓"
        UsbConnectLab.Result.FAIL -> TcwTokens.Red to "✕"
        UsbConnectLab.Result.WARN -> TcwTokens.Amber to "!"
        UsbConnectLab.Result.INFO -> TcwTokens.Blue to "i"
        UsbConnectLab.Result.SKIP -> TcwTokens.Muted to "–"
    }
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            Modifier.size(22.dp).background(color, RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center,
        ) { Text(glyph, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
        Column(Modifier.weight(1f)) {
            Text(step.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = TcwTokens.Ink)
            Text(step.detail, style = MaterialTheme.typography.bodySmall, color = TcwTokens.Muted)
        }
    }
}

@Composable
private fun VerdictBlock(rep: UsbConnectLab.Report) {
    val ok = rep.handshake != null
    Surface(
        color = if (ok) TcwTokens.GreenSubtle else TcwTokens.AmberSubtle,
        shape = RoundedCornerShape(TcwTokens.RadiusSmall),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                rep.verdict,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = if (ok) TcwTokens.Green else TcwTokens.Ink,
            )
            rep.nextActions.forEach { a ->
                Text("• $a", style = MaterialTheme.typography.bodySmall, color = TcwTokens.Ink)
            }
            rep.handshake?.let { hs ->
                Text(
                    hs.take(500),
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = TcwTokens.Muted,
                )
            }
        }
    }
}
