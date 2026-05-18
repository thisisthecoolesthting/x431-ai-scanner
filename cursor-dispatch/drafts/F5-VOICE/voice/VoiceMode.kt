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
 * VoiceMode — wake-word listener for "Hey Together".
 *
 * Lifecycle:
 *   start()  → idles in WAITING state, listening continuously for the wake word.
 *   On match → transitions to CAPTURING, fires onWakeWordDetected callback,
 *               captures the full utterance that follows, fires onUtterance.
 *   stop()   → tears down SpeechRecognizer.
 *
 * Uses on-device recognition only (EXTRA_PREFER_OFFLINE = true).
 * Partial results are enabled so wake-word detection is near-instant.
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
    private var captureMode = false   // false = scanning for wake word, true = capturing command

    companion object {
        private const val TAG = "VoiceMode"
        private val WAKE_WORDS = listOf("hey together", "hey to gather", "hey to get her", "hey 2gether")
        private const val RESTART_DELAY_MS = 400L
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

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

    // -------------------------------------------------------------------------
    // Internal
    // -------------------------------------------------------------------------

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
            // Keep mic open as long as possible
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

    /** Strip the wake-word prefix from a captured utterance if present. */
    private fun stripWakeWord(text: String): String {
        val lower = text.lowercase()
        for (wake in WAKE_WORDS) {
            val idx = lower.indexOf(wake)
            if (idx >= 0) {
                return text.substring(idx + wake.length).trim()
            }
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

    // -------------------------------------------------------------------------
    // RecognitionListener
    // -------------------------------------------------------------------------

    private val listener = object : RecognitionListener {

        override fun onPartialResults(partialResults: Bundle?) {
            val partials = partialResults
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?: return
            val best = partials.firstOrNull() ?: return
            Log.v(TAG, "partial[$captureMode]: $best")

            if (!captureMode && containsWakeWord(best)) {
                // Wake word heard mid-stream — switch to capture mode immediately
                captureMode = true
                transition(State.CAPTURING)
                Log.i(TAG, "Wake word detected in partial: $best")
                // The remainder of this recognition session will be the command
            }
        }

        override fun onResults(results: Bundle?) {
            val hypotheses = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?: run { restartAfterDelay(); return }
            val best = hypotheses.firstOrNull() ?: run { restartAfterDelay(); return }
            Log.d(TAG, "results[$captureMode]: $best")

            when {
                captureMode -> {
                    // This result IS the command (wake word may be prefixed — strip it)
                    val command = stripWakeWord(best)
                    captureMode = false
                    transition(State.WAITING)
                    if (command.isNotBlank()) {
                        Log.i(TAG, "Command captured: $command")
                        onUtterance(command)
                    }
                    restartAfterDelay()
                }
                containsWakeWord(best) -> {
                    // Wake word in final result — command follows in next session
                    val inline = stripWakeWord(best)
                    if (inline.isNotBlank()) {
                        // Inline command ("Hey Together read codes")
                        transition(State.WAITING)
                        Log.i(TAG, "Inline command: $inline")
                        onUtterance(inline)
                        restartAfterDelay()
                    } else {
                        // Pure wake word — begin capture session
                        captureMode = true
                        transition(State.CAPTURING)
                        buildRecognizer()
                        startListening()
                    }
                }
                else -> {
                    restartAfterDelay()
                }
            }
        }

        override fun onError(error: Int) {
            val msg = when (error) {
                SpeechRecognizer.ERROR_NO_MATCH -> "no_match"
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "speech_timeout"
                SpeechRecognizer.ERROR_AUDIO -> "audio_error"
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "recognizer_busy"
                SpeechRecognizer.ERROR_CLIENT -> "client_error"
                else -> "error_$error"
            }
            Log.w(TAG, "SpeechRecognizer error: $msg (captureMode=$captureMode)")
            if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) {
                recognizer?.cancel()
            }
            captureMode = false
            if (_state.value != State.IDLE) {
                transition(State.WAITING)
                restartAfterDelay()
            }
        }

        override fun onReadyForSpeech(params: Bundle?) { Log.v(TAG, "readyForSpeech") }
        override fun onBeginningOfSpeech() { Log.v(TAG, "beginningOfSpeech") }
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() { Log.v(TAG, "endOfSpeech") }
        override fun onEvent(eventType: Int, params: Bundle?) {}
    }
}
