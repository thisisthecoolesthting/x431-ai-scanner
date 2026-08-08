package com.caseforge.scanner.ui.updates

sealed class UpdaterPhase {
    object Idle : UpdaterPhase()
    object Checking : UpdaterPhase()
    data class UpdateAvailable(
        val versionName: String,
        val downloadUrl: String,
        val notes: String,
    ) : UpdaterPhase()
    data class Downloading(
        val bytesRead: Long,
        val total: Long,
        val urlOrName: String,
    ) : UpdaterPhase()
    object Installing : UpdaterPhase()
    data class Installed(val versionName: String, val sha: String) : UpdaterPhase()
    data class Failed(val message: String, val hint: String) : UpdaterPhase()
    object NoUpdate : UpdaterPhase()
    object PermissionRequired : UpdaterPhase()
}
