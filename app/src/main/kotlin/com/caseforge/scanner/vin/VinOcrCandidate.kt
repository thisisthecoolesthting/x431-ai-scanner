package com.caseforge.scanner.vin

/**
 * A VIN hypothesis extracted from noisy OCR text. [score] is used by [VinNormalizer.pickBest];
 * higher is better.
 */
data class VinOcrCandidate(
    /** Raw substring before normalization (may include spaces or OCR junk). */
    val sourceFragment: String,
    /** Uppercase 17-character candidate after normalization. */
    val normalizedVin: String,
    val checkDigitValid: Boolean,
    /** Number of single-character OCR substitutions applied during normalization. */
    val ocrCorrections: Int = 0,
) {
    val charsetValid: Boolean = VinNormalizer.hasValidCharset(normalizedVin)

    val score: Int
        get() {
            var s = 0
            if (normalizedVin.length == VinNormalizer.VIN_LENGTH) s += 50
            if (charsetValid) s += 100
            if (checkDigitValid) s += 1000
            s -= ocrCorrections * 15
            return s
        }
}
