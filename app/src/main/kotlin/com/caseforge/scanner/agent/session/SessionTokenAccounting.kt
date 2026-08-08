package com.caseforge.scanner.agent.session

import android.content.Context
import com.caseforge.scanner.ai.ClaudeClient
import com.caseforge.scanner.data.SettingsRepo
import com.caseforge.scanner.data.session.CustomerSessionRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Per-session and cumulative AI token / cost accounting for wizard chat + overlay agent.
 * Display-only pricing — not billing truth.
 *
 * List rates (claude-sonnet-4-6, advisory):
 *   - input:  $3.00 / MTok
 *   - output: $15.00 / MTok
 * Vision: input usage from API when present; otherwise +[VISION_TOKEN_ESTIMATE_PER_IMAGE] per image.
 */
object SessionTokenAccounting {

    /** USD per 1M input tokens — Sonnet 4.6 published list rate (display only). */
    const val INPUT_USD_PER_MTOK = 3.0

    /** USD per 1M output tokens — Sonnet 4.6 published list rate (display only). */
    const val OUTPUT_USD_PER_MTOK = 15.0

    /** Flat token estimate per vision image when API usage is missing. */
    const val VISION_TOKEN_ESTIMATE_PER_IMAGE = 1_600

    @Serializable
    data class Totals(
        val inputTokens: Long = 0L,
        val outputTokens: Long = 0L,
        val visionCalls: Int = 0,
        val chatTurns: Int = 0,
    ) {
        fun estCostUsd(): Double = estimateCostUsd(inputTokens, outputTokens)
    }

    /** Lifetime rollup (all ended sessions). */
    data class LifetimeStats(
        val inputTokens: Long = 0L,
        val outputTokens: Long = 0L,
        val visionCalls: Int = 0,
        val chatTurns: Int = 0,
        val sessions: Int = 0,
        val estCostUsd: Double = 0.0,
    )

    private const val PREFS_NAME = "tcw_session_ai_cost"
    private const val KEY_LIFETIME_INPUT = "lifetime_input_tokens"
    private const val KEY_LIFETIME_OUTPUT = "lifetime_output_tokens"
    private const val KEY_LIFETIME_VISION = "lifetime_vision_calls"
    private const val KEY_LIFETIME_CHAT = "lifetime_chat_turns"
    private const val KEY_LIFETIME_SESSIONS = "lifetime_sessions"

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val _lifetime = MutableStateFlow(LifetimeStats())
    val lifetime: StateFlow<LifetimeStats> = _lifetime.asStateFlow()

    @Volatile
    var currentSession: Totals = Totals()
        private set

    @Volatile
    private var activeSessionId: String? = null

    @Synchronized
    fun beginSession(sessionId: String? = null) {
        currentSession = Totals()
        activeSessionId = sessionId
    }

    @Synchronized
    fun recordTokens(inputTokens: Int, outputTokens: Int) {
        if (inputTokens <= 0 && outputTokens <= 0) return
        currentSession = currentSession.copy(
            inputTokens = currentSession.inputTokens + inputTokens.coerceAtLeast(0),
            outputTokens = currentSession.outputTokens + outputTokens.coerceAtLeast(0),
        )
        foldLifetime(inputTokens.coerceAtLeast(0), outputTokens.coerceAtLeast(0), visionDelta = 0, chatDelta = 0)
    }

    /**
     * Record one Claude round-trip. Uses [response.usage] when present; otherwise chars/4 heuristic
     * over [system] + [messages] and [fallbackOutputText].
     */
    @Synchronized
    fun recordApiCall(
        response: ClaudeClient.Response?,
        system: String?,
        messages: List<ClaudeClient.Message>,
        fallbackOutputText: String?,
        visionImages: Int = 0,
        isChatTurn: Boolean = false,
        isVisionCall: Boolean = false,
    ) {
        val usage = response?.usage
        val (inTok, outTok) = if (usage != null && (usage.inputTokens > 0 || usage.outputTokens > 0)) {
            usage.inputTokens to usage.outputTokens
        } else {
            estimateTokens(system, messages, fallbackOutputText, visionImages)
        }

        val chatDelta = if (isChatTurn) 1 else 0
        val visionDelta = if (isVisionCall) 1 else 0

        currentSession = currentSession.copy(
            inputTokens = currentSession.inputTokens + inTok,
            outputTokens = currentSession.outputTokens + outTok,
            chatTurns = currentSession.chatTurns + chatDelta,
            visionCalls = currentSession.visionCalls + visionDelta,
        )
        foldLifetime(inTok, outTok, visionDelta, chatDelta)
    }

    @Synchronized
    fun endSession(
        context: Context,
        settings: SettingsRepo,
        sessionId: String? = activeSessionId,
        vin: String? = null,
    ) {
        val endedAt = System.currentTimeMillis()
        val totals = currentSession

        settings.recordEndedSessionAiCost(totals, endedAt)

        val newSessions = _lifetime.value.sessions + 1
        _lifetime.value = _lifetime.value.copy(sessions = newSessions)

        persistLifetime(context)

        if (!vin.isNullOrBlank() && hasActivity(totals)) {
            runCatching {
                runBlocking {
                    CustomerSessionRepository(context.applicationContext)
                        .persistAiUsage(vin, sessionId ?: activeSessionId.orEmpty(), totals, endedAt)
                }
            }
        }

        currentSession = Totals()
        activeSessionId = null
    }

