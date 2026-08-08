package com.caseforge.scanner.obd

/**
 * Diagnostic trouble code container. [code] is normalized (e.g. `P0133`);
 * [description] is optional until a database or cloud lookup is wired.
 */
data class ObdDtc(
    val code: String,
    val description: String? = null,
    val pending: Boolean = false,
)
