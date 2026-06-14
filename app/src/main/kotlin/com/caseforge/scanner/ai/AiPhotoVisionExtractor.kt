package com.caseforge.scanner.ai

import org.json.JSONObject

/**
 * One Claude vision call that turns the captured photos into structured vehicle context
 * (mileage, warning lights, engine-bay findings). Runs once before the diagnostic loop.
 */
object AiPhotoVisionExtractor {

    suspend fun extract(claude: ClaudeClient, intake: AiDiagnosticIntake): AiExtractedVehicleContext {
        val images = buildList {
            intake.dashPhotoBase64?.let { add("dash" to it) }
            intake.engineBayPhotoBase64?.let { add("engine bay" to it) }
        }
        // VIN already comes from OCR; if no photos to analyze, just carry the VIN through.
        if (images.isEmpty()) {
            return AiExtractedVehicleContext(vin = intake.vinPhoto?.vin)
        }

        val content = mutableListOf<ClaudeClient.ContentBlock>()
        images.forEach { (label, b64) ->
            content.add(ClaudeClient.ContentBlock.Text(text = "Photo: $label"))
            content.add(
                ClaudeClient.ContentBlock.Image(
                    source = ClaudeClient.ContentBlock.ImageSource(mediaType = "image/jpeg", data = b64),
                ),
            )
        }
        content.add(ClaudeClient.ContentBlock.Text(text = AiDiagnosticPrompts.VISION_EXTRACT))

        val resp = try {
            claude.sendMessages(
                system = null,
                messages = listOf(ClaudeClient.Message("user", content)),
                maxTokens = 600,
                temperature = 0.0,
            )
        } catch (t: Throwable) {
            return AiExtractedVehicleContext(vin = intake.vinPhoto?.vin)
        }

        val raw = resp.firstText().orEmpty()
        return parse(raw, intake.vinPhoto?.vin)
    }

    private fun parse(raw: String, vin: String?): AiExtractedVehicleContext {
        val jsonText = raw.substringAfter('{', "").let { if (it.isEmpty()) "" else "{$it" }
            .substringBeforeLast('}', "").let { if (it.isEmpty()) "" else "$it}" }
        if (jsonText.isBlank()) return AiExtractedVehicleContext(vin = vin)
        return try {
            val o = JSONObject(jsonText)
            AiExtractedVehicleContext(
                vin = vin,
                mileage = if (o.has("mileage")) o.optInt("mileage").takeIf { it > 0 } else null,
                warningLights = strList(o, "warningLights"),
                visibleEngineFindings = strList(o, "visibleEngineFindings"),
                photoNotes = strList(o, "notes"),
            )
        } catch (_: Throwable) {
            AiExtractedVehicleContext(vin = vin, photoNotes = listOf(raw.take(200)))
        }
    }

    private fun strList(o: JSONObject, key: String): List<String> {
        val arr = o.optJSONArray(key) ?: return emptyList()
        return (0 until arr.length()).mapNotNull { arr.optString(it).takeIf { s -> s.isNotBlank() } }
    }
}
