# K2 lane summary — Together Car Works rebrand sweep

**Branch:** `chore/rebrand-tcw-sweep`  
**Worktree:** `C:\Users\reasn\Documents\Claude\Projects\DEv1\_x431-worktrees\K2`

## Commits (`main..HEAD`)

| SHA | Message |
|-----|---------|
| `ce19dbe` | refactor: rename legacy OEM theme -> Theme.TogetherCarWorks |
| `1bf1142` | refactor: rename VciSocketClient -> BluetoothVciClient |
| `1abada1` | refactor: rename VciUsbClient -> OemUsbVciClient |
| `206e10b` | refactor: rename LinkKind/UserTransport LAUNCH_* -> OEM_* |
| `95c9962` | chore: app_name = Together Car Works in strings.xml + manifest labels |
| `865ad4b` | chore: rewrite accessibility_service_config copy to drop OEM brand |
| `5a78e2b` | chore: SettingsRepo.linkTransport accepts launch_* legacy reads, writes oem_* |
| `3a7f368` | feat: AboutScreen for Together Car Works |
| `e9a4063` | chore: CI release artifact + cache key rename to tcw |
| `d0963b6` | chore: rebrand README |
| `7f7ff1f` | feat: scripts/run-rebrand-grep.ps1 + rebrand-audit job (script only) |
| `863104d` | feat: scripts/run-rebrand-grep.ps1 + rebrand-audit job (remaining rebrand bulk) |

> Note: commits `7f7ff1f` and `863104d` share the same subject; squash before merge if you want exactly eleven commits.

## Class / file renames

| Before | After |
|--------|--------|
| Legacy OEM theme style | `Theme.TogetherCarWorks` |
| Legacy theme composable | `TogetherCarWorksTheme` |
| `vci/VciSocketClient.kt` | `vci/BluetoothVciClient.kt` |
| `vci/VciUsbClient.kt` | `vci/OemUsbVciClient.kt` |
| `vci/CnlaunchAssetIndex.kt` | `vci/OemVehicleAssetIndex.kt` |
| Old vendor asset tree | `assets/oem-vehicle-db/*` |
| `scripts/extract-x431-apk.ps1` | `scripts/extract-oem-tablet-apk.ps1` |

## Enum / API renames

- `DiagnosticConnector.LinkKind.LAUNCH_USB` / `LAUNCH_BT` → `OEM_USB` / `OEM_BT`
- `DiagnosticConnector.UserTransport` — same mapping
- `ScannerAccessibilityService.X431_PACKAGES` → `OEM_DIAG_PACKAGES`
- `bringX431ToFront()` → `bringOemDiagToFront()`
- `App.isX431Foreground()` → `App.isOemDiagForeground()`
- `HealthState.x431Foreground` → `oemDiagForeground`
- `SettingsRepo.overlayOnX431` → `overlayOnOemDiag` (pref key `overlay_on_oem_diag`)

## Settings / prefs

- `linkTransport`: reads `launch_usb` / `launch_bt` (and legacy `vci_usb` / `vci_bt`), normalizes to `oem_usb` / `oem_bt`; writes `oem_*` only
- `tcw.claudeApiKey` preferred in `app/build.gradle.kts` with legacy local.properties key fallback
- Room DB filename moved to `tcw.db` (destructive migration already enabled)

## New UI

- `ui/about/AboutScreen.kt` — wordmark, version/build, “Send diagnostics” / “View on GitHub”; route `"about"` not wired (comment only)

## CI

- Job `rebrand-audit` runs `scripts/run-rebrand-grep.ps1` on every push/PR
- Artifact `tcw-android-debug`, APK rename `tcw-${{ github.run_number }}.apk`, keystore cache `tcw-debug-keystore-v1`

## Audit

```text
pwsh -File scripts/run-rebrand-grep.ps1
→ Rebrand audit clean.
```

Allowed exceptions in script: frozen Android package id paths, OEM tablet package IDs on disk, `transfer/**`, `agent/Updater.kt` (K3), legacy script names, `OEM_DATA_PATH` (K1).

## Cross-lane notes

- **K1** still owns `transfer/**`, `ui/transfer/**`, `VehicleDatabasePathResolver` / `OEM_DATA_PATH`. Avoid merging K2 before K1 if both touch transfer paths.
- **K3** owns `agent/Updater.kt` — excluded from audit; GitHub release URLs there may still point at the old repo until K3 lands.
- **K4** owns launcher mipmaps / empty states — not modified in K2.
- `MainScreen` title is **Together Car Works**; `ConnectionDrawer` chips: USB OBD, OEM USB, ELM327 BT, OEM BT, Auto.
- PDF header/footer strings updated in `report/PdfReportBuilder.kt` where present.

## Files touched (high level)

Manifest, `strings.xml`, themes, ~50 Kotlin sources (excluding K1/K3 scopes), `capabilities.json`, tests (`EngineHealthMonitorTest`, `OemVehicleAssetIndexTest`), `README.md`, `docs/COMMERCIAL_PLAN_TCW.md`, bootstrap scripts, `.github/workflows/build.yml`, `settings.gradle.kts`, `app/build.gradle.kts`.
