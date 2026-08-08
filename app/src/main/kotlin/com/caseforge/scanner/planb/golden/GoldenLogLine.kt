package com.caseforge.scanner.planb.golden

import kotlinx.serialization.Serializable

@Serializable
data class GoldenLogLine(
    val ts: String,
    val dir: String,
    val canId: String,
    val payload: String,
    val uiContext: String = "",
    val oemPackage: String? = null,
    val windowTitle: String? = null,
    val actionId: String? = null,
)
