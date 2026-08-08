package com.caseforge.scanner.ui.session

import kotlinx.serialization.Serializable

/** Rich chat message types rendered in [SessionMessageContent]. */
enum class SessionMessageType {
    TEXT,
    VISUAL_CARD,
    PHOTO,
    CHART,
    DTC_TABLE,
}

@Serializable
data class VisualAttachment(
    val kind: String,
    val title: String = "",
    val subtitle: String = "",
    val bullets: List<String> = emptyList(),
    val imagePath: String? = null,
    val chartSeries: List<Float> = emptyList(),
    val chartLabel: String = "",
    val dtcRows: List<DtcRow> = emptyList(),
)

@Serializable
data class DtcRow(
    val code: String,
    val status: String,
)

data class SessionChatMessage(
    val role: String,
    val type: SessionMessageType = SessionMessageType.TEXT,
    val text: String = "",
    val visualAttachments: List<VisualAttachment> = emptyList(),
)

/** Rare bottom-sheet prompts — max 2–3 actions, never clutter the transcript. */
data class CriticalChoice(
    val id: String,
    val prompt: String,
    val options: List<CriticalOption>,
)

data class CriticalOption(
    val id: String,
    val label: String,
    val voiceKeywords: List<String> = emptyList(),
)
