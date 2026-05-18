package com.caseforge.scanner.report

import android.content.Context
import android.net.Uri
import com.caseforge.scanner.db.AppDatabase
import com.caseforge.scanner.evidence.Evidence
import com.caseforge.scanner.evidence.EvidenceSnapshot
import com.caseforge.scanner.evidence.EvidenceType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Builds a three-page customer-facing PDF repair story from Evidence rows.
 *
 * Page 1 — What we found (BEFORE): vehicle info + fault codes at intake.
 * Page 2 — What we fixed (FIX): work performed + engine-bay photos.
 * Page 3 — All clear (AFTER): codes cleared, live data back in range.
 *
 * Depends on [PdfReportBuilder] (existing, assumed at report/PdfReportBuilder.kt)
 * which exposes: addHeading, addParagraph, addTable, addImage, newPage, build → File.
 *
 * Usage:
 *   val generator = RepairStoryGenerator(applicationContext, AppDatabase.getInstance(ctx))
 *   val pdfFile = generator.generate(ticketId = "TKT-2024-0042")
 *   // share or print pdfFile
 */
class RepairStoryGenerator(
    private val ctx: Context,
    private val db: AppDatabase,
) {

    private val json = Json { ignoreUnknownKeys = true }
    private val dateFormat = SimpleDateFormat("MMMM d, yyyy", Locale.US)
    private val timeFormat = SimpleDateFormat("h:mm a", Locale.US)

    // -------------------------------------------------------------------------
    // Main entry point
    // -------------------------------------------------------------------------

    /**
     * Pull all Evidence for [ticketId], group by type, and render a PDF.
     *
     * @return The generated PDF File in the app's private files directory.
     * @throws IllegalStateException if no BEFORE evidence exists (minimum required).
     */
    suspend fun generate(ticketId: String): File = withContext(Dispatchers.IO) {
        val allEvidence = db.evidenceDao().getByTicket(ticketId)
        check(allEvidence.isNotEmpty()) { "No evidence found for ticket $ticketId" }

        val before = allEvidence.filter { it.type == EvidenceType.BEFORE }
        val fix    = allEvidence.filter { it.type == EvidenceType.FIX }
        val after  = allEvidence.filter { it.type == EvidenceType.AFTER }

        check(before.isNotEmpty()) { "Ticket $ticketId has no BEFORE evidence — cannot generate report" }

        val builder = PdfReportBuilder(ctx)

        // ------------------------------------------------------------------
        // Page 1: What We Found
        // ------------------------------------------------------------------
        renderBeforePage(builder, ticketId, before)

        // ------------------------------------------------------------------
        // Page 2: What We Fixed
        // ------------------------------------------------------------------
        if (fix.isNotEmpty()) {
            builder.newPage()
            renderFixPage(builder, fix)
        }

        // ------------------------------------------------------------------
        // Page 3: All Clear
        // ------------------------------------------------------------------
        if (after.isNotEmpty()) {
            builder.newPage()
            renderAfterPage(builder, after)
        }

        // ------------------------------------------------------------------
        // Output
        // ------------------------------------------------------------------
        val outDir = File(ctx.filesDir, "repair_stories").also { it.mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val outFile = File(outDir, "RepairStory_${ticketId}_$stamp.pdf")
        builder.build(outFile)
        outFile
    }

    // -------------------------------------------------------------------------
    // Page renderers
    // -------------------------------------------------------------------------

    private fun renderBeforePage(
        builder: PdfReportBuilder,
        ticketId: String,
        evidence: List<Evidence>,
    ) {
        val first = evidence.first()
        val snap = first.snapshotJson?.let { parseSnapshot(it) }

        builder.addHeading("Vehicle Inspection Report")
        builder.addParagraph("Ticket: $ticketId")
        builder.addParagraph("Date: ${dateFormat.format(Date(first.timestamp))}")
        builder.addParagraph("Time: ${timeFormat.format(Date(first.timestamp))}")

        if (snap?.vehicleSummary != null) {
            builder.addParagraph(" ")
            builder.addHeading("Your Vehicle", level = 2)
            builder.addParagraph(snap.vehicleSummary)
            if (snap.vehicleVin != null) {
                builder.addParagraph("VIN: ${snap.vehicleVin}")
            }
        }

        // Collect all DTCs across BEFORE evidence rows (de-duplicated by code)
        val dtcs = evidence
            .flatMap { parseSnapshot(it.snapshotJson ?: "")?.dtcs ?: emptyList() }
            .distinctBy { it.code }

        if (dtcs.isNotEmpty()) {
            builder.addParagraph(" ")
            builder.addHeading("Warning Codes We Found", level = 2)
            builder.addParagraph(
                "Our scanner detected ${dtcs.size} issue${if (dtcs.size != 1) "s" else ""} " +
                "stored in your vehicle's computer. Each one is explained below in plain language."
            )
            val rows = mutableListOf(listOf("Code", "What It Means", "Status"))
            dtcs.forEach { dtc ->
                rows.add(listOf(
                    dtc.code,
                    dtc.description?.take(80) ?: "Diagnostic trouble code",
                    friendlyStatus(dtc.status),
                ))
            }
            builder.addTable(rows)
        } else {
            builder.addParagraph("No active fault codes were recorded at intake.")
        }

        // Attach any BEFORE photos
        evidence.mapNotNull { it.photoUri }.forEach { uriStr ->
            builder.addImage(Uri.parse(uriStr), caption = "Vehicle at intake")
        }
    }

    private fun renderFixPage(
        builder: PdfReportBuilder,
        evidence: List<Evidence>,
    ) {
        builder.addHeading("What We Fixed")
        builder.addParagraph(
            "Below is a summary of the repair work performed, including photos " +
            "taken during the service."
        )

        evidence.forEach { ev ->
            builder.addParagraph(" ")
            builder.addHeading(ev.label, level = 2)
            builder.addParagraph(
                "Captured: ${dateFormat.format(Date(ev.timestamp))} " +
                "at ${timeFormat.format(Date(ev.timestamp))}"
            )

            val snap = ev.snapshotJson?.let { parseSnapshot(it) }
            if (snap != null && snap.dtcs.isNotEmpty()) {
                val rows = mutableListOf(listOf("Code", "Status"))
                snap.dtcs.forEach { dtc -> rows.add(listOf(dtc.code, friendlyStatus(dtc.status))) }
                builder.addTable(rows)
            }

            ev.photoUri?.let { uriStr ->
                builder.addImage(Uri.parse(uriStr), caption = ev.label)
            }
        }
    }

    private fun renderAfterPage(
        builder: PdfReportBuilder,
        evidence: List<Evidence>,
    ) {
        builder.addHeading("All Clear — Post-Repair Check")
        builder.addParagraph(
            "After completing the repair, we re-scanned your vehicle to confirm " +
            "all issues were resolved."
        )

        val last = evidence.last()
        val snap = last.snapshotJson?.let { parseSnapshot(it) }

        if (snap != null) {
            if (snap.dtcs.isEmpty()) {
                builder.addParagraph(
                    "Great news — no fault codes remain. Your vehicle's computer " +
                    "is reporting everything within normal range."
                )
            } else {
                builder.addParagraph(
                    "The following codes were still present after the repair. " +
                    "Please discuss these with your technician."
                )
                val rows = mutableListOf(listOf("Code", "Description", "Status"))
                snap.dtcs.forEach { dtc ->
                    rows.add(listOf(dtc.code, dtc.description ?: "—", friendlyStatus(dtc.status)))
                }
                builder.addTable(rows)
            }

            if (snap.livePids.isNotEmpty()) {
                builder.addParagraph(" ")
                builder.addHeading("Live Sensor Readings", level = 2)
                builder.addParagraph("Recorded at: ${timeFormat.format(Date(snap.capturedAtMs))}")
                val rows = mutableListOf(listOf("Sensor", "Reading"))
                snap.livePids.entries.take(20).forEach { (pid, value) ->
                    rows.add(listOf(friendlyPidName(pid), formatPidValue(pid, value)))
                }
                builder.addTable(rows)
            }
        }

        evidence.mapNotNull { it.photoUri }.forEach { uriStr ->
            builder.addImage(Uri.parse(uriStr), caption = "Post-repair verification")
        }

        builder.addParagraph(" ")
        builder.addParagraph(
            "Thank you for trusting us with your vehicle. " +
            "Keep this report for your service records."
        )
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private fun parseSnapshot(jsonStr: String): EvidenceSnapshot? =
        runCatching { json.decodeFromString<EvidenceSnapshot>(jsonStr) }.getOrNull()

    private fun friendlyStatus(status: String?): String = when (status?.lowercase()) {
        "current"  -> "Active now"
        "history"  -> "Previously seen"
        "pending"  -> "Intermittent"
        else       -> status ?: "Unknown"
    }

    /** Map raw OBD PID keys to friendly display names. Extend as needed. */
    private fun friendlyPidName(pid: String): String = when (pid.uppercase()) {
        "RPM"          -> "Engine Speed (RPM)"
        "COOLANT_TEMP" -> "Coolant Temperature"
        "MAF"          -> "Air Flow Rate"
        "THROTTLE"     -> "Throttle Position"
        "FUEL_LEVEL"   -> "Fuel Level"
        "BARO"         -> "Barometric Pressure"
        "O2_B1S1"      -> "Oxygen Sensor (Bank 1)"
        "SPEED"        -> "Vehicle Speed"
        "INTAKE_TEMP"  -> "Intake Air Temperature"
        "LOAD"         -> "Engine Load"
        else           -> pid.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }
    }

    private fun formatPidValue(pid: String, value: Double): String = when (pid.uppercase()) {
        "RPM"          -> "${value.toInt()} rpm"
        "COOLANT_TEMP", "INTAKE_TEMP" -> "${value.toInt()} °C"
        "THROTTLE", "LOAD", "FUEL_LEVEL" -> "${"%.1f".format(value)} %"
        "SPEED"        -> "${value.toInt()} km/h"
        "MAF"          -> "${"%.2f".format(value)} g/s"
        "BARO"         -> "${"%.1f".format(value)} kPa"
        else           -> "%.2f".format(value)
    }
}
