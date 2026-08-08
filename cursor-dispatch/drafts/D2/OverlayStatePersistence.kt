package com.caseforge.scanner.overlay

import android.content.Context
import android.util.Log
import com.caseforge.scanner.engine.EngineState
import com.caseforge.scanner.engine.ScreenKind
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import java.io.File

/**
 * Pure Kotlin helper for idempotent save/load of EngineState snapshot to cacheDir/overlay_state.json.
 * Never throws; returns null on any failure (corrupt file, I/O error, deserialization fail).
 * Safe to call from any thread.
 */
object OverlayStatePersistence {
    private const val TAG = "X431Agent.OverlayState"
    private const val STATE_FILE_NAME = "overlay_state.json"

    @Serializable
    data class OverlayStatSnapshot(
        @SerialName("screen_kind")
        val screenKind: String,
        @SerialName("dtc_list")
        val dtcList: List<String> = emptyList(),
        @SerialName("timestamp_ms")
        val timestampMs: Long = System.currentTimeMillis(),
    )

    /**
     * Persist the current EngineState to cacheDir/overlay_state.json.
     * Extracts screenKind and the last DTC list from the state.
     * Idempotent; never throws.
     */
    fun save(context: Context, state: EngineState): Boolean = runCatching {
        val stateFile = File(context.cacheDir, STATE_FILE_NAME)
        val snap = OverlayStatSnapshot(
            screenKind = state.screen.name,
            dtcList = state.detectedDtcs.toList(),
            timestampMs = System.currentTimeMillis()
        )
        val json = Json.encodeToString(snap)
        stateFile.writeText(json)
        Log.d(TAG, "State saved: screen=${snap.screenKind}, dtcs=${snap.dtcList.size}, file=${stateFile.absolutePath}")
        true
    }.onFailure { t ->
        Log.w(TAG, "State save failed: ${t.message}", t)
    }.getOrNull() ?: false

    /**
     * Restore EngineState from cacheDir/overlay_state.json if it exists and is valid.
     * Returns null if the file doesn't exist, is corrupt, or deserialization fails.
     * Never throws.
     */
    fun load(context: Context): EngineState? = runCatching {
        val stateFile = File(context.cacheDir, STATE_FILE_NAME)
        if (!stateFile.exists()) {
            Log.d(TAG, "State file does not exist: ${stateFile.absolutePath}")
            return@runCatching null
        }
        val json = stateFile.readText()
        val snap: OverlayStatSnapshot = Json.decodeFromString(json)
        Log.d(TAG, "State loaded: screen=${snap.screenKind}, dtcs=${snap.dtcList.size}")

        // Reconstruct EngineState with the persisted screen and DTC list.
        // Other fields (health, errors, etc.) are reset to defaults since they're transient.
        val screenKind = ScreenKind.valueOf(snap.screenKind)
        EngineState.EMPTY.copy(
            screen = screenKind,
            detectedDtcs = snap.dtcList.toSet()
        )
    }.onFailure { t ->
        Log.w(TAG, "State load failed: ${t.message}", t)
    }.getOrNull()

    /**
     * Delete the state file. Used on graceful service shutdown or reset.
     * Never throws.
     */
    fun clear(context: Context): Boolean = runCatching {
        val stateFile = File(context.cacheDir, STATE_FILE_NAME)
        if (stateFile.exists()) {
            stateFile.delete()
            Log.d(TAG, "State file cleared")
            true
        } else {
            false
        }
    }.onFailure { t ->
        Log.w(TAG, "State clear failed: ${t.message}", t)
    }.getOrNull() ?: false
}
