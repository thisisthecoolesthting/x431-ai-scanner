package com.caseforge.scanner.ai

import android.content.Context
import com.caseforge.scanner.agent.AgentTts
import com.caseforge.scanner.data.AppDatabase
import com.caseforge.scanner.ui.main.StandaloneVciController
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Orchestrates the whole AI Diagnostic Mode flow: holds UI state, runs the connect step once,
 * runs the Claude diagnostic loop, manages the ask_user suspend/resume, and saves the result.
 *
 * Not an AndroidViewModel — it takes a CoroutineScope from the host composable to keep wiring
 * trivial for the one-shot build. The host must call [dispose] on leave.
 */
class AiDiagnosticController(
    private val context: Context,
    private val scope: CoroutineScope,
    private val vci: StandaloneVciController,
    private val claude: ClaudeClient,
    private val db: AppDatabase,
    private val tts: AgentTts?,
) {
    private val _ui = MutableStateFlow(AiDiagUiState())
    val ui: StateFlow<AiDiagUiState> = _ui.asStateFlow()

    private val _thoughtFeed = MutableStateFlow<List<AiThoughtEvent>>(emptyList())
    val thoughtFeed: StateFlow<List<AiThoughtEvent>> = _thoughtFeed.asStateFlow()

    private val _gauges = MutableStateFlow<List<GaugeReading>>(emptyList())
    val gauges: StateFlow<List<GaugeReading>> = _gauges.asStateFlow()

    private val _pendingQuestion = MutableStateFlow<PendingUserQuestion?>(null)
    val pendingQuestion: StateFlow<PendingUserQuestion?> = _pendingQuestion.asStateFlow()

    private var answerDeferred: CompletableDeferred<String>? = null
    private var runJob: Job? = null

    private fun setPhase(p: AiDiagPhase) { _ui.value = _ui.value.copy(phase = p) }
    private fun addThought(e: AiThoughtEvent) { _thoughtFeed.value = _thoughtFeed.value + e }

    // --- intake mutations ---
    fun setSymptoms(text: String, chips: List<String>) {
        _ui.value = _ui.value.copy(intake = _ui.value.intake.copy(symptoms = text.ifBlank { null }, complaintChips = chips))
    }

    fun onSymptomsNext() = setPhase(AiDiagPhase.PHOTO_ENGINE_BAY)
    fun onSymptomsSkip() {
        _ui.value = _ui.value.copy(intake = _ui.value.intake.copy(symptoms = null, complaintChips = emptyList()))
        setPhase(AiDiagPhase.PHOTO_ENGINE_BAY)
    }

    fun onEngineBayPhoto(base64: String?) {
        _ui.value = _ui.value.copy(intake = _ui.value.intake.copy(engineBayPhotoBase64 = base64))
        setPhase(AiDiagPhase.PHOTO_DOOR_JAMB)
    }

    fun onVinPhoto(vin: VinPhotoCapture?) {
        _ui.value = _ui.value.copy(intake = _ui.value.intake.copy(vinPhoto = vin))
        setPhase(AiDiagPhase.PHOTO_DASH)
    }

    fun onDashPhoto(base64: String?) {
        _ui.value = _ui.value.copy(intake = _ui.value.intake.copy(dashPhotoBase64 = base64))
        setPhase(AiDiagPhase.CONNECT)
    }

    fun skipTo(next: AiDiagPhase) = setPhase(next)

    // --- connect (one attempt) ---
    fun startConnect() {
        _ui.value = _ui.value.copy(connectProgress = "Connecting…", error = null)
        scope.launch {
            val r = vci.connect(scope)
            if (r.isSuccess && vci.isConnected) {
                _ui.value = _ui.value.copy(connectProgress = "Connected (${vci.linkKind()?.name ?: "link"})")
                startDiagnosis()
            } else {
                _ui.value = _ui.value.copy(
                    connectProgress = null,
                    error = vci.lastConnectError() ?: "Could not connect. Check the cable/adapter and ignition.",
                )
                setPhase(AiDiagPhase.ERROR)
            }
        }
    }

    // --- the AI loop ---
    private fun startDiagnosis() {
        val port = vci.diagnosticPort()
        if (port == null) {
            _ui.value = _ui.value.copy(error = "Connected but no diagnostic port available.")
            setPhase(AiDiagPhase.ERROR)
            return
        }
        setPhase(AiDiagPhase.RUNNING)
        runJob = scope.launch {
            // 1. vision extraction (mileage, lights, engine-bay notes)
            val extracted = AiPhotoVisionExtractor.extract(claude, _ui.value.intake)
            _ui.value = _ui.value.copy(intake = _ui.value.intake.copy(extracted = extracted))
            if (extracted.mileage != null || extracted.warningLights.isNotEmpty()) {
                addThought(
                    AiThoughtEvent(
                        System.currentTimeMillis(), AiThoughtKind.FINDING, "Read the dash",
                        buildString {
                            extracted.mileage?.let { append("Mileage $it. ") }
                            if (extracted.warningLights.isNotEmpty()) append("Lights: ${extracted.warningLights.joinToString(", ")}")
                        },
                    ),
                )
            }

            // 2. run the diagnostic loop
            val runner = AiDiagnosticAgentRunner(
                claude = claude,
                port = port,
                intake = _ui.value.intake,
                callbacks = object : AiDiagnosticAgentRunner.Callbacks {
                    override fun onThought(event: AiThoughtEvent) = addThought(event)
                    override fun onGauges(readings: List<GaugeReading>) { _gauges.value = readings }
                    override suspend fun onAskUser(question: String, chips: List<String>, speak: Boolean): String {
                        if (speak) runCatching { tts?.speak(question) }
                        val def = CompletableDeferred<String>()
                        answerDeferred = def
                        _pendingQuestion.value = PendingUserQuestion(
                            id = UUID.randomUUID().toString(),
                            question = question,
                            answerChips = chips,
                            allowFreeText = true,
                        )
                        val answer = def.await()
                        _pendingQuestion.value = null
                        answerDeferred = null
                        return answer
                    }
                },
            )
            val result = runCatching { runner.run() }.getOrNull()
            if (result != null) {
                AiDiagnosticPersistence.save(db, result, _thoughtFeed.value)
                _ui.value = _ui.value.copy(result = result)
                setPhase(AiDiagPhase.RESULT)
            } else {
                _ui.value = _ui.value.copy(error = _ui.value.error ?: "The AI could not complete the diagnosis.")
                setPhase(AiDiagPhase.ERROR)
            }
        }
    }

    /** Called by the UI when the tech answers a question. */
    fun submitAnswer(answer: String) {
        answerDeferred?.complete(answer)
    }

    fun retry() {
        _ui.value = _ui.value.copy(error = null)
        setPhase(AiDiagPhase.CONNECT)
    }

    fun dispose() {
        runJob?.cancel()
        answerDeferred?.cancel()
        runCatching { if (vci.isConnected) vci.disconnect() }
    }
}
