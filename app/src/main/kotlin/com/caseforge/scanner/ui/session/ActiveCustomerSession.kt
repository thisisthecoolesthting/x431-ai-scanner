package com.caseforge.scanner.ui.session

/**
 * In-memory payload passed from wizard completion into [SessionChatScreen].
 */
data class ActiveCustomerSession(
    val sessionId: String,
    val vin: String?,
    val engineBayPhotoPath: String?,
    val doorJambPhotoPath: String?,
    val dashboardPhotoPath: String?,
    val engineBayTriageSummary: String? = null,
    val engineBaySuggestedNextStep: String? = null,
    val engineBayVisionPromptHook: String? = null,
) {
    fun photoPaths(): List<String> = listOfNotNull(
        engineBayPhotoPath,
        doorJambPhotoPath,
        dashboardPhotoPath,
    )
}

/** Route names registered in [com.caseforge.scanner.MainActivity]. */
object SessionRoutes {
    const val WIZARD = "new_session_wizard"
    const val CHAT = "session_chat"
}

enum class WizardStep { ENGINE_BAY, DOOR_JAMB, DASHBOARD, PARTS_SCAN }
