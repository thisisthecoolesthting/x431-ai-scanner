package com.caseforge.scanner.transfer

/**
 * Operator PC on the shop LAN (Ricky's office machine).
 * Tablet pushes cnlaunch zip here; PC runs [scripts/lan-export-receiver.ps1].
 */
object LanExportConfig {
    const val RECEIVER_PC_HOST = "192.168.1.129"
    const val RECEIVER_PC_PORT = 8766
    const val TABLET_SERVE_PORT = 8765

    fun receiverUploadUrl(): String = "http://$RECEIVER_PC_HOST:$RECEIVER_PC_PORT/upload"

    fun pcBrowserUrl(tabletHost: String, port: Int): String =
        "http://$tabletHost:$port/"
}
