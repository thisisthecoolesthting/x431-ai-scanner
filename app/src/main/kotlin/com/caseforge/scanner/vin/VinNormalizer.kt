package com.caseforge.scanner.vin

/**
 * ISO 3779 VIN normalization and validation for camera / OCR pipelines.
 * No ML here — callers pass raw text from ML Kit or manual entry.
 */
object VinNormalizer {

    const val VIN_LENGTH = 17

    private val INVALID_CHARS = setOf('I', 'O', 'Q')
    private val VIN_CHARSET = Regex("^[A-HJ-NPR-Z0-9]{17}$")

    private val WEIGHTS = intArrayOf(8, 7, 6, 5, 4, 3, 2, 10, 0, 9, 8, 7, 6, 5, 4, 3, 2)

    private val TRANSLITERATION: Map<Char, Int> = mapOf(
        'A' to 1, 'B' to 2, 'C' to 3, 'D' to 4, 'E' to 5, 'F' to 6, 'G' to 7, 'H' to 8,
        'J' to 1, 'K' to 2, 'L' to 3, 'M' to 4, 'N' to 5, 'P' to 7, 'R' to 9,
        'S' to 2, 'T' to 3, 'U' to 4, 'V' to 5, 'W' to 6, 'X' to 7, 'Y' to 8, 'Z' to 9,
    )

    /** Strip noise and uppercase; does not enforce length. */
    fun normalizeOcrText(raw: String): String {
        if (raw.isBlank()) return ""
        val sb = StringBuilder()
        for (ch in raw.uppercase()) {
            if (ch.isLetterOrDigit()) sb.append(ch)
        }
        return sb.toString()
    }

    fun hasValidCharset(vin: String): Boolean =
        vin.length == VIN_LENGTH && VIN_CHARSET.matches(vin)

    fun containsInvalidLetters(vin: String): Boolean =
        vin.any { it in INVALID_CHARS }

    /**
     * Returns true when [vin] is 17 chars, uses allowed charset, and position-9 check digit matches.
     */
    fun isValidVin(vin: String): Boolean {
        if (!hasValidCharset(vin)) return false
        return validateCheckDigit(vin)
    }

    /** ISO 3779 check digit (position 9, index 8). */
    fun validateCheckDigit(vin: String): Boolean {
        if (vin.length != VIN_LENGTH || containsInvalidLetters(vin)) return false
        return vin[8] == computeCheckDigit(vin)
    }

    fun computeCheckDigit(vin: String): Char {
        require(vin.length == VIN_LENGTH) { "VIN must be 17 characters" }
        var sum = 0
        for (i in vin.indices) {
            if (i == 8) continue
            val value = transliterate(vin[i]) ?: error("Invalid VIN character: ${vin[i]}")
            sum += value * WEIGHTS[i]
        }
        val remainder = sum % 11
        return if (remainder == 10) 'X' else ('0' + remainder)
    }

    /** Best-effort normalize to a charset-valid 17-char VIN, or null. */
    fun normalizeToVin(raw: String): String? =
        pickBestFromOcr(raw)?.normalizedVin?.takeIf { hasValidCharset(it) }

    /**
     * Slide a 17-char window over cleaned OCR text and emit scored candidates.
     */
    fun extractCandidates(raw: String): List<VinOcrCandidate> {
        val cleaned = normalizeOcrText(raw)
        if (cleaned.isEmpty()) return emptyList()

        val seen = LinkedHashSet<String>()
        val out = ArrayList<VinOcrCandidate>()

        if (cleaned.length <= VIN_LENGTH) {
            tryNormalizeWindow(cleaned)?.let { addCandidate(out, seen, it) }
            return out.sortedByDescending { it.score }
        }

        for (start in 0..cleaned.length - VIN_LENGTH) {
            val fragment = cleaned.substring(start, start + VIN_LENGTH)
            tryNormalizeWindow(fragment)?.let { addCandidate(out, seen, it) }
        }
        return out.sortedByDescending { it.score }
    }

    fun pickBest(candidates: List<VinOcrCandidate>): VinOcrCandidate? =
        candidates.maxByOrNull { it.score }

    fun pickBestFromOcr(raw: String): VinOcrCandidate? =
        pickBest(extractCandidates(raw))

    private fun addCandidate(
        out: MutableList<VinOcrCandidate>,
        seen: MutableSet<String>,
        candidate: VinOcrCandidate,
    ) {
        if (seen.add(candidate.normalizedVin)) {
            out.add(candidate)
        }
    }

    private fun tryNormalizeWindow(fragment: String): VinOcrCandidate? {
        val cleaned = normalizeOcrText(fragment)
        if (cleaned.isEmpty()) return null
        val window = when {
            cleaned.length == VIN_LENGTH -> cleaned
            cleaned.length > VIN_LENGTH -> cleaned.substring(0, VIN_LENGTH)
            else -> return null
        }
        val (normalized, corrections) = applyOcrCorrections(window)
        if (normalized.length != VIN_LENGTH) return null
        return VinOcrCandidate(
            sourceFragment = fragment,
            normalizedVin = normalized,
            checkDigitValid = validateCheckDigit(normalized),
            ocrCorrections = corrections,
        )
    }

    /** Map common OCR confusions; VIN forbids I/O/Q. */
    private fun applyOcrCorrections(fragment: String): Pair<String, Int> {
        var corrections = 0
        val chars = fragment.uppercase().toCharArray()
        for (i in chars.indices) {
            val mapped = mapOcrChar(chars[i])
            if (mapped != chars[i]) {
                chars[i] = mapped
                corrections++
            }
        }
        return String(chars) to corrections
    }

    private fun mapOcrChar(ch: Char): Char = when (ch) {
        'I', 'L', '|' -> '1'
        'O', 'Q' -> '0'
        else -> ch
    }

    private fun transliterate(ch: Char): Int? = when {
        ch in '0'..'9' -> ch - '0'
        else -> TRANSLITERATION[ch]
    }
}
