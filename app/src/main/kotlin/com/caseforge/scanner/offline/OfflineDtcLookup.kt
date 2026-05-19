package com.caseforge.scanner.offline

import android.content.Context

/**
 * Local, asset-backed DTC dictionary. No network calls.
 */
class OfflineDtcLookup private constructor(
    private val bundle: OfflineBundle,
) {

    constructor(context: Context) : this(OfflineBundle.load(context.applicationContext))

    /** Exact code lookup after normalization (e.g. "p0300", "0300" → P0300). */
    fun lookup(code: String): OfflineDtc? = bundle.dtc(code)

    /**
     * Case-insensitive search across code, title, summary, tags, and cause/check text.
     * Empty query returns no results.
     */
    fun search(query: String): List<OfflineDtc> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()

        val qUpper = q.uppercase()
        val qLower = q.lowercase()

        return bundle.dtcs.filter { dtc ->
            val code = normalizeCode(dtc.code)
            code.contains(qUpper) ||
                dtc.title.contains(q, ignoreCase = true) ||
                dtc.summary.contains(q, ignoreCase = true) ||
                dtc.tags.any { it.contains(qLower) } ||
                dtc.likelyCauses.any { it.contains(q, ignoreCase = true) } ||
                dtc.firstChecks.any { it.contains(q, ignoreCase = true) }
        }.sortedWith(
            compareByDescending<OfflineDtc> { normalizeCode(it.code).startsWith(qUpper) }
                .thenBy { normalizeCode(it.code) },
        )
    }

    fun guidedTestsForCode(code: String): List<OfflineGuidedTest> =
        bundle.guidedTestsForCode(code)

    fun searchGuidedTests(query: String): List<OfflineGuidedTest> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        val qUpper = q.uppercase()
        return bundle.guidedTests.filter { test ->
            test.id.contains(q, ignoreCase = true) ||
                test.title.contains(q, ignoreCase = true) ||
                test.summary.contains(q, ignoreCase = true) ||
                test.relatedCodes.any { normalizeCode(it).contains(qUpper) } ||
                test.steps.any { it.contains(q, ignoreCase = true) }
        }
    }

    companion object {
        fun fromBundle(bundle: OfflineBundle): OfflineDtcLookup = OfflineDtcLookup(bundle)

        /** Normalize user/scan input to canonical OBD-II powertrain form (P + 4 digits). */
        fun normalizeCode(raw: String): String {
            val compact = raw.trim().uppercase().replace(Regex("[^A-Z0-9]"), "")
            if (compact.isEmpty()) return ""
            val digits = compact.removePrefix("P").filter { it.isDigit() }.take(4)
            if (digits.length == 4) return "P$digits"
            if (compact.startsWith("P") && compact.length >= 5) return compact.take(5)
            return compact
        }
    }
}
