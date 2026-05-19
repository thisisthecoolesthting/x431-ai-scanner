package com.caseforge.scanner.pc

import org.json.JSONArray
import org.json.JSONObject

/** PC receiver health payload from `GET /health`. */
data class PcHealthInfo(
    val ok: Boolean,
    val name: String,
    val savePath: String,
    val freeBytes: Long,
    val version: String,
    val latencyMs: Long,
) {
    companion object {
        fun fromJson(body: String, latencyMs: Long): PcHealthInfo? = runCatching {
            val json = JSONObject(body)
            PcHealthInfo(
                ok = json.optBoolean("ok", false),
                name = json.optString("name", ""),
                savePath = json.optString("savePath", ""),
                freeBytes = json.optLong("freeBytes", 0L),
                version = json.optString("version", "?"),
                latencyMs = latencyMs,
            )
        }.getOrNull()
    }
}

/** Processing lane state reported by `GET /process/status`. */
enum class PcProcessState {
    IDLE,
    QUEUED,
    PROCESSING,
    COMPLETE,
    ERROR,
    UNKNOWN;

    companion object {
        fun fromWire(value: String?): PcProcessState = when (value?.lowercase()) {
            "idle" -> IDLE
            "queued" -> QUEUED
            "processing" -> PROCESSING
            "complete", "completed" -> COMPLETE
            "error", "failed" -> ERROR
            else -> UNKNOWN
        }
    }
}

/** Capabilities and job progress from the PC processing worker (stub-friendly). */
data class PcProcessStatus(
    val ok: Boolean,
    val state: PcProcessState,
    val capabilities: List<String>,
    val activeJobId: String?,
    val progress: Int,
    val message: String,
) {
    companion object {
        fun fromJson(body: String): PcProcessStatus? = runCatching {
            val json = JSONObject(body)
            PcProcessStatus(
                ok = json.optBoolean("ok", false),
                state = PcProcessState.fromWire(json.optString("state", null)),
                capabilities = json.optJSONArray("capabilities").toStringList(),
                activeJobId = json.optString("activeJobId", null)?.takeIf { it.isNotBlank() },
                progress = json.optInt("progress", 0).coerceIn(0, 100),
                message = json.optString("message", ""),
            )
        }.getOrNull()
    }
}

/** Optional body for `POST /process/start`. */
data class PcProcessStartRequest(
    val bundleName: String? = null,
    val job: String = "index",
) {
    fun toJsonBody(): String = JSONObject().apply {
        bundleName?.let { put("bundleName", it) }
        put("job", job)
    }.toString()
}

/** Acknowledgement from `POST /process/start`. */
data class PcProcessStartResponse(
    val ok: Boolean,
    val jobId: String?,
    val state: PcProcessState,
    val message: String,
) {
    companion object {
        fun fromJson(body: String): PcProcessStartResponse? = runCatching {
            val json = JSONObject(body)
            PcProcessStartResponse(
                ok = json.optBoolean("ok", false),
                jobId = json.optString("jobId", null)?.takeIf { it.isNotBlank() },
                state = PcProcessState.fromWire(json.optString("state", null)),
                message = json.optString("message", ""),
            )
        }.getOrNull()
    }
}

private fun JSONArray?.toStringList(): List<String> {
    if (this == null) return emptyList()
    return buildList(length()) {
        for (i in 0 until length()) {
            optString(i, "").takeIf { it.isNotBlank() }?.let { add(it) }
        }
    }
}
