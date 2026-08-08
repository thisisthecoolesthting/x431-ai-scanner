package com.caseforge.scanner.transfer

import android.content.Context
import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Persists failed Shop Desk ingest attempts in SharedPreferences for retry on app resume.
 */
object PendingShopDeskUploadQueue {

    private const val PREFS_NAME = "tcw_pending_shopdesk_uploads"
    private const val KEY_ITEMS = "items"

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Serializable
    data class QueuedItem(
        val id: String,
        val url: String,
        val sessionId: String? = null,
        val manifest: HarvestBatchManifest,
        val queuedAtMs: Long,
        val lastError: String? = null,
    )

    fun enqueue(
        context: Context,
        url: String,
        batch: HarvestBatch,
        sessionId: String?,
        error: Throwable?,
    ) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val existing = loadItems(prefs).toMutableList()
        val sid = sessionId?.takeIf { it.isNotBlank() }
        val errMsg = error?.message?.take(500)
        val duplicateIdx = sid?.let { id ->
            existing.indexOfFirst { it.sessionId == id }
        } ?: -1
        val item = QueuedItem(
            id = if (duplicateIdx >= 0) existing[duplicateIdx].id else UUID.randomUUID().toString(),
            url = url.trim(),
            sessionId = sid,
            manifest = batch.manifest,
            queuedAtMs = System.currentTimeMillis(),
            lastError = errMsg,
        )
        if (duplicateIdx >= 0) {
            existing[duplicateIdx] = item
        } else {
            existing.add(item)
        }
        saveItems(prefs, existing)
    }

    fun list(context: Context): List<QueuedItem> =
        loadItems(context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE))

    fun remove(context: Context, id: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val filtered = loadItems(prefs).filterNot { it.id == id }
        saveItems(prefs, filtered)
    }

    fun updateError(context: Context, id: String, message: String?) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val updated = loadItems(prefs).map { item ->
            if (item.id == id) item.copy(lastError = message?.take(500)) else item
        }
        saveItems(prefs, updated)
    }

    fun hasSession(context: Context, sessionId: String): Boolean =
        list(context).any { it.sessionId == sessionId }

    fun count(context: Context): Int = list(context).size

    private fun loadItems(prefs: android.content.SharedPreferences): List<QueuedItem> {
        val raw = prefs.getString(KEY_ITEMS, null) ?: return emptyList()
        return runCatching {
            json.decodeFromString<List<QueuedItem>>(raw)
        }.getOrElse { emptyList() }
    }

    private fun saveItems(prefs: android.content.SharedPreferences, items: List<QueuedItem>) {
        prefs.edit()
            .putString(KEY_ITEMS, json.encodeToString(items))
            .apply()
    }
}
