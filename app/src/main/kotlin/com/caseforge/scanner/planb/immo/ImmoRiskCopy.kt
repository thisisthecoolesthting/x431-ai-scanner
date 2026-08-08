package com.caseforge.scanner.planb.immo

/**
 * Static, vehicle-neutral disclaimers for the immobilizer info tier (no key programming).
 */
object ImmoRiskCopy {
    const val infoOnlyBanner: String =
        "Immobilizer info only. This tier does not perform key programming or security resets."

    const val fullDisclaimer: String =
        "Incorrect immobilizer or security work can disable starting or lock out modules. " +
            "Use OEM procedures and authorized tools. This app provides read-only context only."
}
