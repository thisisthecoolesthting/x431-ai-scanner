package com.caseforge.scanner.ui.session

import android.graphics.BitmapFactory
import java.io.File

data class EngineBayTriageResult(
    val classification: String,
    val summary: String,
    val nextStep: String,
    val visionPromptHook: String,
)

object EngineBayPhotoTriage {
    private const val MIN_VALID_SIZE_BYTES = 48 * 1024L
    private const val LOW_LUX_LUMA = 55.0
    private const val DIM_LUMA = 80.0

    fun classify(photoPath: String?): EngineBayTriageResult {
        if (photoPath.isNullOrBlank()) {
            return EngineBayTriageResult(
                classification = "skipped",
                summary = "No engine-bay photo captured.",
                nextStep = "Continue wizard and collect a photo later if needed.",
                visionPromptHook = "",
            )
        }
        val file = File(photoPath)
        if (!file.isFile || file.length() <= 0L) {
            return EngineBayTriageResult(
                classification = "missing_file",
                summary = "Engine-bay photo file is missing.",
                nextStep = "Retake engine-bay photo before diagnosis.",
                visionPromptHook = "Retake required before visual triage.",
            )
        }
        if (file.length() < MIN_VALID_SIZE_BYTES) {
            return EngineBayTriageResult(
                classification = "likely_bad_capture",
                summary = "Photo looks too small to trust visual details.",
                nextStep = "Retake with wider framing and stable hold.",
                visionPromptHook = "Retake requested: current image too small/noisy.",
            )
        }

        val avgLuma = averageLuma(file.absolutePath) ?: return EngineBayTriageResult(
            classification = "decode_failed",
            summary = "Photo captured but image decode failed.",
            nextStep = "Retake and verify camera permission/storage health.",
            visionPromptHook = "Decode failed; no visual triage available.",
        )

        return when {
            avgLuma < LOW_LUX_LUMA -> EngineBayTriageResult(
                classification = "low_lux",
                summary = "Engine-bay image is very dark.",
                nextStep = "Increase bay lighting or tablet brightness and retake if detail is unclear.",
                visionPromptHook = "Low-light engine bay. Focus on leaks, disconnected plugs, missing covers.",
            )
            avgLuma < DIM_LUMA -> EngineBayTriageResult(
                classification = "dim_but_usable",
                summary = "Image is dim but likely usable.",
                nextStep = "Proceed, but retake if leak points or harness details are hard to read.",
                visionPromptHook = "Dim image. Prioritize fluid traces, broken mounts, exposed wiring.",
            )
            else -> EngineBayTriageResult(
                classification = "usable",
                summary = "Image quality appears usable for first-pass triage.",
                nextStep = "Proceed to VIN and dashboard captures.",
                visionPromptHook = "Look for fluid leaks, missing caps/covers, obvious damage or loose wiring.",
            )
        }
    }

    private fun averageLuma(path: String): Double? {
        val options = BitmapFactory.Options().apply { inSampleSize = 8 }
        val bmp = BitmapFactory.decodeFile(path, options) ?: return null
        val w = bmp.width
        val h = bmp.height
        if (w <= 0 || h <= 0) {
            bmp.recycle()
            return null
        }
        val pixels = IntArray(w * h)
        bmp.getPixels(pixels, 0, w, 0, 0, w, h)
        bmp.recycle()

        var total = 0.0
        var count = 0
        for (argb in pixels) {
            val r = (argb shr 16) and 0xFF
            val g = (argb shr 8) and 0xFF
            val b = argb and 0xFF
            total += (0.2126 * r + 0.7152 * g + 0.0722 * b)
            count++
        }
        return if (count == 0) null else total / count
    }
}
