package com.caseforge.scanner.report

import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * PDF report generator using only Android's built-in android.graphics.pdf.PdfDocument.
 * Letter size: 612 x 792 pt.
 */
class PdfReportBuilder {

    data class DtcRow(
        val code: String,
        val module: String?,
        val description: String?,
        val status: String?
    )

    data class InvoiceLine(
        val description: String,
        val amount: Double
    )

    // ---- Layout constants -------------------------------------------------
    private val pageWidth = 612
    private val pageHeight = 792
    private val marginLeft = 48f
    private val marginRight = 48f
    private val marginTop = 56f
    private val marginBottom = 56f
    private val contentWidth = pageWidth - marginLeft - marginRight

    // ---- Paints -----------------------------------------------------------
    private val titlePaint = Paint().apply {
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textSize = 18f
        color = Color.BLACK
        isAntiAlias = true
    }
    private val headingPaint = Paint().apply {
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textSize = 14f
        color = Color.BLACK
        isAntiAlias = true
    }
    private val bodyPaint = Paint().apply {
        typeface = Typeface.DEFAULT
        textSize = 11f
        color = Color.BLACK
        isAntiAlias = true
    }
    private val bodyBold = Paint().apply {
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textSize = 11f
        color = Color.BLACK
        isAntiAlias = true
    }
    private val mutedPaint = Paint().apply {
        typeface = Typeface.DEFAULT
        textSize = 11f
        color = Color.DKGRAY
        isAntiAlias = true
    }
    private val rulePaint = Paint().apply {
        color = Color.LTGRAY
        strokeWidth = 0.6f
        isAntiAlias = true
    }
    private val accentPaint = Paint().apply {
        color = Color.DKGRAY
        strokeWidth = 1.2f
        isAntiAlias = true
    }

    // ---- Public API -------------------------------------------------------
    fun buildDiagnosticReport(
        out: File,
        vin: String?,
        vehicleSummary: String?,
        symptom: String?,
        rootCause: String?,
        recommendedRepair: String?,
        dtcs: List<DtcRow>,
        evidencePhotoPaths: List<String> = emptyList(),
        technicianName: String = "Ricky",
        shopName: String = "Together Car Works"
    ): File {
        val doc = PdfDocument()
        val ctx = PageCtx(doc, startPage(doc, 1))
        drawHeader(ctx)
        drawTitle(ctx, "Diagnostic Report")
        drawKeyValueBlock(
            ctx,
            listOf(
                "Shop" to shopName,
                "Technician" to technicianName,
                "VIN" to (vin ?: "-"),
                "Vehicle" to (vehicleSummary ?: "-")
            )
        )
        drawSectionHeading(ctx, "Symptom")
        drawParagraph(ctx, symptom ?: "Not provided.")
        drawSectionHeading(ctx, "Root Cause")
        drawParagraph(ctx, rootCause ?: "Not determined.")
        drawSectionHeading(ctx, "Recommended Repair")
        drawParagraph(ctx, recommendedRepair ?: "Not provided.")
        drawSectionHeading(ctx, "Diagnostic Trouble Codes")
        drawDtcTable(ctx, dtcs)
        if (evidencePhotoPaths.isNotEmpty()) {
            drawSectionHeading(ctx, "Repair Evidence Photos")
            evidencePhotoPaths.forEachIndexed { index, path ->
                drawParagraph(ctx, "Photo ${index + 1}: $path")
            }
        }
        drawSignatureBlock(ctx, technicianName)
        finalize(ctx, out)
        return out
    }

    fun buildPreScanReport(
        out: File,
        vin: String?,
        vehicleSummary: String?,
        dtcs: List<DtcRow>
    ): File {
        val doc = PdfDocument()
        val ctx = PageCtx(doc, startPage(doc, 1))
        drawHeader(ctx)
        drawTitle(ctx, "Pre-Scan Report")
        drawKeyValueBlock(
            ctx,
            listOf(
                "VIN" to (vin ?: "-"),
                "Vehicle" to (vehicleSummary ?: "-"),
                "Scan Type" to "Pre-Repair"
            )
        )
        drawSectionHeading(ctx, "Codes Found (Before Repair)")
        if (dtcs.isEmpty()) drawParagraph(ctx, "No diagnostic trouble codes reported.")
        else drawDtcTable(ctx, dtcs)
        drawSignatureBlock(ctx, null)
        finalize(ctx, out)
        return out
    }

