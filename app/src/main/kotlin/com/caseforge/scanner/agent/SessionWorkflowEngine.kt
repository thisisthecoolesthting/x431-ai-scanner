package com.caseforge.scanner.agent

import android.content.Context
import com.caseforge.scanner.agent.discovery.DiscoveryReport
import com.caseforge.scanner.agent.session.BackgroundObdSnapshot
import com.caseforge.scanner.agent.session.DiagnosticPhotoInsights
import com.caseforge.scanner.agent.session.DiagnosticPhotoInsightsCodec
import com.caseforge.scanner.agent.session.SessionDiagnosticVision
import com.caseforge.scanner.agent.session.SessionTokenAccounting
import com.caseforge.scanner.ai.ClaudeClient
import com.caseforge.scanner.data.SettingsRepo
import com.caseforge.scanner.data.session.CustomerSessionEntity
import com.caseforge.scanner.planb.programming.ProgrammingGate
import com.caseforge.scanner.transfer.SessionEventLogger
import com.caseforge.scanner.ui.session.SessionVisualComposer
import com.caseforge.scanner.ui.session.VisualAttachment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * New Session chat orchestrator — one technician-facing question per turn.
 * Primary model: [SettingsRepo.model] (defaults to claude-sonnet-4-6 via env migration).
 * Wizard photos are analyzed by [SessionDiagnosticVision] and cached on [CustomerSessionEntity.photoDiagnosticJson].
 */
