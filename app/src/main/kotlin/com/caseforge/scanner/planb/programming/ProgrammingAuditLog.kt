package com.caseforge.scanner.planb.programming

import android.content.Context
import com.caseforge.scanner.planb.PlanbMarque
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Append-only audit trail for Tier 4 flash *requests* (scaffold — no opcode execution).
 */
class ProgrammingAuditLog(context: Context) {

    private val file: File = File(context.filesDir, LOG_FILENAME).also {
        if (!it.exists()) it.createNewFile()
    }
    private val ts = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    fun recordFlashRequest(marque: PlanbMarque, op: FlashOp): String {
        val entryId = UUID.randomUUID().toString().take(ENTRY_ID_LENGTH)
        append(entryId, "flash_request", "marque=${marque.id} op=${op.id} mode=${op.applyMode}")
        return entryId
    }

    fun recordFlashOutcome(entryId: String, result: FlashRequestResult) {
        val status = when (result) {
            is FlashRequestResult.Blocked -> "blocked"
            is FlashRequestResult.PartnerRequired -> "partner_required"
            is FlashRequestResult.QueuedForLab -> "queued_for_lab"
        }
        append(entryId, "flash_outcome", "status=$status op=${result.op.id}")
    }

    fun tail(maxLines: Int = DEFAULT_TAIL_LINES): List<String> {
        val lines = file.readLines()
        return if (lines.size <= maxLines) lines else lines.takeLast(maxLines)
    }

    @Synchronized
    private fun append(entryId: String, kind: String, detail: String) {
        file.appendText("${ts.format(Date())}\t$entryId\t$kind\t${detail.replace('\n', ' ')}\n")
    }

    companion object {
        const val LOG_FILENAME = "programming_audit.log"
        private const val ENTRY_ID_LENGTH = 8
        private const val DEFAULT_TAIL_LINES = 200
    }
}
