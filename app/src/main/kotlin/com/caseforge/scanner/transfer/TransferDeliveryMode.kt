package com.caseforge.scanner.transfer

/** Normalized persisted values for [com.caseforge.scanner.data.SettingsRepo.transferDeliveryMode]. */
object TransferDeliveryMode {
    const val SHARE = "share"
    const val SELF_HOSTED = "self_hosted"
    const val LAN_PC = "lan_pc"

    private val ALIASES = mapOf(
        SHARE to SHARE,
        "zip" to SHARE,
        "share_sheet" to SHARE,
        SELF_HOSTED to SELF_HOSTED,
        "self-hosted" to SELF_HOSTED,
        "vps" to SELF_HOSTED,
        LAN_PC to LAN_PC,
        "lan" to LAN_PC,
        "wifi" to LAN_PC,
        "pc_push" to LAN_PC,
    )

    fun normalize(raw: String): String {
        val key = raw.trim().lowercase()
        return ALIASES[key]
            ?: when {
                key.contains("self") -> SELF_HOSTED
                key.contains("lan") || key.contains("pc") -> LAN_PC
                else -> SHARE
            }
    }
}