    @Synchronized
    fun loadLifetime(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val inTok = prefs.getLong(KEY_LIFETIME_INPUT, 0L)
        val outTok = prefs.getLong(KEY_LIFETIME_OUTPUT, 0L)
        val vision = prefs.getInt(KEY_LIFETIME_VISION, 0)
        val chat = prefs.getInt(KEY_LIFETIME_CHAT, 0)
        val sess = prefs.getInt(KEY_LIFETIME_SESSIONS, 0)
        _lifetime.value = LifetimeStats(
            inputTokens = inTok,
            outputTokens = outTok,
            visionCalls = vision,
            chatTurns = chat,
            sessions = sess,
            estCostUsd = estimateCostUsd(inTok, outTok),
        )
    }

    fun computeEstimatedSessionCostUsd(costUsd: Double, label: String): String =
        "~$${formatMoney(costUsd)} ($label)"

    fun computeEstimatedSessionCostUsd(totals: Totals, label: String): String =
        computeEstimatedSessionCostUsd(totals.estCostUsd(), label)

    fun formatSettingsPrimaryLine(settings: SettingsRepo): String {
        val last = settings.lastSessionAiCostUsd
        return if (last > 0.0) {
            "Estimated AI cost (last session): ~$${formatMoney(last)}"
        } else {
            "Estimated AI cost (last session): —"
        }
    }

    fun formatSettingsSubtext(): String = "Sonnet 4.6 list rates; advisory only"

    fun formatSettingsTodayLine(settings: SettingsRepo): String? {
        settings.rollTodayIfNeeded()
        val today = settings.todayAiCostUsd
        if (today <= 0.0) return null
        return "Today cumulative: ~$${formatMoney(today)}"
    }

    fun formatSettingsRecentAvgLine(settings: SettingsRepo): String? {
        val costs = settings.recentSessionCosts
        if (costs.isEmpty()) return null
        return computeEstimatedSessionCostUsd(costs.average(), "avg (last ${costs.size})")
    }

    fun encodeSnapshot(totals: Totals, endedAtMs: Long): String {
        val snap = AiUsageSnapshot(
            inputTokens = totals.inputTokens,
            outputTokens = totals.outputTokens,
            visionCalls = totals.visionCalls,
            chatTurns = totals.chatTurns,
            estCostUsd = totals.estCostUsd(),
            endedAtMs = endedAtMs,
        )
        return json.encodeToString(snap)
    }

    @Serializable
    data class AiUsageSnapshot(
        val inputTokens: Long = 0L,
        val outputTokens: Long = 0L,
        val visionCalls: Int = 0,
        val chatTurns: Int = 0,
        val estCostUsd: Double = 0.0,
        val endedAtMs: Long = 0L,
    )

    private fun hasActivity(totals: Totals): Boolean =
        totals.inputTokens > 0 || totals.outputTokens > 0 || totals.visionCalls > 0 || totals.chatTurns > 0

    private fun estimateTokens(
        system: String?,
        messages: List<ClaudeClient.Message>,
        outputText: String?,
        visionImages: Int,
    ): Pair<Int, Int> {
        var inputChars = system?.length ?: 0
        for (msg in messages) {
            for (block in msg.content) {
                when (block) {
                    is ClaudeClient.ContentBlock.Text -> inputChars += block.text.length
                    is ClaudeClient.ContentBlock.Image -> inputChars += 4_000
                    is ClaudeClient.ContentBlock.ToolUse -> inputChars += block.name.length + 64
                    is ClaudeClient.ContentBlock.ToolResult -> inputChars += 128
                }
            }
        }
        val visionExtra = visionImages.coerceAtLeast(0) * VISION_TOKEN_ESTIMATE_PER_IMAGE
        val inTok = tokensFromChars(inputChars) + visionExtra
        val outTok = tokensFromChars(outputText?.length ?: 0)
        return inTok to outTok
    }

    fun tokensFromChars(chars: Int): Int = if (chars <= 0) 0 else (chars + 3) / 4

    fun estimateCostUsd(inputTokens: Long, outputTokens: Long): Double {
        val inCost = inputTokens.toDouble() / 1_000_000.0 * INPUT_USD_PER_MTOK
        val outCost = outputTokens.toDouble() / 1_000_000.0 * OUTPUT_USD_PER_MTOK
        return inCost + outCost
    }

    fun formatMoney(usd: Double): String = String.format(Locale.US, "%.2f", usd)

    private fun foldLifetime(inTok: Int, outTok: Int, visionDelta: Int, chatDelta: Int) {
        val life = _lifetime.value
        val newIn = life.inputTokens + inTok
        val newOut = life.outputTokens + outTok
        _lifetime.value = life.copy(
            inputTokens = newIn,
            outputTokens = newOut,
            visionCalls = life.visionCalls + visionDelta,
            chatTurns = life.chatTurns + chatDelta,
            estCostUsd = estimateCostUsd(newIn, newOut),
        )
    }

    private fun persistLifetime(context: Context) {
        val life = _lifetime.value
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_LIFETIME_INPUT, life.inputTokens)
            .putLong(KEY_LIFETIME_OUTPUT, life.outputTokens)
            .putInt(KEY_LIFETIME_VISION, life.visionCalls)
            .putInt(KEY_LIFETIME_CHAT, life.chatTurns)
            .putInt(KEY_LIFETIME_SESSIONS, life.sessions)
            .apply()
    }
}
