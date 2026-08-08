package com.caseforge.scanner.planb.immo

import com.caseforge.scanner.planb.PlanbMarque

/**
 * SKIM/SKREEM immobilizer key-learning — catalog capability [CAPABILITY_ID], Tier 3 info + Tier 4 runbook only.
 * No automated PIN capture, key programming, or security bypass in this app.
 */
object SkreemModule {

    const val CAPABILITY_ID = "programming_skim_key_learn"

    /** Matches [CapabilityEntry.path] in bundled capabilities.json. */
    val CAPABILITY_PATH: List<String> = listOf(
        "programming",
        "immobilizer",
        "skim_key_learn",
    )

    const val MODULE_NAME = "SKIM / SKREEM"

    const val TIER3_ROLE_SUMMARY: String =
        "The SKIM (Sentry Key Immobilizer Module) / SKREEM (Sentry Key Remote Entry Module) " +
            "validates transponder keys against the PCM security policy. Tier 3 surfaces module role " +
            "and risk context only — no live PIN entry or key writes."

    const val TIER4_BLOCKED_BANNER: String =
        "SKIM/SKREEM key learning requires OEM PIN and authorized tooling. " +
            "This app provides a partner/manual checklist only; automated flash and key learn stay blocked."

    fun isStellantisMarque(marque: PlanbMarque): Boolean =
        marque == PlanbMarque.JEEP ||
            marque == PlanbMarque.DODGE ||
            marque == PlanbMarque.RAM ||
            marque == PlanbMarque.CHRYSLER

    fun fordNotApplicableNote(): String =
        "SKIM/SKREEM does not apply to Ford platforms — use PATS information and Ford Tier 4 checklists instead."
}
