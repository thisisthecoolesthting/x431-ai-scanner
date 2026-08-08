package com.caseforge.scanner.agent.session

import android.content.Context
import com.caseforge.scanner.agent.X431InstalledProbe
import com.caseforge.scanner.data.SettingsRepo
import com.caseforge.scanner.engine.Dtc
import com.caseforge.scanner.vci.DiagnosticConnector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Best-effort live OBD poll for session visual strip. Uses [DiagnosticConnector] (ELM327 / OEM / BT)
 * when Tier 0 / native OBD enabled. Returns stub/disconnected snapshot when no link.
 */
class SessionLiveObdPoller(
    private val context: Context,
    private val settings: SettingsRepo,
) {
    private val rpmHistory = ArrayDeque<Float>(64)

    suspend fun poll(vinHint: String?, priorLinkStatus: String): BackgroundObdSnapshot =
        withContext(Dispatchers.IO) {
            val launchInstalled = X431InstalledProbe.installedFlags(context.packageManager).values.any { it }
            if (settings.launchPlanABridgeEnabled && launchInstalled) {
                val planA = LaunchAccessibilityGoldenPath.readLatestDtcSnapshotOrNull(context)
                if (planA != null) {
                    return@withContext BackgroundObdSnapshot(
                        connected = true,
                        linkStatus = "Plan A linked (Launch accessibility)",
                        protocol = "Launch accessibility golden path",
                        ecuAddress = "Launch/X431",
                        dtcSummary = "Plan A (Launch accessibility): ${planA.dtcSummary}",
                        storedDtcCount = planA.storedCount,
                        pendingDtcCount = planA.pendingCount,
                        monitorsReady = if (planA.storedCount == 0) "MIL off" else "MIL on — ${planA.storedCount} stored",
                    )
                }
            }
            if (!settings.isPlanBTierEffective(0) && !settings.nativeObdExperimental) {
                return@withContext BackgroundObdSnapshot(linkStatus = priorLinkStatus)
            }

            val link = DiagnosticConnector.connect(context, settings).getOrNull()
                ?: return@withContext BackgroundObdSnapshot(linkStatus = priorLinkStatus)

            try {
                val dtcs = link.port.readDtcs(null).getOrNull().orEmpty()
                val storedCount = dtcs.count { !it.description.startsWith("Pending", ignoreCase = true) }
                val pendingCount = dtcs.count { it.description.startsWith("Pending", ignoreCase = true) }
                val dtcSummary = formatDtcs(dtcs).ifBlank { null }

                var rpm: Float? = null
                var coolant: Float? = null
                if (
                    link.kind == DiagnosticConnector.LinkKind.ELM327_USB ||
                    link.kind == DiagnosticConnector.LinkKind.ELM327_BT
                ) {
                    withTimeoutOrNull(2_500L) {
                        link.port.liveData(listOf("0C", "05")).first { sample ->
                            when (sample.pid.uppercase()) {
                                "0C" -> rpm = sample.value.toFloat()
                                "05" -> coolant = sample.value.toFloat()
                                else -> Unit
                            }
                            rpm != null && coolant != null
                        }
                    }
                }

                rpm?.let {
                    rpmHistory.addLast(it)
                    while (rpmHistory.size > 60) rpmHistory.removeFirst()
                }

                BackgroundObdSnapshot(
                    connected = true,
                    linkStatus = "OBD linked (${link.detail})",
                    protocol = link.kind.name.replace('_', ' '),
                    ecuAddress = "7E0",
                    dtcSummary = dtcSummary,
                    storedDtcCount = storedCount,
                    pendingDtcCount = pendingCount,
                    monitorsReady = if (storedCount == 0) "MIL off" else "MIL on — $storedCount stored",
                    rpm = rpm,
                    coolantC = coolant,
                    voltage = null,
                    rpmHistory = rpmHistory.toList(),
                )
            } catch (_: Throwable) {
                BackgroundObdSnapshot(linkStatus = "OBD read error")
            } finally {
                link.disconnect()
            }
        }

    private fun formatDtcs(dtcs: List<Dtc>): String {
        val stored = dtcs.filter { !it.description.startsWith("Pending", ignoreCase = true) }
        val pending = dtcs.filter { it.description.startsWith("Pending", ignoreCase = true) }
        return buildString {
            append("Stored: ")
            append(stored.joinToString(", ") { it.code }.ifBlank { "none" })
            append("; Pending: ")
            append(pending.joinToString(", ") { it.code.removePrefix("PENDING:") }.ifBlank { "none" })
        }
    }
}
