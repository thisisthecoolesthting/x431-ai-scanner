package com.caseforge.scanner.transfer

sealed class SendState {
    object Idle : SendState()
    data class CheckingPc(val host: String, val port: Int) : SendState()
    data class PcReady(val savePath: String, val freeBytes: Long, val latencyMs: Long) : SendState()
    data class PcUnreachable(val reason: String, val remediation: Remediation) : SendState()
    data class ScanningFiles(val files: Int, val bytes: Long) : SendState()
    data class Zipping(
        val filesDone: Int,
        val filesTotal: Int,
        val bytesDone: Long,
        val bytesTotal: Long,
    ) : SendState()
    data class Uploading(
        val bytesSent: Long,
        val bytesTotal: Long,
        val bytesPerSec: Long,
        val etaMs: Long,
    ) : SendState()
    object Verifying : SendState()
    data class Done(
        val pcPath: String,
        val bytes: Long,
        val elapsedMs: Long,
        val sha256: String,
    ) : SendState()
    data class Failed(val reason: String, val remediation: Remediation) : SendState()
}

enum class Remediation {
    NONE,
    OPEN_SETTINGS,
    GRANT_ALL_FILES,
    RESCAN,
    EDIT_PC_IP,
    EDIT_DROP_URL,
    RESUME,
    RETRY,
    OPEN_WIFI_SETTINGS,
    SHOW_FIREWALL_COMMAND,
    OPEN_DIAGNOSTIC_APP,
}
