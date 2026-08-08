package com.caseforge.scanner.planb.gateway

/**
 * Operational notes for Stellantis-architecture gateways (often referred to commercially as **SGW** /
 * security gateway firewall behavior).
 *
 * **Diagnostic risk:** A secure gateway sits between an aftermarket diagnostic link (OBD-II / third-party
 * VCI) and in-vehicle networks. Arbitration IDs referenced in app data ([EcuEntry.reqId], [EcuEntry.respId])
 * may be correct for the ECU on paper while still being unreachable from the aftermarket path without
 * the correct session/authentication—or not at all when the firewall policy forbids them. Silence,
 * timeouts, or negative responses do not imply a wiring fault versus a gateway policy limitation.
 *
 * **UI doctrine:** Anything shown to technicians must describe limitations in neutral OEM-agnostic language
 * (for example “Gateway may restrict this request”) rather than slang, accusations, or tool-brand attacks.
 *
 * Kotlin-side constants below are identifiers for logging or future localization keys only; they are **not**
 * final user-visible copy.
 */
object StellantisGatewayNotes {

    /** Internal key — human copy should be authored via string resources before shipping. */
    const val GATEWAY_POLICY_MAY_RESTRICT_DIAG = "gateway.policy_may_restrict"

    /** Internal key — use neutral wording only in localized UI. */
    const val GATEWAY_AUTHENTICATION_MAY_BE_REQUIRED = "gateway.auth_may_be_required"
}
