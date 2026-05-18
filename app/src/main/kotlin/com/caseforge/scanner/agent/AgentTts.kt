package com.caseforge.scanner.agent

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * Speaks the agent's activity ticker out loud. Hands-free under-the-dash mode.
 * Lazy init so we don't spin up TTS unless it's actually enabled.
 *
 * Coalesces rapid updates — only speaks the latest activity when the engine is idle.
 */
class AgentTts(context: Context) {
    private var tts: TextToSpeech? = null
    private var ready = false
    @Volatile private var pending: String? = null

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.US
                tts?.setSpeechRate(1.1f)
                ready = true
                pending?.let { speak(it); pending = null }
            }
        }
    }

    fun speak(text: String) {
        if (text.isBlank()) return
        if (!ready) { pending = text; return }
        // Drop in-flight speech if a newer status arrived.
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "agent-status")
    }

    fun shutdown() {
        runCatching { tts?.stop(); tts?.shutdown() }
        tts = null
        ready = false
    }
}
