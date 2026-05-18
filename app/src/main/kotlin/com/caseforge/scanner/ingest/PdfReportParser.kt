package com.caseforge.scanner.ingest

import android.content.Context
import android.net.Uri
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.InputStream

/** Extracts text from an X431 PDF report. Uses PdfBox-Android (works offline). */
object PdfReportParser {

    @Volatile private var initialized = false

    private fun ensureInit(context: Context) {
        if (!initialized) {
            PDFBoxResourceLoader.init(context.applicationContext)
            initialized = true
        }
    }

    fun extractText(context: Context, uri: Uri): String {
        ensureInit(context)
        val input: InputStream = context.contentResolver.openInputStream(uri)
            ?: return ""
        input.use { stream ->
            PDDocument.load(stream).use { doc ->
                val stripper = PDFTextStripper()
                return stripper.getText(doc).trim()
            }
        }
    }
}
