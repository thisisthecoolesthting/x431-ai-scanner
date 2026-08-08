package com.caseforge.scanner.agent.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionTokenAccountingTest {

    @Test
    fun estimateCostUsd_usesSonnetListRates() {
        // 100k in @ $3/MTok + 20k out @ $15/MTok = 0.30 + 0.30 = 0.60
        val cost = SessionTokenAccounting.estimateCostUsd(100_000, 20_000)
        assertEquals(0.60, cost, 0.001)
    }

    @Test
    fun tokensFromChars_usesQuarterCharHeuristic() {
        assertEquals(0, SessionTokenAccounting.tokensFromChars(0))
        assertEquals(1, SessionTokenAccounting.tokensFromChars(1))
        assertEquals(1, SessionTokenAccounting.tokensFromChars(4))
        assertEquals(2, SessionTokenAccounting.tokensFromChars(5))
    }

    @Test
    fun computeEstimatedSessionCostUsd_formatsLabel() {
        val line = SessionTokenAccounting.computeEstimatedSessionCostUsd(0.42, "last session")
        assertEquals("~$0.42 (last session)", line)
    }

    @Test
    fun recordApiCall_prefersUsageOverEstimate() {
        SessionTokenAccounting.beginSession("test")
        SessionTokenAccounting.recordApiCall(
            response = null,
            system = "sys",
            messages = listOf(
                com.caseforge.scanner.ai.ClaudeClient.userText("hello world"),
            ),
            fallbackOutputText = "reply",
            visionImages = 2,
            isChatTurn = true,
        )
        val cur = SessionTokenAccounting.currentSession
        assertTrue(cur.inputTokens > 0)
        assertTrue(cur.outputTokens > 0)
        assertEquals(1, cur.chatTurns)
    }
}
