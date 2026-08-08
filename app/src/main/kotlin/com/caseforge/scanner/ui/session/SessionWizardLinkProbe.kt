package com.caseforge.scanner.ui.session

import android.content.Context
import com.caseforge.scanner.agent.SessionBackgroundScanner
import com.caseforge.scanner.data.SettingsRepo

/**
 * Best-effort OBD link probe during the intake wizard (dashboard / parts steps).
 * Returns a user-facing error line when connect was attempted and failed; null when OK or not applicable.
 */
object SessionWizardLinkProbe {

    fun linkFailureBannerMessage(linkStatus: String): String? {
        val trimmed = linkStatus.trim()
        if (trimmed.startsWith("OBD:")) {
            return trimmed.removePrefix("OBD:").trim().takeIf { it.isNotBlank() }
                ?.let { "Adapter link failed: $it. You can continue the wizard; chat will retry in the background." }
                ?: "Adapter link failed. You can continue the wizard; chat will retry in the background."
        }
        return null
    }

    suspend fun probe(context: Context, sessionId: String, vinHint: String?): String? {
        val settings = SettingsRepo(context.applicationContext)
        if (!settings.isPlanBTierEffective(0) &&
            !settings.nativeObdExperimental &&
            !settings.launchPlanABridgeEnabled
        ) {
            return null
        }
        val snap = SessionBackgroundScanner(context.applicationContext, settings)
            .run(sessionId, vinHint)
        return linkFailureBannerMessage(snap.linkStatus)
    }
}