class SessionWorkflowEngine(
    private val context: Context,
    private val settings: SettingsRepo,
) {
    companion object {
        /** Active New Session id for agent actuation audit logs ([PendingActionActuation]). */
        fun activeSessionIdForActuationLog(): String? =
            com.caseforge.scanner.agent.session.SessionCopilotRegistry.active?.sessionId
    }

    private val vision = SessionDiagnosticVision(context, settings)

    data class ChatTurn(
        val role: String,
        val text: String,
    )

    data class Reply(
        val question: String,
        val detailExpansion: String? = null,
        val blockedTier4: Boolean = false,
        val visualAttachments: List<VisualAttachment> = emptyList(),
    )

    /** Multimodal + text bundle for a chat turn. */
    data class SessionChatContext(
        val textContext: String,
        val imageBlocks: List<ClaudeClient.ContentBlock.Image>,
    )

    private val tier4BlockPatterns = listOf(
        Regex("program\\s+(key|fob|remote)", RegexOption.IGNORE_CASE),
        Regex("key\\s+program", RegexOption.IGNORE_CASE),
        Regex("flash\\s+(pcm|ecu|module|tcm)", RegexOption.IGNORE_CASE),
        Regex("immobilizer\\s+program", RegexOption.IGNORE_CASE),
        Regex("skim|skreem|pats\\s+learn", RegexOption.IGNORE_CASE),
    )

    /** Automatic vision pass when session chat opens after the wizard. */
    suspend fun runInitialPhotoVision(
        sessionId: String,
        photoPaths: List<String>,
        vin: String?,
        priorVisits: CustomerSessionEntity?,
    ): DiagnosticPhotoInsights? = withContext(Dispatchers.IO) {
        if (photoPaths.isEmpty()) return@withContext null
        if (settings.claudeApiKey.isBlank()) {
            return@withContext DiagnosticPhotoInsightsCodec.decode(priorVisits?.photoDiagnosticJson)
        }
        val result = vision.analyzePaths(sessionId, photoPaths, vin, vinOcrText = vin)
        result.insights ?: DiagnosticPhotoInsightsCodec.decode(priorVisits?.photoDiagnosticJson)
    }

    suspend fun nextQuestion(
        sessionId: String,
        vin: String?,
        photoPaths: List<String>,
        discoveryReport: DiscoveryReport?,
        dtcSummary: String?,
        priorVisits: CustomerSessionEntity?,
        needDescription: String,
        transcript: List<ChatTurn>,
        obdSnapshot: BackgroundObdSnapshot? = null,
        photoInsights: DiagnosticPhotoInsights? = null,
        onStreamingText: (suspend (String) -> Unit)? = null,
    ): Reply = withContext(Dispatchers.IO) {
        SessionEventLogger.log(
            context, sessionId, "chat_turn",
            detail = needDescription.take(200),
            extra = mapOf("transcriptSize" to transcript.size.toString()),
        )

        if (tier4BlockPatterns.any { it.containsMatchIn(needDescription) } ||
            transcript.any { tier4BlockPatterns.any { p -> p.containsMatchIn(it.text) } }
        ) {
            val blocked = ProgrammingGate.TIER4_BLOCKED.message ?: "Tier 4 blocked"
            SessionEventLogger.log(context, sessionId, "tier4_blocked", detail = blocked)
            return@withContext Reply(
                question = blocked,
                detailExpansion = "Use Settings → Plan B → Programming reference for partner/manual steps.",
                blockedTier4 = true,
            )
        }

        val apiKey = settings.claudeApiKey
        if (apiKey.isBlank()) {
            return@withContext Reply(
                question = "Set a Claude API key in Settings to enable session AI.",
            )
        }

        val insights = photoInsights
            ?: DiagnosticPhotoInsightsCodec.decode(priorVisits?.photoDiagnosticJson)

        val client = ClaudeClient(apiKey = apiKey, model = settings.model)
        val system = buildString {
            appendLine("You are Together Car Works session assistant for a professional technician.")
            appendLine("Ask exactly ONE clear next diagnostic question per reply.")
            appendLine("Focus on physical checks first (battery terminals, fuses, connectors, fuel level).")
            appendLine("Do not suggest in-app programming, key coding, or module flash.")
            appendLine("If the tech already answered, acknowledge briefly then ask the next single question.")
            appendLine("Keep the main reply under 3 sentences.")
            appendLine(SessionDiagnosticVision.VISUAL_DISCLAIMER)
            insights?.let {
                appendLine("Use cached photo vision insights as advisory context — verify on vehicle.")
            }
        }

        val chatCtx = buildContext(
            vin = vin,
            photoPaths = photoPaths,
            discoveryReport = discoveryReport,
            dtcSummary = dtcSummary,
            priorVisits = priorVisits,
            needDescription = needDescription,
            transcript = transcript,
            photoInsights = insights,
        )

        val blocks = mutableListOf<ClaudeClient.ContentBlock>()
        blocks += ClaudeClient.ContentBlock.Text(text = chatCtx.textContext)
        blocks += chatCtx.imageBlocks

        val messages = listOf(ClaudeClient.Message("user", blocks))

        val response = runCatching {
            client.sendMessages(system = system, messages = messages, maxTokens = 512)
        }.getOrElse { e ->
            return@withContext Reply(question = "AI error: ${e.message?.take(120)}")
        }

        SessionTokenAccounting.recordApiCall(
            response = response,
            system = system,
            messages = messages,
            fallbackOutputText = response.firstText(),
            visionImages = chatCtx.imageBlocks.size,
            isChatTurn = true,
        )

        val text = response.firstText()?.trim().orEmpty().ifBlank { "What is the primary symptom right now?" }
        if (settings.deepseekStreamingEnabled && onStreamingText != null) {
            val words = text.split(Regex("\\s+")).filter { it.isNotBlank() }
            if (words.isNotEmpty()) {
                val chunks = words.chunked(3).map { it.joinToString(" ") }
                val partial = StringBuilder()
                chunks.forEachIndexed { index, chunk ->
                    if (partial.isNotEmpty()) partial.append(' ')
                    partial.append(chunk)
                    withContext(Dispatchers.Main) { onStreamingText(partial.toString()) }
                    if (index < chunks.lastIndex) delay(24)
                }
            }
        }
        SessionEventLogger.log(context, sessionId, "ai_reply", detail = text.take(300))

        val visuals = buildList {
            insights?.let { addAll(SessionVisualComposer.attachmentsForPhotoInsights(it)) }
            addAll(SessionVisualComposer.attachmentsForAgentText(text, obdSnapshot))
        }
        if (visuals.isNotEmpty()) {
            SessionEventLogger.log(
                context,
                sessionId,
                "ai_visual_attach",
                extra = mapOf("count" to visuals.size.toString()),
            )
        }

        Reply(question = text, visualAttachments = visuals)
    }

    /**
     * Builds text + optional fresh multimodal images for ongoing chat.
     * Cached [DiagnosticPhotoInsights] are always in text; images re-sent when session photos are < 24h old.
     */
    fun buildContext(
        vin: String?,
        photoPaths: List<String>,
        discoveryReport: DiscoveryReport?,
        dtcSummary: String?,
        priorVisits: CustomerSessionEntity?,
        needDescription: String,
        transcript: List<ChatTurn>,
        photoInsights: DiagnosticPhotoInsights?,
    ): SessionChatContext {
        val userContext = buildString {
            vin?.let { appendLine("VIN: $it") }
            priorVisits?.lastNeedDescription?.let { appendLine("Prior visit need: $it") }
            priorVisits?.lastDtcSummary?.let { appendLine("Prior DTC summary: $it") }
            dtcSummary?.let { appendLine("Current DTC snapshot: $it") }
            discoveryReport?.let { appendLine("Link readiness: ${it.recommendedAction}") }
            photoInsights?.let { insights ->
                appendLine("Photo vision insights (${insights.confidence} confidence):")
                DiagnosticPhotoInsightsCodec.summaryBullets(insights).forEach { appendLine("  • $it") }
                if (insights.suggestedNextSteps.isNotEmpty()) {
                    appendLine("Suggested checks from photos:")
                    insights.suggestedNextSteps.forEach { appendLine("  • $it") }
                }
                appendLine(insights.disclaimer)
            }
            appendLine("Tech need: $needDescription")
            if (transcript.isNotEmpty()) {
                appendLine("Conversation so far:")
                transcript.forEach { appendLine("  ${it.role}: ${it.text}") }
            }
            appendLine()
            appendLine("Reply with your single next question for the technician.")
        }

        val imageBlocks = mutableListOf<ClaudeClient.ContentBlock.Image>()
        if (SessionDiagnosticVision.photosFreshForMultimodal(photoPaths)) {
            photoPaths.take(3).forEach { path ->
                SessionDiagnosticVision.encodePhotoBase64(path)?.let { b64 ->
                    imageBlocks += ClaudeClient.ContentBlock.Image(
                        source = ClaudeClient.ContentBlock.ImageSource(
                            mediaType = "image/jpeg",
                            data = b64,
                        ),
                    )
                }
            }
        }

        return SessionChatContext(userContext, imageBlocks)
    }

    suspend fun describeSessionPhotoStub(sessionId: String, photoPath: String, role: String = "photo"): String? =
        withContext(Dispatchers.IO) {
            val result = vision.analyzePhoto(sessionId, photoPath, role)
            result.insights?.let { vision.formatInsightsForChat(it) }
        }
}
