package com.caseforge.scanner.ai

import kotlinx.coroutines.delay
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Minimal Anthropic Messages API client with vision + tool-use support.
 *
 * Docs: https://docs.claude.com/en/api/messages
 *
 * This is intentionally hand-rolled and dependency-light so it builds on the X431 tablet
 * without pulling in heavy SDKs. It supports streaming-off (single response) only — that's
 * what the agent loop needs.
 */
class ClaudeClient(
    private val apiKey: String,
    private val model: String = "claude-sonnet-4-5",
    private val baseUrl: String = "https://api.anthropic.com/v1",
) {
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
        explicitNulls = false
    }

    @OptIn(ExperimentalSerializationApi::class)
    suspend fun sendMessages(
        system: String?,
        messages: List<Message>,
        tools: List<Tool> = emptyList(),
        maxTokens: Int = 2048,
        temperature: Double = 0.2,
        toolChoice: String? = null,   // null = auto, "any" = must call a tool
    ): Response {
        val tc: JsonObject? = toolChoice?.let { buildJsonObject { put("type", it) } }
        val body = MessagesRequest(
            model = model,
            maxTokens = maxTokens,
            temperature = temperature,
            system = system,
            messages = messages,
            tools = tools.ifEmpty { null },
            toolChoice = tc,
        )
        val payload = json.encodeToString(body)
        val req = Request.Builder()
            .url("$baseUrl/messages")
            .header("x-api-key", apiKey)
            .header("anthropic-version", "2023-06-01")
            .header("content-type", "application/json")
            .post(payload.toRequestBody("application/json".toMediaType()))
            .build()

        // Up to 4 attempts on 429 / 529 / 503 with exponential backoff (1s, 2s, 4s, 8s).
        var lastBody = ""
        var lastCode = 0
        repeat(4) { attempt ->
            http.newCall(req).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (resp.isSuccessful) {
                    return json.decodeFromString(Response.serializer(), text)
                }
                lastCode = resp.code
                lastBody = text
                val retryable = resp.code == 429 || resp.code == 529 || resp.code == 503
                if (!retryable) throw ClaudeApiException(resp.code, text)
            }
            // sleep before retrying
            val waitMs = 1000L * (1L shl attempt)   // 1s, 2s, 4s, 8s
            delay(waitMs)
        }
        throw ClaudeApiException(lastCode, lastBody)
    }

    /** -------- Request DTOs -------- */
    @Serializable
    data class MessagesRequest(
        val model: String,
        @SerialName("max_tokens") val maxTokens: Int,
        val temperature: Double,
        val system: String? = null,
        val messages: List<Message>,
        val tools: List<Tool>? = null,
        @SerialName("tool_choice") val toolChoice: JsonObject? = null,
    )

    @Serializable
    data class Message(
        val role: String, // "user" | "assistant"
        val content: List<ContentBlock>,
    )

    @Serializable(with = ContentBlockSerializer::class)
    sealed class ContentBlock {
        @Serializable
        data class Text(val type: String = "text", val text: String) : ContentBlock()

        @Serializable
        data class Image(
            val type: String = "image",
            val source: ImageSource,
        ) : ContentBlock()

        @Serializable
        data class ImageSource(
            val type: String = "base64",
            @SerialName("media_type") val mediaType: String,
            val data: String,
        )

        @Serializable
        data class ToolUse(
            val type: String = "tool_use",
            val id: String,
            val name: String,
            val input: JsonObject,
        ) : ContentBlock()

        @Serializable
        data class ToolResult(
            val type: String = "tool_result",
            @SerialName("tool_use_id") val toolUseId: String,
            val content: List<ContentBlock>, // typically a single Text or Image
            @SerialName("is_error") val isError: Boolean? = null,
        ) : ContentBlock()
    }

    @Serializable
    data class Tool(
        val name: String,
        val description: String,
        @SerialName("input_schema") val inputSchema: JsonObject,
    )

    /** -------- Response DTOs -------- */
    @Serializable
    data class Response(
        val id: String,
        val role: String,
        val model: String,
        val content: List<ContentBlock>,
        @SerialName("stop_reason") val stopReason: String? = null,
        val usage: Usage? = null,
    ) {
        fun firstText(): String? =
            content.filterIsInstance<ContentBlock.Text>().joinToString("\n") { it.text }
                .ifBlank { null }

        fun toolUses(): List<ContentBlock.ToolUse> =
            content.filterIsInstance<ContentBlock.ToolUse>()
    }

    @Serializable
    data class Usage(
        @SerialName("input_tokens") val inputTokens: Int = 0,
        @SerialName("output_tokens") val outputTokens: Int = 0,
    )

    class ClaudeApiException(val httpCode: Int, val body: String) :
        RuntimeException("Claude API error $httpCode: $body")

    /** Helpers */
    companion object {
        fun userText(text: String) = Message("user", listOf(ContentBlock.Text(text = text)))
        fun userImage(jpegBase64: String, mediaType: String = "image/jpeg") =
            Message(
                "user",
                listOf(ContentBlock.Image(source = ContentBlock.ImageSource(mediaType = mediaType, data = jpegBase64)))
            )

        fun obj(builder: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit): JsonObject =
            buildJsonObject(builder)
    }
}

