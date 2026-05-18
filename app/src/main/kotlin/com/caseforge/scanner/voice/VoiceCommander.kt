package com.caseforge.scanner.voice

import android.content.Context
import android.os.Build
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.caseforge.scanner.engine.CapabilityMap
import com.caseforge.scanner.engine.CapabilityMap.Capability
import com.caseforge.scanner.voice.VoiceIntentResolver.IntentClass
import com.caseforge.scanner.voice.VoiceIntentResolver.VoiceIntent
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale
import java.util.UUID

/**
 * Orchestrates wake-word capture, intent resolution, overlay dispatch, and TTS feedback.
 */
class VoiceCommander(
    private val context: Context,
    private val engineCallback: EngineCallback,
    private val catalogProvider: () -> List<Capability> = { CapabilityMap.ALL },
) {
    interface EngineCallback {
        fun onReadCodes()
        fun onClearCodes()
        fun onGraphPid(pidLabel: String)
        fun onRunCapability(capabilityId: String)
        fun onDismiss()
        fun onPeek()
        fun onHelp()
        fun onVoiceStateChanged(state: VoiceMode.State)
        fun onLastPhraseChanged(phrase: String)
    }

    companion object {
        private const val TAG = "VoiceCommander"
    }

    private var tts: TextToSpeech? = null
    private var ttsReady = false

    private val voiceMode = VoiceMode(
        context = context,
        onUtterance = ::handleUtterance,
        onStateChange = engineCallback::onVoiceStateChanged,
    )

    val voiceState: StateFlow<VoiceMode.State> get() = voiceMode.state

    fun start() {
        initTts()
        voiceMode.start()
        Log.i(TAG, "VoiceCommander started")
    }

    fun stop() {
        voiceMode.stop()
        tts?.stop()
        tts?.shutdown()
        tts = null
        ttsReady = false
        Log.i(TAG, "VoiceCommander stopped")
    }

    fun startPushToTalk() {
        voiceMode.startPushToTalk()
    }

    fun stopPushToTalk() {
        voiceMode.cancelPushToTalk()
    }

    private fun initTts() {
        tts = TextToSpeech(context) { status ->
            ttsReady = (status == TextToSpeech.SUCCESS)
            if (ttsReady) {
                tts?.language = Locale.US
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) {}
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        Log.w(TAG, "TTS error for utterance $utteranceId")
                    }
                })
            } else {
                Log.e(TAG, "TTS init failed with status $status")
            }
        }
    }

    private fun speak(text: String) {
        if (!ttsReady) return
        val utteranceId = UUID.randomUUID().toString()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        } else {
            @Suppress("DEPRECATION")
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null)
        }
    }

    private fun handleUtterance(utterance: String) {
        Log.i(TAG, "Utterance: $utterance")
        engineCallback.onLastPhraseChanged(utterance)
        dispatch(VoiceIntentResolver.resolve(utterance, catalogProvider()))
    }

    private fun dispatch(intent: VoiceIntent) {
        when (intent.cls) {
            IntentClass.ReadCodes -> {
                speak("Reading codes now")
                engineCallback.onReadCodes()
            }
            IntentClass.ClearCodes -> {
                speak("Clearing codes now")
                engineCallback.onClearCodes()
            }
            IntentClass.GraphPid -> {
                val pidLabel = intent.extra ?: "that PID"
                speak("Graphing $pidLabel")
                val capId = intent.capabilityId ?: "live_data"
                engineCallback.onRunCapability(capId)
                engineCallback.onGraphPid(pidLabel)
            }
            IntentClass.RunCapability -> {
                val capId = intent.capabilityId
                if (capId == null) {
                    speak("Sorry, I didn't catch which function you wanted")
                    return
                }
                val cap = CapabilityMap.byId(capId)
                val label = cap?.label ?: capId.replace('_', ' ')
                speak("Running $label now")
                engineCallback.onRunCapability(capId)
            }
            IntentClass.Dismiss -> {
                speak("Got it")
                engineCallback.onDismiss()
            }
            IntentClass.Peek -> {
                speak("Let me take a look")
                engineCallback.onPeek()
            }
            IntentClass.Help -> {
                speak(
                    "You can say: read codes, clear codes, run full scan, " +
                        "graph R P M, oil reset, or name any service function.",
                )
                engineCallback.onHelp()
            }
            IntentClass.Unknown -> {
                speak("I didn't understand that. Say help for a list of commands.")
            }
        }
    }
}
