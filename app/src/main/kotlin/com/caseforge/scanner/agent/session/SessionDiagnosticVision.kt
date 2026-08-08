package com.caseforge.scanner.agent.session

import android.content.Context
import android.util.Base64
import com.caseforge.scanner.ai.ClaudeClient
import com.caseforge.scanner.agent.session.SessionTokenAccounting
import com.caseforge.scanner.data.SettingsRepo
import com.caseforge.scanner.planb.MarqueWedgeConfig
import com.caseforge.scanner.transfer.SessionEventLogger
import com.caseforge.scanner.ui.session.ActiveCustomerSession
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Multimodal Claude vision pass over wizard photos (engine bay, door jamb, dashboard).
 * Advisory only — never triggers programming or flash workflows.
 */
class SessionDiagnosticVision(
    private val context: Context,
    private val settings: SettingsRepo,
) {
    data class PhotoSlot(
        val role: String,
        val label: String,
        val path: String,
    )

    data class AnalyzeResult(
        val insights: DiagnosticPhotoInsights?,
        val error: String? = null,
        val imagesSent: Int = 0,
    )

    suspend fun analyzeWizardPhotos(
        sessionId: String,
        session: ActiveCustomerSession,
        vinOcrText: String? = session.vin,
    ): AnalyzeResult = withContext(Dispatchers.IO) {
        val slots = photoSlots(session)
        if (slots.isEmpty()) {
            return@withContext AnalyzeResult(null, error = "no_photos")
        }
        analyzeSlots(
            sessionId = sessionId,
            slots = slots,
            vin = session.vin,
            vinOcrText = vinOcrText,
            eventKind = "photo_vision_wizard",
        )
    }

    suspend fun analyzePhoto(sessionId: String, photoPath: String, role: String): AnalyzeResult =
        withContext(Dispatchers.IO) {
            val slot = PhotoSlot(role = role, label = roleLabel(role), path = photoPath)
            analyzeSlots(sessionId, listOf(slot), vin = null, vinOcrText = null, eventKind = "photo_vision_single")
        }

    suspend fun analyzePaths(
        sessionId: String,
        paths: List<String>,
        vin: String?,
        vinOcrText: String? = vin,
    ): AnalyzeResult = withContext(Dispatchers.IO) {
        val slots = paths.mapIndexed { i, path ->
            val role = listOf("engine_bay", "door_jamb", "dashboard").getOrElse(i) { "photo_$i" }
            PhotoSlot(role, roleLabel(role), path)
        }
        analyzeSlots(sessionId, slots, vin, vinOcrText, eventKind = "photo_vision_batch")
    }

    private suspend fun analyzeSlots(
        sessionId: String,
        slots: List<PhotoSlot>,
        vin: String?,
        vinOcrText: String?,
        eventKind: String,
    ): AnalyzeResult {
        val apiKey = settings.claudeApiKey
        if (apiKey.isBlank()) {
            return AnalyzeResult(null, error = "missing_api_key")
        }

        val encoded = slots.take(MAX_IMAGES).mapNotNull { slot ->
            encodePhotoBase64(slot.path)?.let { slot to it }
        }
        if (encoded.isEmpty()) {
            return AnalyzeResult(null, error = "photos_unreadable")
        }

        val model = settings.model
        SessionEventLogger.log(
            context,
            sessionId,
            "${eventKind}_start",
            extra = mapOf(
                "model" to model,
                "imageCount" to encoded.size.toString(),
                "roles" to encoded.joinToString(",") { it.first.role },
            ),
        )

        val wedgeLine = vin?.let { v ->
            MarqueWedgeConfig.findCardForVin(context, v)?.let { card ->
                "Marque wedge: ${card.marque} ${card.model} (${card.platformCode})"
            }
        }

        val userText = buildString {
            appendLine("Analyze these workshop photos for diagnostic clues.")
            appendLine(VISUAL_DISCLAIMER)
            vin?.let { appendLine("VIN (confirmed): $it") }
            vinOcrText?.takeIf { it != vin }?.let { appendLine("Door jamb OCR / sticker text: $it") }
            wedgeLine?.let { appendLine(it) }
            appendLine()
            encoded.forEach { (slot, _) ->
                appendLine("Image role: ${slot.role} — ${slot.label}")
            }
            appendLine()
            appendLine(
                "Return ONLY valid JSON (no markdown) matching: " +
                    """{"findings":[{"area":"engine_bay|door_jamb|dashboard","observation":"...","severity":"info|watch|concern"}],"confidence":"low|medium|high","suggestedNextSteps":["..."],"perPhoto":[{"role":"...","bullets":["..."]}]}""",
            )
            appendLine("Engine bay: leaks, corrosion, disconnected hoses, belt condition, fluid stains.")
            appendLine("Door jamb: VIN plate legibility, sticker tampering, structural damage at latch.")
            appendLine("Dashboard: MIL/warning lamps, telltales, odometer/mileage if visible.")
        }

        val blocks = mutableListOf<ClaudeClient.ContentBlock>()
        blocks += ClaudeClient.ContentBlock.Text(text = userText)
        encoded.forEach { (_, b64) ->
            blocks += ClaudeClient.ContentBlock.Image(
                source = ClaudeClient.ContentBlock.ImageSource(
                    mediaType = "image/jpeg",
                    data = b64,
                ),
            )
        }

        val client = ClaudeClient(apiKey = apiKey, model = model)
        val response = runCatching {
            client.sendMessages(
                system = SYSTEM_PROMPT,
                messages = listOf(ClaudeClient.Message("user", blocks)),
                maxTokens = 1024,
                temperature = 0.1,
            )
        }.getOrElse { e ->
            SessionEventLogger.log(context, sessionId, "${eventKind}_error", detail = e.message?.take(200).orEmpty())
            return AnalyzeResult(null, error = e.message?.take(120))
        }

        SessionTokenAccounting.recordApiCall(
            response = response,
            system = SYSTEM_PROMPT,
            messages = listOf(ClaudeClient.Message("user", blocks)),
            fallbackOutputText = response.firstText(),
            visionImages = encoded.size,
            isVisionCall = true,
        )

        val raw = response.firstText().orEmpty()
        val parsed = parseInsightsJson(raw)?.copy(
            analyzedAtMs = System.currentTimeMillis(),
            model = model,
            disclaimer = VISUAL_DISCLAIMER,
        )

        if (parsed == null) {
            SessionEventLogger.log(context, sessionId, "${eventKind}_parse_fail", detail = raw.take(300))
            return AnalyzeResult(null, error = "parse_failed", imagesSent = encoded.size)
        }

        SessionEventLogger.log(
            context,
            sessionId,
            "${eventKind}_complete",
            detail = "confidence=${parsed.confidence}",
            extra = mapOf(
                "model" to model,
                "imageCount" to encoded.size.toString(),
                "findingCount" to parsed.findings.size.toString(),
                "inputTokens" to (response.usage?.inputTokens?.toString() ?: "?"),
                "outputTokens" to (response.usage?.outputTokens?.toString() ?: "?"),
            ),
        )

        return AnalyzeResult(parsed, imagesSent = encoded.size)
    }

    fun formatInsightsForChat(insights: DiagnosticPhotoInsights): String {
        val bullets = DiagnosticPhotoInsightsCodec.summaryBullets(insights)
        return buildString {
            appendLine("Photo analysis (${insights.confidence} confidence):")
            bullets.forEach { appendLine("• $it") }
            if (insights.suggestedNextSteps.isNotEmpty()) {
                appendLine("Suggested checks:")
                insights.suggestedNextSteps.take(4).forEach { appendLine("• $it") }
            }
            appendLine(insights.disclaimer)
        }.trim()
    }

    companion object {
        const val VISUAL_DISCLAIMER =
            "Visual estimate — verify on vehicle. Advisory only; no auto flash or programming."

        private const val MAX_IMAGES = 3
        private const val MAX_BYTES = 4 * 1024 * 1024
        private const val SESSION_IMAGE_TTL_MS = 24L * 60 * 60 * 1000

        private const val SYSTEM_PROMPT =
            "You are a master automotive technician assistant analyzing workshop photos. " +
                "Infer only what is clearly visible. Never recommend in-app programming, key coding, or module flash. " +
                "All observations are visual estimates — the technician must verify on the vehicle."

        fun photoSlots(session: ActiveCustomerSession): List<PhotoSlot> = buildList {
            session.engineBayPhotoPath?.let {
                add(PhotoSlot("engine_bay", "Engine bay — leaks, hoses, belts, corrosion", it))
            }
            session.doorJambPhotoPath?.let {
                add(PhotoSlot("door_jamb", "Door jamb / VIN sticker", it))
            }
            session.dashboardPhotoPath?.let {
                add(PhotoSlot("dashboard", "Dashboard — warning lamps and odometer", it))
            }
        }

        fun roleLabel(role: String): String = when (role) {
            "engine_bay" -> "Engine bay"
            "door_jamb" -> "Door jamb / VIN"
            "dashboard" -> "Dashboard"
            else -> role
        }

        fun encodePhotoBase64(path: String): String? {
            val file = File(path)
            if (!file.isFile || !file.canRead()) return null
            if (file.length() > MAX_BYTES) return null
            return Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)
        }

        /** True when all paths exist and newest file is within 24h. */
        fun photosFreshForMultimodal(paths: List<String>): Boolean {
            val files = paths.mapNotNull { p ->
                val f = File(p)
                if (f.isFile) f else null
            }
            if (files.isEmpty()) return false
            val newest = files.maxOf { it.lastModified() }
            return System.currentTimeMillis() - newest <= SESSION_IMAGE_TTL_MS
        }

        fun parseInsightsJson(raw: String): DiagnosticPhotoInsights? {
            val trimmed = raw.trim()
            val jsonBody = when {
                trimmed.startsWith("{") -> trimmed
                else -> {
                    val fence = Regex("""```(?:json)?\s*([\s\S]*?)```""").find(trimmed)?.groupValues?.get(1)?.trim()
                    fence ?: Regex("""\{[\s\S]*\}""").find(trimmed)?.value
                }
            } ?: return null
            return DiagnosticPhotoInsightsCodec.json.decodeFromString(
                DiagnosticPhotoInsights.serializer(),
                jsonBody,
            )
        }
    }
}