/** Polymorphic serializer for ContentBlock that emits/reads the discriminator field "type".
 *  IMPORTANT: uses a dedicated Json instance with encodeDefaults=true so the "type" field
 *  (which is a default-valued property on every subtype) is actually emitted on the wire. */
internal object ContentBlockSerializer :
    kotlinx.serialization.KSerializer<ClaudeClient.ContentBlock> {
    private val inner = kotlinx.serialization.json.Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }
    private val delegate = kotlinx.serialization.json.JsonElement.serializer()
    override val descriptor = delegate.descriptor

    override fun serialize(
        encoder: kotlinx.serialization.encoding.Encoder,
        value: ClaudeClient.ContentBlock,
    ) {
        val jsonEncoder = encoder as? kotlinx.serialization.json.JsonEncoder
            ?: error("ContentBlock requires JSON")
        val element: JsonElement = when (value) {
            is ClaudeClient.ContentBlock.Text -> inner.encodeToJsonElement(
                ClaudeClient.ContentBlock.Text.serializer(), value
            )
            is ClaudeClient.ContentBlock.Image -> inner.encodeToJsonElement(
                ClaudeClient.ContentBlock.Image.serializer(), value
            )
            is ClaudeClient.ContentBlock.ToolUse -> inner.encodeToJsonElement(
                ClaudeClient.ContentBlock.ToolUse.serializer(), value
            )
            is ClaudeClient.ContentBlock.ToolResult -> inner.encodeToJsonElement(
                ClaudeClient.ContentBlock.ToolResult.serializer(), value
            )
        }
        jsonEncoder.encodeJsonElement(element)
    }

    override fun deserialize(decoder: kotlinx.serialization.encoding.Decoder): ClaudeClient.ContentBlock {
        val jsonDecoder = decoder as? kotlinx.serialization.json.JsonDecoder
            ?: error("ContentBlock requires JSON")
        val el = jsonDecoder.decodeJsonElement() as JsonObject
        return when ((el["type"] as? JsonPrimitive)?.content) {
            "text" -> inner.decodeFromJsonElement(
                ClaudeClient.ContentBlock.Text.serializer(), el
            )
            "image" -> inner.decodeFromJsonElement(
                ClaudeClient.ContentBlock.Image.serializer(), el
            )
            "tool_use" -> inner.decodeFromJsonElement(
                ClaudeClient.ContentBlock.ToolUse.serializer(), el
            )
            "tool_result" -> inner.decodeFromJsonElement(
                ClaudeClient.ContentBlock.ToolResult.serializer(), el
            )
            else -> {
                // Anthropic may add new block types (thinking, web_search, etc.). Don't crash —
                // fall back to a Text block carrying the raw JSON so the agent loop survives.
                ClaudeClient.ContentBlock.Text(text = el.toString().take(2000))
            }
        }
    }
}
