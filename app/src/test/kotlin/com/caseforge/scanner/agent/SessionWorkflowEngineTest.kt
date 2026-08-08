package com.caseforge.scanner.agent

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.caseforge.scanner.data.SettingsRepo
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SessionWorkflowEngineTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun nextQuestion_blocksTier4ProgrammingInNeedDescription() = runBlocking {
        val engine = SessionWorkflowEngine(context, SettingsRepo(context))
        val reply = engine.nextQuestion(
            sessionId = "tier4-test",
            vin = null,
            photoPaths = emptyList(),
            discoveryReport = null,
            dtcSummary = null,
            priorVisits = null,
            needDescription = "need to program key fob",
            transcript = emptyList(),
        )
        assertTrue(reply.blockedTier4)
        assertTrue(reply.question.contains("Tier 4", ignoreCase = true) || reply.question.isNotBlank())
    }

    @Test
    fun nextQuestion_blocksTier4FlashInTranscript() = runBlocking {
        val engine = SessionWorkflowEngine(context, SettingsRepo(context))
        val reply = engine.nextQuestion(
            sessionId = "tier4-transcript",
            vin = "1HGBH41JXMN109186",
            photoPaths = emptyList(),
            discoveryReport = null,
            dtcSummary = null,
            priorVisits = null,
            needDescription = "no start",
            transcript = listOf(
                SessionWorkflowEngine.ChatTurn("tech", "customer wants flash pcm"),
            ),
        )
        assertTrue(reply.blockedTier4)
    }
}
