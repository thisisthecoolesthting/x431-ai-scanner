package com.caseforge.scanner.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Wake-word listener for "Hey Together" plus optional push-to-talk capture.
 */
class VoiceMode(
    private val context: Context,
    private val onUtterance: (String) -> Unit,
    private val onStateChange: (State) -> Unit = {},
) {
    enum class State { IDLE, WAITING, CAPTURING, ERROR }

    private val _state = MutableStateFlow(State.IDLE)
    val state: StateFlow<State> = _state.asStateFlow()

    private var recognizer: SpeechRecognizer? = null
    private var captureMode = false

    companion object {
        private const val TAG = "VoiceMode"
        private val WAKE_WORDS = listOf("hey together", "hey to gather", "hey to get her", "hey 2gether")
        private const val RESTART_DELAY_MS = 400L
    }

    fun start() {
        if (_state.value != State.IDLE) return
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            Log.e(TAG, "SpeechRecognizer not available on this device")
            transition(State.ERROR)
            return
        }
        captureMode = false
        buildRecognizer()
        startListening()
        transition(State.WAITING)
    }

    fun stop() {
        recognizer?.destroy()
        recognizer = null
        captureMode = false
        transition(State.IDLE)
    }

    /** One-shot listen (FAB long-press); skips wake-word detection. */
    fun startPushToTalk() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            transition(State.ERROR)
            return
        }
        captureMode = true
        buildRecognizer()
        startListening()
        transition(State.CAPTURING)
    }

    fun cancelPushToTalk() {
        if (_state.value == State.CAPTURING && captureMode) {
            recognizer?.cancel()
            captureMode = false
            if (_state.value != State.IDLE) transition(State.WAITING)
        }
    }

    private fun buildRecognizer() {
        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(listener)
        }
    }

    private fun startListening() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 500L)
        }
        recognizer?.startListening(intent)
    }

    private fun transition(newState: State) {
        _state.value = newState
        onStateChange(newState)
    }

    private fun containsWakeWord(text: String): Boolean {
        val lower = text.lowercase().trim()
        return WAKE_WORDS.any { lower.contains(it) }
    }

    private fun stripWakeWord(text: String): String {
        val lower = text.lowercase()
        for (wake in WAKE_WORDS) {
            val idx = lower.indexOf(wake)
            if (idx >= 0) return text.substring(idx + wake.length).trim()
        }
        return text.trim()
    }

    private fun restartAfterDelay() {
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            if (_state.value != State.IDLE) {
                buildRecognizer()
                startListening()
            }
        }, RESTART_DELAY_MS)
    }

    private val listener = object : RecognitionListener {
        override fun onPartialResults(partialResults: Bundle?) {
            val partials = partialResults
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION) ?: return
            val best = partials.firstOrNull() ?: return
            if (!captureMode && containsWakeWord(best)) {
                captureMode = true
                transition(State.CAPTURING)
            }
        }

        override fun onResults(results: Bundle?) {
            val hypotheses = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?: run { restartAfterDelay(); return }
            val best = hypotheses.firstOrNull() ?: run { restartAfterDelay(); return }

            when {
                captureMode -> {
                    val command = stripWakeWord(best)
                    val wasPushToTalk = _state.value == State.CAPTURING
                    captureMode = false
                    transition(if (wasPushToTalk && _state.value != State.IDLE) State.WAITING else State.WAITING)
                    if (command.isNotBlank()) onUtterance(command)
                    if (_state.value != State.IDLE) restartAfterDelay()
                }
                containsWakeWord(best) -> {
                    val inline = stripWakeWord(best)
                    if (inline.isNotBlank()) {
                        transition(State.WAITING)
                        onUtterance(inline)
                        restartAfterDelay()
                    } else {
                        captureMode = true
                        transition(State.CAPTURING)
                        buildRecognizer()
                        startListening()
                    }
                }
                else -> restartAfterDelay()
            }
        }

        override fun onError(error: Int) {
            if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) recognizer?.cancel()
            captureMode = false
            if (_state.value != State.IDLE) {
                transition(State.WAITING)
                restartAfterDelay()
            }
        }

        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onEvent(eventType: Int, params: Bundle?) {}
    }
}
