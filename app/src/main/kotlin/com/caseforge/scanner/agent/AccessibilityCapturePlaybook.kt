package com.caseforge.scanner.agent

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/**
 * Stub playbook loader for OEM accessibility-driven capture workflows.
 * Bundled JSON under assets/agent/ — extend steps when oracle lanes define stable flows.
 */
@Serializable
data class AccessibilityCapturePlaybook(
    val id: String = "",
    val title: String = "",
    val notes: String = "",
    val steps: List<String> = emptyList(),
)

object AccessibilityCapturePlaybookLoader {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
    }

    /** Default asset path: `assets/agent/accessibility_capture_playbook.json` */
    fun loadOrNull(
        context: Context,
        assetRelativePath: String = "agent/accessibility_capture_playbook.json",
    ): AccessibilityCapturePlaybook? =
        runCatching {
            context.assets.open(assetRelativePath).bufferedReader().use { r ->
                json.decodeFromString<AccessibilityCapturePlaybook>(r.readText())
            }
        }.getOrNull()

    /** Non-null stub when asset is missing — keeps callers from branching on null during bring-up. */
    fun loadOrStub(context: Context): AccessibilityCapturePlaybook =
        loadOrNull(context) ?: AccessibilityCapturePlaybook(
            id = "stub-missing-asset",
            title = "Accessibility capture playbook (stub)",
            notes = "Asset agent/accessibility_capture_playbook.json not found or invalid.",
            steps = emptyList(),
        )
}
