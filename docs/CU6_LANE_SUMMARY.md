# CU6 Lane Summary — Offline bundle + Update Center UI

**Branch:** `feat/offline-bundle-update-ui`  
**Worktree:** `_tcw-wave2/CU6`

## Files touched

| File | Change |
|---|---|
| `app/src/main/kotlin/com/caseforge/scanner/ui/updates/UpdateCenterScreen.kt` | Offline bundle status card, Update everything section, shared progress patterns |
| `app/src/main/kotlin/com/caseforge/scanner/ui/offline/OfflineBundleScreen.kt` | Optional detail screen for bundled DTC/test counts and no-network note |

## Route wiring (integration lane — not owned by CU6)

`UpdateCenterScreen` and `OfflineBundleScreen` are composable-only until `MainActivity` / `SettingsScreen` land the routes.

Suggested wiring in `MainActivity.kt`:

```kotlin
// State (near other routes):
var route by remember { mutableStateOf("main") }

// Settings → Update Center entry (SettingsScreen callback):
onOpenUpdateCenter = { route = "update_center" }

// Route block:
"update_center" -> SubScreenScaffold(
    title = "Updates",
    onBack = { route = "settings" },
) {
    UpdateCenterScreen(
        versionName = BuildConfig.VERSION_NAME,
        versionCode = BuildConfig.VERSION_CODE,
        buildSha = BuildConfig.BUILD_INFO,
        phaseFlow = Updater.phase,
        onCheckNow = { updaterController.checkNow() },
        onInstall = { url -> updaterController.install(url) },
        onOpenPermissionSettings = { Updater.openInstallPermissionSettings(context) },
        onRestartApp = { /* recreate activity */ },
        onOpenOfflineBundleDetails = { route = "offline_bundle" },
    )
}
"offline_bundle" -> OfflineBundleScreen(
    appVersionName = BuildConfig.VERSION_NAME,
    onBack = { route = "update_center" },
)
```

Requires `Updater.phase` `StateFlow` from the hardening lane (`fix/updater-hardening-and-update-center`).

## Feature flags

- `PC_BUNDLE_SYNC_WIRED` in `UpdateCenterScreen.kt` — set `true` when DX7 PC `/process` bundle merge is live.
- Until then, **Update everything** stays disabled; **App update** card remains usable.

## Done-when

- [x] Offline bundle card shows DTC count, guided-test count, data revision, app version, no-network note
- [x] Update everything section lists app + vehicle sync + PC bundle with disabled placeholder
- [x] Progress UI reuses `LinearProgressIndicator`, `CircularProgressIndicator`, and `DotPulse` from app update card