    fun buildPostScanReport(
        out: File,
        vin: String?,
        vehicleSummary: String?,
        before: List<DtcRow>,
        after: List<DtcRow>
    ): File {
        val doc = PdfDocument()
        val ctx = PageCtx(doc, startPage(doc, 1))
        drawHeader(ctx)
        drawTitle(ctx, "Post-Scan Report")
        drawKeyValueBlock(
            ctx,
            listOf(
                "VIN" to (vin ?: "-"),
                "Vehicle" to (vehicleSummary ?: "-"),
                "Scan Type" to "Post-Repair"
            )
        )
        drawSectionHeading(ctx, "Before Repair")
        if (before.isEmpty()) drawParagraph(ctx, "No codes recorded before repair.")
        else drawDtcTable(ctx, before)
        drawSectionHeading(ctx, "After Repair")
        if (after.isEmpty()) drawParagraph(ctx, "No remaining codes. All systems cleared.")
        else drawDtcTable(ctx, after)

        val beforeCodes = before.map { it.code }.toSet()
        val afterCodes = after.map { it.code }.toSet()
        val cleared = beforeCodes - afterCodes
        val remaining = afterCodes.intersect(beforeCodes)
        val newCodes = afterCodes - beforeCodes
        drawSectionHeading(ctx, "Resolution Summary")
        drawParagraph(
            ctx,
            "Cleared: ${cleared.size}    Remaining: ${remaining.size}    New: ${newCodes.size}"
        )
        drawSignatureBlock(ctx, null)
        finalize(ctx, out)
        return out
    }

    fun buildInvoice(
        out: File,
        customerName: String,
        vehicleSummary: String?,
        lineItems: List<InvoiceLine>,
        laborHours: Double,
        laborRate: Double,
        partsTotal: Double,
        tax: Double = 0.0
    ): File {
        val doc = PdfDocument()
        val ctx = PageCtx(doc, startPage(doc, 1))
        drawHeader(ctx)
        drawTitle(ctx, "Invoice")
        drawKeyValueBlock(
            ctx,
            listOf(
                "Bill To" to customerName,
                "Vehicle" to (vehicleSummary ?: "-"),
                "Invoice Date" to currentDate()
            )
        )

        drawSectionHeading(ctx, "Line Items")
        drawInvoiceTable(ctx, lineItems)

        val labor = laborHours * laborRate
        val subtotal = lineItems.sumOf { it.amount } + labor + partsTotal
        val total = subtotal + tax

        drawSectionHeading(ctx, "Totals")
        drawTotalsRow(ctx, "Labor (${formatHours(laborHours)} hr @ ${money(laborRate)})", money(labor))
        drawTotalsRow(ctx, "Parts", money(partsTotal))
        drawTotalsRow(ctx, "Subtotal", money(subtotal))
        drawTotalsRow(ctx, "Tax", money(tax))
        ensureSpace(ctx, 18f)
        ctx.canvas.drawLine(
            pageWidth - marginRight - 220f, ctx.y - 4f,
            pageWidth - marginRight, ctx.y - 4f, accentPaint
        )
        drawTotalsRow(ctx, "TOTAL DUE", money(total), bold = true)
        drawSignatureBlock(ctx, null)
        finalize(ctx, out)
        return out
    }

    // ---- Internal page management ----------------------------------------
    private inner class PageCtx(val doc: PdfDocument, var page: PdfDocument.Page) {
        val canvas get() = page.canvas
        var y: Float = marginTop
        var pageNumber: Int = 1
    }

