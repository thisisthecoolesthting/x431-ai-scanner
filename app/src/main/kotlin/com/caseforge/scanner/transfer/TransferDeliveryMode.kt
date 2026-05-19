package com.caseforge.scanner.transfer

/** How vehicle database bundles leave the tablet. Default is [SHARE] (no paid API). */
object TransferDeliveryMode {
    /** Zip on device, then Android share sheet (Drive, email, USB copy apps). $0. */
    const val SHARE = "share"

    /**
     * HTTP POST to a URL you control (your VPS, shop server). No third-party upload API fees.
     * Set [com.caseforge.scanner.data.SettingsRepo.transferDropUrl].
     */
    const val SELF_HOSTED = "self_hosted"

    /** Legacy: direct LAN push to office PC IP:port (advanced; same Wi‑Fi often required). */
    const val LAN_PC = "lan_pc"

    fun normalize(raw: String): String = when (raw.lowercase()) {
        SELF_HOSTED, LAN_PC -> raw.lowercase()
        else -> SHARE
    }

    fun label(mode: String): String = when (normalize(mode)) {
        SHARE -> "Share (free)"
        SELF_HOSTED -> "Your server (free)"
        LAN_PC -> "LAN → PC (advanced)"
        else -> "Share (free)"
    }
}
