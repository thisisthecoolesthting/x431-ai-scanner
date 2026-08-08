package com.caseforge.scanner.transfer

/**
 * Reduces device-unique churn in [android.os.Build.FINGERPRINT] for fleet-side analytics.
 */
object BuildFingerprintSanitizer {

    private val LONG_HEX = Regex("[a-fA-F0-9]{12,}")
    private val LONG_DIGITS = Regex("\\b\\d{8,}\\b")

    fun sanitize(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        var s = raw
        s = LONG_HEX.replace(s, "…")
        s = LONG_DIGITS.replace(s, "…")
        if (s.length > 240) {
            s = s.take(240) + "…"
        }
        return s
    }
}
