package com.caseforge.scanner.ui.session

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class WizardCopyRoot(
    val engine_bay: WizardStepCopy,
    val door_jamb: DoorJambCopy,
    val dashboard: DashboardCopy,
)

@Serializable
data class WizardStepCopy(
    val title: String,
    val hint: String,
    val skip_label: String = "Skip",
)

@Serializable
data class DoorJambCopy(
    val title: String,
    val hint: String,
    val skip_warning: String,
    val manual_label: String = "Edit VIN",
    val confirm_label: String = "Use this VIN",
)

@Serializable
data class DashboardCopy(
    val title: String,
    val instructions: List<String>,
    val skip_note: String,
)

object SessionWizardCopy {
    private val json = Json { ignoreUnknownKeys = true }

    fun load(context: Context): WizardCopyRoot {
        val text = context.assets.open("session/wizard_copy.json").bufferedReader().use { it.readText() }
        return json.decodeFromString(text)
    }
}
