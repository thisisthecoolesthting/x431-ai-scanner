package com.caseforge.scanner.diagnostics

import com.caseforge.scanner.vci.DiagnosticConnector

/**
 * Selects guided-test plans from free-text symptoms, DTC codes, and the active transport.
 */
object GuidedTestPlanner {

    private const val MIN_SCORE = 5

    /**
     * Returns ranked matches sorted by score (highest first). Empty when nothing meets [MIN_SCORE]
     * or transport filters out all candidates.
     */
    fun suggest(
        symptomQuery: String? = null,
        dtcCodes: List<String> = emptyList(),
        activeTransport: GuidedTransportRequirement? = null,
        limit: Int = 5,
    ): List<GuidedTestMatch> {
        val normalizedQuery = symptomQuery?.trim()?.lowercase().orEmpty()
        val normalizedCodes = dtcCodes.map { normalizeDtc(it) }.filter { it.isNotEmpty() }

        return GuidedTestCatalog.ALL
            .asSequence()
            .filter { supportsTransport(it, activeTransport) }
            .map { test -> score(test, normalizedQuery, normalizedCodes) }
            .filter { it.score >= MIN_SCORE }
            .sortedByDescending { it.score }
            .take(limit.coerceAtLeast(1))
            .toList()
    }

    fun planForId(id: String): GuidedTest? = GuidedTestCatalog.byId(id)

    fun bestMatch(
        symptomQuery: String? = null,
        dtcCodes: List<String> = emptyList(),
        activeTransport: GuidedTransportRequirement? = null,
    ): GuidedTestMatch? = suggest(symptomQuery, dtcCodes, activeTransport, limit = 1).firstOrNull()

    /** Maps an active vehicle link to the transport tier used for filtering. */
    fun transportFromLink(kind: DiagnosticConnector.LinkKind): GuidedTransportRequirement =
        when (kind) {
            DiagnosticConnector.LinkKind.ELM327_USB,
            DiagnosticConnector.LinkKind.ELM327_BT,
            -> GuidedTransportRequirement.ELM327

            DiagnosticConnector.LinkKind.OEM_USB,
            DiagnosticConnector.LinkKind.OEM_BT,
            -> GuidedTransportRequirement.OEM
        }

    private fun supportsTransport(
        test: GuidedTest,
        active: GuidedTransportRequirement?,
    ): Boolean {
        if (active == null) return true
        return when (test.requiredTransport) {
            GuidedTransportRequirement.ANY -> true
            GuidedTransportRequirement.ELM327 ->
                active == GuidedTransportRequirement.ELM327 || active == GuidedTransportRequirement.OEM
            GuidedTransportRequirement.OEM ->
                active == GuidedTransportRequirement.OEM
        }
    }

    private fun score(
        test: GuidedTest,
        query: String,
        codes: List<String>,
    ): GuidedTestMatch {
        val matchedAliases = mutableListOf<String>()
        val matchedPrefixes = mutableListOf<String>()
        var score = 0

        if (query.isNotEmpty()) {
            for (alias in test.symptomAliases) {
                val needle = alias.lowercase()
                if (query.contains(needle) || needle.contains(query)) {
                    matchedAliases += alias
                    score += 8
                }
            }
            if (test.title.lowercase() in query || query in test.title.lowercase()) {
                score += 6
            }
        }

        for (code in codes) {
            for (prefix in test.relatedDtcPrefixes) {
                val normalizedPrefix = prefix.uppercase()
                if (code.startsWith(normalizedPrefix)) {
                    matchedPrefixes += normalizedPrefix
                    score += 12
                }
            }
        }

        if (codes.isNotEmpty() && matchedPrefixes.isEmpty() && query.isEmpty()) {
            score = 0
        }

        return GuidedTestMatch(
            test = test,
            score = score,
            matchedAliases = matchedAliases.distinct(),
            matchedDtcPrefixes = matchedPrefixes.distinct(),
        )
    }

    private fun normalizeDtc(raw: String): String {
        val trimmed = raw.trim().uppercase()
        val code = trimmed.substringAfterLast(' ', trimmed)
        return code.filter { it.isLetterOrDigit() }
    }
}
