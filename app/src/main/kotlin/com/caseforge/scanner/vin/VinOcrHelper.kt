package com.caseforge.scanner.vin

import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await

/** Extracts a 17-character VIN from camera frame text via ML Kit. */
object VinOcrHelper {
    private val vinRegex = Regex("""[A-HJ-NPR-Z0-9]{17}""")

    suspend fun extractVinFromImage(image: InputImage): String? {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        return try {
            val result = recognizer.process(image).await()
            result.text.uppercase().let { text ->
                vinRegex.find(text)?.value
            }
        } finally {
            recognizer.close()
        }
    }

    fun extractVinFromText(text: String): String? =
        vinRegex.find(text.uppercase())?.value
}
