package com.caseforge.scanner.agent

import android.content.Context
import com.caseforge.scanner.agent.session.SessionTokenAccounting
import com.caseforge.scanner.data.SettingsRepo
import kotlinx.coroutines.flow.StateFlow

/**
 * Legacy alias for overlay [AgentRunner] token accounting.
 * New session chat uses [SessionTokenAccounting] directly.
 */
object CostTracker {

    /** @see SessionTokenAccounting.Totals */
    data class Stats(
        val inputTokens: Long = 0L,
        val outputTokens: Long = 0L,
        val estCostUsd: Double = 0.0,
        val sessions: Int = 0,
    )

    val stats: StateFlow<SessionTokenAccounting.LifetimeStats>
        get() = SessionTokenAccounting.lifetime

    var currentSession: Stats
        get() {
            val t = SessionTokenAccounting.currentSession
            return Stats(
                inputTokens = t.inputTokens,
                outputTokens = t.outputTokens,
                estCostUsd = t.estCostUsd(),
            )
        }
        private set(_) {}

    fun beginSession() = SessionTokenAccounting.beginSession(sessionId = null)

    fun record(inputTokens: Int, outputTokens: Int) =
        SessionTokenAccounting.recordTokens(inputTokens, outputTokens)

    fun endSession(context: Context) {
        SessionTokenAccounting.endSession(
            context = context,
            settings = SettingsRepo(context.applicationContext),
            sessionId = null,
            vin = null,
        )
    }

    fun loadLifetime(context: Context) = SessionTokenAccounting.loadLifetime(context)
}