    private fun startPage(doc: PdfDocument, number: Int): PdfDocument.Page {
        val info = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, number).create()
        return doc.startPage(info)
    }

    private fun newPage(ctx: PageCtx) {
        drawFooter(ctx)
        ctx.doc.finishPage(ctx.page)
        ctx.pageNumber += 1
        ctx.page = startPage(ctx.doc, ctx.pageNumber)
        ctx.y = marginTop
        drawHeader(ctx)
    }

    private fun ensureSpace(ctx: PageCtx, needed: Float) {
        if (ctx.y + needed > pageHeight - marginBottom) {
            newPage(ctx)
        }
    }

    private fun finalize(ctx: PageCtx, out: File) {
        drawFooter(ctx)
        ctx.doc.finishPage(ctx.page)
        FileOutputStream(out).use { ctx.doc.writeTo(it) }
        ctx.doc.close()
    }

    // ---- Drawing primitives ----------------------------------------------
    private fun drawHeader(ctx: PageCtx) {
        val brandPaint = Paint(titlePaint).apply { textSize = 16f }
        ctx.canvas.drawText("Together Car Works", marginLeft, marginTop - 18f, brandPaint)
        val date = currentDate()
        val dateWidth = mutedPaint.measureText(date)
        ctx.canvas.drawText(date, pageWidth - marginRight - dateWidth, marginTop - 18f, mutedPaint)
        ctx.canvas.drawLine(marginLeft, marginTop - 10f, pageWidth - marginRight, marginTop - 10f, rulePaint)
        ctx.y = marginTop + 6f
    }

    private fun drawFooter(ctx: PageCtx) {
        val footY = pageHeight - marginBottom + 24f
        ctx.canvas.drawLine(marginLeft, footY - 16f, pageWidth - marginRight, footY - 16f, rulePaint)
        ctx.canvas.drawText(
            "Together Car Works - Generated ${currentDate()}",
            marginLeft, footY, mutedPaint
        )
        val pageLabel = "Page ${ctx.pageNumber}"
        val pw = mutedPaint.measureText(pageLabel)
        ctx.canvas.drawText(pageLabel, pageWidth - marginRight - pw, footY, mutedPaint)
    }

    private fun drawTitle(ctx: PageCtx, title: String) {
        ensureSpace(ctx, 32f)
        ctx.y += 12f
        ctx.canvas.drawText(title, marginLeft, ctx.y, titlePaint)
        ctx.y += 8f
        ctx.canvas.drawLine(marginLeft, ctx.y, marginLeft + 60f, ctx.y, accentPaint)
        ctx.y += 14f
    }

    private fun drawSectionHeading(ctx: PageCtx, text: String) {
        ensureSpace(ctx, 28f)
        ctx.y += 8f
        ctx.canvas.drawText(text, marginLeft, ctx.y, headingPaint)
        ctx.y += 6f
        ctx.canvas.drawLine(marginLeft, ctx.y, pageWidth - marginRight, ctx.y, rulePaint)
        ctx.y += 14f
    }

    private fun drawKeyValueBlock(ctx: PageCtx, pairs: List<Pair<String, String>>) {
        val labelWidth = 90f
        for ((k, v) in pairs) {
            ensureSpace(ctx, 16f)
            ctx.canvas.drawText("$k:", marginLeft, ctx.y, bodyBold)
            val wrapped = wrapText(v, bodyPaint, contentWidth - labelWidth)
            for ((i, line) in wrapped.withIndex()) {
                if (i > 0) {
                    ctx.y += 14f
                    ensureSpace(ctx, 14f)
                }
                ctx.canvas.drawText(line, marginLeft + labelWidth, ctx.y, bodyPaint)
            }
            ctx.y += 16f
        }
    }

    private fun drawParagraph(ctx: PageCtx, text: String) {
        val lines = wrapText(text, bodyPaint, contentWidth)
        for (line in lines) {
            ensureSpace(ctx, 14f)
            ctx.canvas.drawText(line, marginLeft, ctx.y, bodyPaint)
            ctx.y += 14f
        }
        ctx.y += 4f
    }

    private fun drawDtcTable(ctx: PageCtx, dtcs: List<DtcRow>) {
        if (dtcs.isEmpty()) {
            drawParagraph(ctx, "No codes.")
            return
        }
        val colCode = marginLeft
        val colModule = marginLeft + 90f
        val colStatus = marginLeft + 200f
        val colDesc = marginLeft + 280f
        val descWidth = pageWidth - marginRight - colDesc

        ensureSpace(ctx, 20f)
        ctx.canvas.drawText("Code", colCode, ctx.y, bodyBold)
        ctx.canvas.drawText("Module", colModule, ctx.y, bodyBold)
        ctx.canvas.drawText("Status", colStatus, ctx.y, bodyBold)
        ctx.canvas.drawText("Description", colDesc, ctx.y, bodyBold)
        ctx.y += 6f
        ctx.canvas.drawLine(marginLeft, ctx.y, pageWidth - marginRight, ctx.y, rulePaint)
        ctx.y += 12f

        for (row in dtcs) {
            val descLines = wrapText(row.description ?: "-", bodyPaint, descWidth)
            val rowHeight = (descLines.size.coerceAtLeast(1) * 14f) + 4f
            ensureSpace(ctx, rowHeight)
            ctx.canvas.drawText(row.code, colCode, ctx.y, bodyPaint)
            ctx.canvas.drawText(row.module ?: "-", colModule, ctx.y, bodyPaint)
            ctx.canvas.drawText(row.status ?: "-", colStatus, ctx.y, bodyPaint)
            for ((i, line) in descLines.withIndex()) {
                ctx.canvas.drawText(line, colDesc, ctx.y + (i * 14f), bodyPaint)
            }
            ctx.y += rowHeight
        }
        ctx.y += 4f
    }

    private fun drawInvoiceTable(ctx: PageCtx, items: List<InvoiceLine>) {
        val colDesc = marginLeft
        val colAmount = pageWidth - marginRight - 80f
        val descWidth = colAmount - colDesc - 12f

        ensureSpace(ctx, 20f)
        ctx.canvas.drawText("Description", colDesc, ctx.y, bodyBold)
        val amtHead = "Amount"
        val ahw = bodyBold.measureText(amtHead)
        ctx.canvas.drawText(amtHead, pageWidth - marginRight - ahw, ctx.y, bodyBold)
        ctx.y += 6f
        ctx.canvas.drawLine(marginLeft, ctx.y, pageWidth - marginRight, ctx.y, rulePaint)
        ctx.y += 12f

        if (items.isEmpty()) {
            drawParagraph(ctx, "No line items.")
            return
        }
        for (item in items) {
            val lines = wrapText(item.description, bodyPaint, descWidth)
            val rowHeight = (lines.size.coerceAtLeast(1) * 14f) + 4f
            ensureSpace(ctx, rowHeight)
            for ((i, line) in lines.withIndex()) {
                ctx.canvas.drawText(line, colDesc, ctx.y + (i * 14f), bodyPaint)
            }
            val amt = money(item.amount)
            val aw = bodyPaint.measureText(amt)
            ctx.canvas.drawText(amt, pageWidth - marginRight - aw, ctx.y, bodyPaint)
            ctx.y += rowHeight
        }
    }

    private fun drawTotalsRow(ctx: PageCtx, label: String, value: String, bold: Boolean = false) {
        ensureSpace(ctx, 16f)
        val labelPaint = if (bold) bodyBold else bodyPaint
        val valuePaint = if (bold) bodyBold else bodyPaint
        val labelX = pageWidth - marginRight - 220f
        val vw = valuePaint.measureText(value)
        ctx.canvas.drawText(label, labelX, ctx.y, labelPaint)
        ctx.canvas.drawText(value, pageWidth - marginRight - vw, ctx.y, valuePaint)
        ctx.y += 16f
    }

    private fun drawSignatureBlock(ctx: PageCtx, technicianName: String?) {
        ensureSpace(ctx, 60f)
        ctx.y += 24f
        val lineY = ctx.y
        val sigWidth = 240f
        ctx.canvas.drawLine(marginLeft, lineY, marginLeft + sigWidth, lineY, accentPaint)
        ctx.canvas.drawLine(
            pageWidth - marginRight - sigWidth, lineY,
            pageWidth - marginRight, lineY, accentPaint
        )
        ctx.y += 14f
        ctx.canvas.drawText(
            "Technician${if (technicianName != null) " ($technicianName)" else ""}",
            marginLeft, ctx.y, mutedPaint
        )
        val custLabel = "Customer Signature"
        val cw = mutedPaint.measureText(custLabel)
        ctx.canvas.drawText(custLabel, pageWidth - marginRight - cw, ctx.y, mutedPaint)
        ctx.y += 16f
    }

    // ---- Helpers ----------------------------------------------------------
    private fun currentDate(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    private fun money(v: Double): String =
        "$" + String.format(Locale.US, "%,.2f", v)

    private fun formatHours(h: Double): String =
        if (h == h.toInt().toDouble()) h.toInt().toString() else String.format(Locale.US, "%.2f", h)

    private fun wrapText(text: String, paint: Paint, maxWidth: Float): List<String> {
        if (text.isEmpty()) return listOf("")
        val out = mutableListOf<String>()
        for (rawLine in text.split('\n')) {
            if (rawLine.isEmpty()) { out.add(""); continue }
            val words = rawLine.split(' ')
            var current = StringBuilder()
            for (word in words) {
                val candidate = if (current.isEmpty()) word else "$current $word"
                if (paint.measureText(candidate) <= maxWidth) {
                    current = StringBuilder(candidate)
                } else {
                    if (current.isNotEmpty()) {
                        out.add(current.toString())
                        current = StringBuilder()
                    }
                    if (paint.measureText(word) > maxWidth) {
                        var buf = StringBuilder()
                        for (ch in word) {
                            if (paint.measureText("$buf$ch") > maxWidth && buf.isNotEmpty()) {
                                out.add(buf.toString())
                                buf = StringBuilder()
                            }
                            buf.append(ch)
                        }
                        if (buf.isNotEmpty()) current = buf
                    } else {
                        current = StringBuilder(word)
                    }
                }
            }
            if (current.isNotEmpty()) out.add(current.toString())
        }
        return if (out.isEmpty()) listOf("") else out
    }
}
