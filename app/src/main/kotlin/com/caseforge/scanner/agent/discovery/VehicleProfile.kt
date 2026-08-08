package com.caseforge.scanner.agent.discovery

import kotlinx.serialization.Serializable

@Serializable
data class VehicleProfile(
    val schemaVersion: Int = 1,
    val id: String,
    val marque: String,
    val model: String,
    val modelYearStart: Int,
    val modelYearEnd: Int,
    val wedgeCardId: String? = null,
    val obdLegislated: Boolean = true,
    val recommendedTier: Int = 0,
    val supportedTiers: List<Int> = listOf(0),
    val protocolNotes: String = "",
    val immoNotes: String = "",
    val notSupported: List<String> = emptyList(),
    val adapterClasses: List<AdapterClassHint> = emptyList(),
    val linkHints: LinkHints = LinkHints(),
    val connectionRunbookRef: String = "",
    val operatorSteps: List<String> = emptyList(),
)

@Serializable
data class AdapterClassHint(
    val id: String,
    val label: String = "",
    val recommended: Boolean = false,
)

@Serializable
data class LinkHints(
    val transportMode: String = "auto",
    val fallbackTransportMode: String = "auto",
    val serial: SerialLinkHint = SerialLinkHint(),
)

@Serializable
data class SerialLinkHint(
    val baud: Int = 115200,
    val dataBits: Int = 8,
    val parity: String = "none",
    val stopBits: Int = 1,
)
