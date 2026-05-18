package com.caseforge.scanner.agent

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process-wide singleton that aggregates input/output token counts and an estimated
 * USD cost for the current agent session AND the device lifetime.
 *
 * Pricing assumes Anthropic's claude-sonnet-4-5 published rates:
 *   - input:  $3  per 1,000,000 tokens
 *   - output: $15 per 1,000,000 tokens
 *
 * Lifetime totals are persisted to SharedPreferences ("caseforge_cost") so they
 * survive process death. The "current session" totals are kept in memory only.
 */
object CostTracker {

    /** Snapshot of token counts + computed cost. */
    data class Stats(
        val inputTokens: Long = 0L,
        val outputTokens: Long = 0L,
        val estCostUsd: Double = 0.0,
        val sessions: Int = 0,
    )

    // Pricing (USD per 1M tokens) for claude-sonnet-4-5.
    private const val INPUT_USD_PER_MTOK = 3.0
    private const val OUTPUT_USD_PER_MTOK = 15.0

    private const val PREFS_NAME = "caseforge_cost"
    private const val KEY_LIFETIME_INPUT = "lifetime_input_tokens"
    private const val KEY_LIFETIME_OUTPUT = "lifetime_output_tokens"
    private const val KEY_LIFETIME_SESSIONS = "lifetime_sessions"

    // Lifetime stats (input+output rolled across all sessions ever recorded).
    private val _stats = MutableStateFlow(Stats())
    val stats: StateFlow<Stats> = _stats.asStateFlow()

    // In-memory only — zeroed at the start of each agent run.
    @Volatile
    var currentSession: Stats = Stats()
        private set

    /** Zero the current-session counters. Lifetime totals untouched. */
    @Synchronized
    fun beginSession() {
        currentSession = Stats()
    }

    /**
     * Add the tokens consumed by one Claude API round-trip to BOTH the current
     * session and the lifetime totals. Cost is recomputed from the running totals.
     */
    @Synchronized
    fun record(inputTokens: Int, outputTokens: Int) {
        if (inputTokens <= 0 && outputTokens <= 0) return

        val curIn = currentSession.inputTokens + inputTokens
        val curOut = currentSession.outputTokens + outputTokens
        currentSession = currentSession.copy(
            inputTokens = curIn,
            outputTokens = curOut,
            estCostUsd = cost(curIn, curOut),
        )

        val lifeIn = _stats.value.inputTokens + inputTokens
        val lifeOut = _stats.value.outputTokens + outputTokens
        _stats.value = _stats.value.copy(
            inputTokens = lifeIn,
            outputTokens = lifeOut,
            estCostUsd = cost(lifeIn, lifeOut),
        )
    }

    /**
     * Close out the current session: bump the lifetime session count and persist
     * the (already-folded) lifetime totals to SharedPreferences. Safe to call even
     * if no tokens were recorded.
     */
    @Synchronized
    fun endSession(context: Context) {
        val newSessions = _stats.value.sessions + 1
        _stats.value = _stats.value.copy(sessions = newSessions)

        val prefs = context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putLong(KEY_LIFETIME_INPUT, _stats.value.inputTokens)
            .putLong(KEY_LIFETIME_OUTPUT, _stats.value.outputTokens)
            .putInt(KEY_LIFETIME_SESSIONS, newSessions)
            .apply()
    }

    /** Restore lifetime totals from disk. Call once from App.onCreate(). */
    @Synchronized
    fun loadLifetime(context: Context) {
        val prefs = context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val inTok = prefs.getLong(KEY_LIFETIME_INPUT, 0L)
        val outTok = prefs.getLong(KEY_LIFETIME_OUTPUT, 0L)
        val sess = prefs.getInt(KEY_LIFETIME_SESSIONS, 0)
        _stats.value = Stats(
            inputTokens = inTok,
            outputTokens = outTok,
            estCostUsd = cost(inTok, outTok),
            sessions = sess,
        )
    }

    private fun cost(inputTokens: Long, outputTokens: Long): Double {
        val inCost = inputTokens.toDouble() / 1_000_000.0 * INPUT_USD_PER_MTOK
        val outCost = outputTokens.toDouble() / 1_000_000.0 * OUTPUT_USD_PER_MTOK
        return inCost + outCost
    }
}
