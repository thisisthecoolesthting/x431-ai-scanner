package com.caseforge.scanner.ui.setup

import androidx.annotation.StringRes
import com.caseforge.scanner.R

/**
 * Offline help cards for [SetupLiveWizardScreen] — one quick-help + fix-tips string per [SetupLiveStep].
 */
object SetupStepHelp {

    data class Card(
        @StringRes val quickHelp: Int,
        @StringRes val fixTips: Int,
        val promptId: String,
    )

    fun card(step: SetupLiveStep): Card = when (step) {
        SetupLiveStep.APP_HEALTH -> Card(
            R.string.setup_help_app_health_quick,
            R.string.setup_help_app_health_fix,
            "setup_ai_app_health",
        )
        SetupLiveStep.SHOP_DESK -> Card(
            R.string.setup_help_shop_desk_quick,
            R.string.setup_help_shop_desk_fix,
            "setup_ai_shop_desk",
        )
        SetupLiveStep.LINK_TRANSPORT -> Card(
            R.string.setup_help_link_transport_quick,
            R.string.setup_help_link_transport_fix,
            "setup_ai_link_transport",
        )
        SetupLiveStep.CAMERA -> Card(
            R.string.setup_help_camera_quick,
            R.string.setup_help_camera_fix,
            "setup_ai_camera_vin",
        )
        SetupLiveStep.SESSION_BOOTSTRAP -> Card(
            R.string.setup_help_session_bootstrap_quick,
            R.string.setup_help_session_bootstrap_fix,
            "setup_ai_session_bootstrap",
        )
        SetupLiveStep.HARVEST_PATH -> Card(
            R.string.setup_help_harvest_path_quick,
            R.string.setup_help_harvest_path_fix,
            "setup_ai_harvest_path",
        )
        SetupLiveStep.TIER4_TRIAL -> Card(
            R.string.setup_help_tier4_trial_quick,
            R.string.setup_help_tier4_trial_fix,
            "setup_ai_tier4_trial",
        )
        SetupLiveStep.MARK_COMPLETE -> Card(
            R.string.setup_help_mark_complete_quick,
            R.string.setup_help_mark_complete_fix,
            "setup_ai_complete",
        )
    }
}
