package com.caseforge.scanner.ui.session

import android.util.Log
import com.caseforge.scanner.agent.AgentTts
import com.caseforge.scanner.voice.VoiceMode
import kotlinx.coroutines.flow.StateFlow

/**
 * Hands-free session chat voice loop — speak agent replies via [AgentTts], then capture tech
 * utterance via [VoiceMode.startPushToTalk] (no mic button in chat when enabled).
 *
 * Critical choices accept voice keywords ("confirm", "skip") via [onVoiceCriticalChoice].
 */
class SessionVoiceLoop(
    private val context: android.content.Context,
    private val tts: AgentTts,
    private val enabled: Boolean,
    private val onTechUtterance: (String) -> Unit,
    private val onVoiceCriticalChoice: ((CriticalOption) -> Unit)? = null,
) {
    companion object {
        private const val TAG = "SessionVoiceLoop"
    }

    private val voiceMode = VoiceMode(
        context = context,
        onUtterance = ::handleUtterance,
    )

    val voiceState: StateFlow<VoiceMode.State> get() = voiceMode.state

    private var pendingCritical: CriticalChoice? = null

    fun start() {
        if (!enabled) return
        voiceMode.start()
        Log.i(TAG, "Session voice loop started (hands-free after TTS)")
    }

    fun stop() {
        voiceMode.stop()
    }

    /** Speak assistant text, then open mic for tech reply. */
    fun speakThenListen(text: String) {
        if (!enabled || text.isBlank()) return
        tts.speak(text)
        // Short delay so TTS starts before mic opens
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            if (pendingCritical == null) {
                voiceMode.startPushToTalk()
            }
        }, 800L)
    }

    fun bindCriticalChoice(choice: CriticalChoice?) {
        pendingCritical = choice
    }

    private fun handleUtterance(raw: String) {
        val utterance = raw.trim().lowercase()
        if (utterance.isBlank()) return

        pendingCritical?.let { crit ->
            crit.options.firstOrNull { opt ->
                opt.voiceKeywords.any { utterance.contains(it.lowercase()) } ||
                    utterance.contains(opt.label.lowercase())
            }?.let { matched ->
                onVoiceCriticalChoice?.invoke(matched)
                pendingCritical = null
                return
            }
        }

        onTechUtterance(raw.trim())
    }
}
