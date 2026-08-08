package com.caseforge.scanner.obd.j1850

/**
 * Classifies how a SKIM read attempt concluded. Lets downstream code
 * (ImmoReadStateAdapter, UI) branch without string-matching
 * [SkreemReadResult.immobilizerStatus].
 */
enum class SkimReadOutcome {
    /** Adapter confirmed VPW, we sent the request, and we parsed at least
     * one recognizable SKIM frame back from the module. */
    MODULE_PRESENT,

    /** Adapter confirmed VPW and the request was sent, but nothing usable
     * came back (ELM "NO DATA"/"UNABLE TO CONNECT", a frame from some
     * other module but not SKIM, or a blank read), OR ELM init itself
     * failed (adapter unreachable / didn't confirm VPW). */
    NO_RESPONSE,

    /** Something with the right PCI ID came back but it could not be
     * safely interpreted (truncated/empty frame, missing or invalid CRC,
     * unparseable hex text). */
    MALFORMED_RESPONSE
}

/**
 * Domain result of a Phase-1 (READ ONLY) SKIM/SKREEM query over J1850 VPW.
 *
 * READ ONLY: nothing that produces this type writes to the module,
 * programs a key, or captures a PIN/seed/key value - [rawHex] and the
 * other fields only ever describe what the module reported back on
 * request.
 */
data class SkreemReadResult(
    val modulePresent: Boolean,
    val immobilizerStatus: String,
    val keyCount: Int?,
    val vinEcho: String?,
    val rawHex: String,
    val outcome: SkimReadOutcome,
    /** Human-readable extra context, mainly for the negative/malformed paths. */
    val detail: String? = null
)
