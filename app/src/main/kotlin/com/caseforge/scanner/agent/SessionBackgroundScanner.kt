package com.caseforge.scanner.agent

import android.content.Context
import com.caseforge.scanner.agent.discovery.DiscoveryReport
import com.caseforge.scanner.agent.discovery.TabletHardwareDiscoveryAgent
import com.caseforge.scanner.agent.discovery.VehicleProfileLoader
import com.caseforge.scanner.agent.session.LaunchAccessibilityGoldenPath
import com.caseforge.scanner.data.SettingsRepo
import com.caseforge.scanner.engine.Dtc
import com.caseforge.scanner.transfer.SessionEventLogger
import com.caseforge.scanner.vci.DiagnosticConnector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Silent background hardware discovery + Tier 0 OBD DTC read when a cable is detected.
 * No user prompts — status is surfaced only via a subtle status line in [SessionChatScreen].
 */
class SessionBackgroundScanner(
    private val context: Context,
    private val settings: SettingsRepo,
) {
    data class Snapshot(
        val discoveryReport: DiscoveryReport?,
        val dtcSummary: String?,
        val linkStatus: String,
    )

    suspend fun run(sessionId: String, vinHint: String?): Snapshot = withContext(Dispatchers.IO) {
        SessionEventLogger.log(context, sessionId, "background_scan_start", vinHint.orEmpty())

        val profileId = vinHint?.let { VehicleProfileLoader.profileIdForVin(context, it) }
            ?: VehicleProfileLoader.DEFAULT_WINDSTAR_ID

        val discovery = runCatching {
            TabletHardwareDiscoveryAgent(context).scan(profileId)
        }.getOrNull()

        discovery?.let {
            SessionEventLogger.log(
                context, sessionId, "discovery_complete",
                detail = it.recommendedAction,
                extra = mapOf("deviceCount" to it.devices.size.toString()),
            )
        }

        val obdRead = readTier0DtcsIfCablePresent(sessionId, vinHint)
        val dtcSummary = obdRead.summary
        val linkStatus = when {
            dtcSummary != null -> "Reading codes… done"
            obdRead.connectError != null -> "OBD: ${obdRead.connectError}"
            discovery?.devices?.any { it.permissionGranted == true || it.obdLikely } == true ->
                "Adapter detected"
            else -> "No OBD cable detected"
        }

        if (dtcSummary != null) {
            SessionEventLogger.log(context, sessionId, "dtc_snapshot", detail = dtcSummary.take(500))
        }

        Snapshot(
            discoveryReport = discovery,
            dtcSummary = dtcSummary,
            linkStatus = linkStatus,
        )
    }

    private data class ObdReadOutcome(
        val summary: String?,
        val connectError: String?,
    )

    private suspend fun readTier0DtcsIfCablePresent(sessionId: String, vinHint: String?): ObdReadOutcome {
        val launchInstalled = X431InstalledProbe.installedFlags(context.packageManager).values.any { it }
        if (settings.launchPlanABridgeEnabled && launchInstalled) {
            val planA = LaunchAccessibilityGoldenPath.readLatestDtcSnapshotOrNull(context)
            if (planA != null) {
                SessionEventLogger.log(
                    context,
                    sessionId,
                    "plan_a_dtc_snapshot",
                    detail = planA.dtcSummary.take(500),
                )
                return ObdReadOutcome("Plan A (Launch accessibility): ${planA.dtcSummary}", null)
            }
        }
        if (!settings.isPlanBTierEffective(0) && !settings.nativeObdExperimental) {
            return ObdReadOutcome(null, null)
        }
        val connectResult = DiagnosticConnector.connect(context, settings)
        val link = connectResult.getOrNull()
        if (link == null) {
            val err = connectResult.exceptionOrNull()?.message?.take(220) ?: "Connect failed"
            SessionEventLogger.log(context, sessionId, "obd_connect_failed", detail = err)
            return ObdReadOutcome(null, err)
        }

        return try {
            SessionEventLogger.log(
                context,
                sessionId,
                "obd_reading",
                detail = "Tier 0 DTC read via ${link.kind.name}",
            )
            val obdVin = link.readVin()?.takeIf { it.isNotBlank() } ?: vinHint
            val dtcs = link.port.readDtcs(null).getOrNull() ?: return ObdReadOutcome(null, "DTC read returned no data")
            ObdReadOutcome(
                buildString {
                    obdVin?.let { appendLine("OBD VIN: $it") }
                    append(formatDtcs(dtcs))
                }.trim().ifBlank { null },
                null,
            )
        } catch (t: Throwable) {
            ObdReadOutcome(null, t.message?.take(220) ?: "OBD read error")
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
