# K3 Lane Summary — Updater hardening + Update Center

Branch: `fix/updater-hardening-and-update-center`  
Worktree: `_x431-worktrees/K3`

## Commits (6)

1. `chore: rename Updater APK filename to tcw-latest.apk + User-Agent`
2. `fix: Updater download path falls back to filesDir when external null`
3. `feat: UpdaterPhase sealed class + StateFlow`
4. `feat: UpdaterPhase emits Downloading/Installing/Installed/Failed/PermissionRequired`
5. `feat: UpdateCenterScreen renders phase with progress + remediation`
6. `feat: persist update history JSON`

## Files changed

| File | Change |
|------|--------|
| `app/.../agent/Updater.kt` | Rewrite: safe download path, TCW branding, `phase` StateFlow, progress download, install result → phase, `checkForUpdate()`, public `validateApkFile()` |
| `app/.../ui/updates/UpdaterPhase.kt` | New sealed class for all updater UI states |
| `app/.../ui/updates/UpdateCenterScreen.kt` | New Compose screen with phase-driven status card, progress bars, dot-pulse, remediation |
| `app/.../ui/updates/UpdateHistory.kt` | JSON persistence in `filesDir/update-history.json` (last 10 installs) |

## MainActivity wiring TODO (C6 lane)

Add route near other `composable(...)` entries in `MainActivity.kt`:

```kotlin
composable("updates") {
    UpdateCenterScreen(
        versionName = BuildConfig.VERSION_NAME,
        versionCode = BuildConfig.VERSION_CODE,
        buildSha = BuildConfig.BUILD_INFO.substringAfter(" ").take(7),
        phaseFlow = Updater.phase,
        onCheckNow = { /* lifecycleScope.launch(Dispatchers.IO) { Updater.checkForUpdate(applicationContext) } */ },
        onInstall = { url ->
            /* lifecycleScope.launch(Dispatchers.IO) {
                val phase = Updater.phase.value
                val version = (phase as? UpdaterPhase.UpdateAvailable)?.versionName ?: "—"
                Updater.downloadAndInstall(applicationContext, url, version, version) { AgentStatus.setActivity(it) }
            } */
        },
        onOpenPermissionSettings = { Updater.openInstallPermissionSettings(this@MainActivity) },
        onRestartApp = { /* recreate() or Process.killProcess + restart */ },
    )
}
```

**Inject `Updater.phase`:** pass `phaseFlow = Updater.phase` (the public `StateFlow<UpdaterPhase>` on the `Updater` object). Collect in the screen via `phaseFlow.collectAsState()`.

Add nav entry from Settings or About (e.g. `navController.navigate("updates")`).

## Notes

- `MainActivity.checkForAppUpdate()` unchanged; it still calls `checkLatest()` + `downloadAndInstall()` and benefits from phase emissions on download/install.
- Install permission: `downloadAndInstall` emits `PermissionRequired` and returns (no auto settings launch). Update Center shows the settings button.
- APK saved as `tcw-latest.apk` under `getExternalFilesDir(null) ?: filesDir`.
