package com.caseforge.scanner.ai

/**
 * All value types for AI Diagnostic Mode. Kept in one small file so every other
 * AI-mode file compiles against a single source of truth (one-shot build safety).
 */

/** The phases of the guided flow. */
enum class AiDiagPhase {
    SYMPTOMS,
    PHOTO_ENGINE_BAY,
    PHOTO_DOOR_JAMB,
    PHOTO_DASH,
    CONNECT,
    RUNNING,
    RESULT,
    ERROR,
}

enum class AiPhotoKind { ENGINE_BAY, DOOR_JAMB, DASH }

/** VIN photo result (from the existing VinCameraScanActivity). */
data class VinPhotoCapture(
    val vin: String?,
    val rawOcr: String?,
    val candidateCount: Int,
)

/** Structured data Claude vision pulls out of the photos before the diagnostic loop. */
data class AiExtractedVehicleContext(
    val vin: String? = null,
    val mileage: Int? = null,
    val warningLights: List<String> = emptyList(),
    val visibleEngineFindings: List<String> = emptyList(),
    val photoNotes: List<String> = emptyList(),
)

/** Everything gathered before the AI starts reasoning. */
data class AiDiagnosticIntake(
    val symptoms: String? = null,
    val complaintChips: List<String> = emptyList(),
    val engineBayPhotoBase64: String? = null,
    val vinPhoto: VinPhotoCapture? = null,
    val dashPhotoBase64: String? = null,
    val extracted: AiExtractedVehicleContext = AiExtractedVehicleContext(),
)

/** One entry in the live "thought feed". */
enum class AiThoughtKind { THINKING, TOOL, FINDING, QUESTION, ANSWER, RESULT, ERROR }

data class AiThoughtEvent(
    val at: Long,
    val kind: AiThoughtKind,
    val title: String,
    val detail: String? = null,
)

/** A live gauge reading bound to the UI. */
data class GaugeReading(
    val pid: String,
    val label: String,
    val value: String,
    val unit: String,
    val fillFraction: Float,
)

/** A question the AI is waiting on the tech to answer. */
data class PendingUserQuestion(
    val id: String,
    val question: String,
    val answerChips: List<String> = emptyList(),
    val allowFreeText: Boolean = true,
)

/** A trouble code in the final result. */
data class AiDtc(
    val code: String,
    val module: String? = null,
    val description: String? = null,
    val status: String? = null,
)

/** The final diagnosis. */
data class AiDiagnosticResult(
    val vin: String? = null,
    val startedAt: Long,
    val endedAt: Long,
    val symptoms: String? = null,
    val rootCause: String,
    val confidence: Float,
    val recommendedRepair: String,
    val summary: String? = null,
    val supportingEvidence: List<String> = emptyList(),
    val codes: List<AiDtc> = emptyList(),
    val mileage: Int? = null,
    val warningLights: List<String> = emptyList(),
    val photoRefs: List<AiPhotoKind> = emptyList(),
)

/** Top-level UI state for the whole flow. */
data class AiDiagUiState(
    val phase: AiDiagPhase = AiDiagPhase.SYMPTOMS,
    val intake: AiDiagnosticIntake = AiDiagnosticIntake(),
    val connectProgress: String? = null,
    val result: AiDiagnosticResult? = null,
    val error: String? = null,
)
