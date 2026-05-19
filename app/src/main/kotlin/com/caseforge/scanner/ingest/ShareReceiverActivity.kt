package com.caseforge.scanner.ingest

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.caseforge.scanner.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Entry point for "Share to Together Car Works" from the OEM diagnostic app's report view.
 * Reads the PDF, extracts text, then hands the text off to MainActivity for triage.
 */
class ShareReceiverActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uri: Uri? = intent.getParcelableExtra(Intent.EXTRA_STREAM)
        if (uri == null) { finish(); return }
        lifecycleScope.launch {
            val parsed = withContext(Dispatchers.IO) { PdfReportParser.extractText(this@ShareReceiverActivity, uri) }
            val forward = Intent(this@ShareReceiverActivity, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                putExtra(MainActivity.EXTRA_REPORT_TEXT, parsed)
                putExtra(MainActivity.EXTRA_REPORT_SOURCE, uri.toString())
            }
            startActivity(forward)
            finish()
        }
    }
}
