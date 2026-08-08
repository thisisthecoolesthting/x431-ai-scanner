package com.caseforge.scanner.planb

import android.content.Context

/**
 * Bundled partner checklist for Tier 4 (manual/partner PCM and security provisioning).
 * Asset mirrors [docs/Tier4-Manual-Flash-Runbook.md] under the CU1 tree.
 */
object Tier4ManualFlashRunbook {

    const val ASSET_PATH = "planb/Tier4-Manual-Flash-Runbook.md"

    /** Line-oriented body for dialogs; trims trailing spaces, preserves blank paragraph breaks as empty strings. */
    fun loadLines(context: Context): List<String> =
        runCatching {
            context.assets.open(ASSET_PATH).bufferedReader().use { reader ->
                reader.readLines().map { it.trimEnd() }
            }
        }.getOrElse { embeddedFallback }

    /** Short title for dialogs / links */
    fun titleShort(): String = "Tier 4 manual flash — partners"

    private val embeddedFallback: List<String> = listOf(
        "Tier 4 manual flash runbook",
        "",
        "(Bundled markdown missing — reinstall or update app.)",
        "",
        "Programming and security provisioning stay on licensed OEM or partner tools only.",
        "See repo docs/Tier4-Manual-Flash-Runbook.md for the canonical runbook.",
    )
}
