package com.caseforge.scanner.agent

import com.caseforge.scanner.ai.ClaudeClient
import com.caseforge.scanner.ai.Prompts
import com.caseforge.scanner.data.SettingsRepo
import com.caseforge.scanner.ui.setup.SetupLiveStep
import com.caseforge.scanner.ui.setup.SetupStepHelp
import com.caseforge.scanner.ui.setup.SetupStepState
import com.caseforge.scanner.ui.setup.SetupStepStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * One-turn setup wizard assistant — no tool use, no [AgentRunner] loop.
 * Context: current step id + last failure JSON from [SetupStepState].
 */
class SetupAssistantAgent(
    private val settings: SettingsRepo,
) {
    @Serializable
    data class StepFailureContext(
        val stepId: String,
        val stepTitle: String,
        val status: String,
        val detail: String,
        val skipReason: String? = null,
    ) {
        fun toJson(): String = json.encodeToString(this)
    }

    suspend fun deepHelp(
        step: SetupLiveStep,
        failure: StepFailureContext?,
        userQuestion: String? = null,
    ): String = withContext(Dispatchers.IO) {
        val apiKey = settings.claudeApiKey
        if (apiKey.isBlank()) {
            return@withContext "Set a Claude API key in Settings (or complete the API key step) before using Deep help."
        }

        val promptId = SetupStepHelp.card(step).promptId
        val system = Prompts.setupAiSystem(promptId)
        val userMessage = buildUserMessage(step, failure, userQuestion)

        val client = ClaudeClient(apiKey = apiKey, model = settings.model)
        val response = runCatching {
            client.sendMessages(
                system = system,
                messages = listOf(
                    ClaudeClient.Message(
                        role = "user",
                        content = listOf(ClaudeClient.ContentBlock.Text(text = userMessage)),
                    ),
                ),
                maxTokens = 512,
                temperature = 0.2,
            )
        }.getOrElse { err ->
            return@withContext "AI error: ${err.message?.take(160) ?: "unknown"}"
        }

        response.firstText()?.trim().orEmpty().ifBlank {
            "Try the fix tips above, then re-run the step. If it still fails, check Settings → Shop & link self-tests."
        }
    }

    companion object {
        private val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        fun failureFromState(step: SetupLiveStep, state: SetupStepState): StepFailureContext? {
            if (state.status != SetupStepStatus.FAILED && state.status != SetupStepStatus.SKIPPED) {
                return null
            }
            return StepFailureContext(
                stepId = step.id,
                stepTitle = step.title,
                status = state.status.name,
                detail = state.detail,
                skipReason = state.skipReason,
            )
        }

        fun buildUserMessage(
            step: SetupLiveStep,
            failure: StepFailureContext?,
            userQuestion: String?,
        ): String = buildString {
            appendLine("Setup wizard step: ${step.title} (id=${step.id})")
            appendLine("Summary: ${step.summary}")
            failure?.let {
                appendLine()
                appendLine("Last step outcome JSON:")
                appendLine(it.toJson())
            }
            userQuestion?.trim()?.takeIf { it.isNotEmpty() }?.let {
                appendLine()
                appendLine("Technician question: $it")
            }
            appendLine()
            appendLine(
                "Help the operator complete this setup step. Interpret any failure detail, " +
                    "suggest concrete fixes (USB permission, Shop Desk URL, force-stop Launch OEM app, " +
                    "accessibility toggle, etc.), and keep the reply under 6 short sentences.",
            )
        }
    }
}
