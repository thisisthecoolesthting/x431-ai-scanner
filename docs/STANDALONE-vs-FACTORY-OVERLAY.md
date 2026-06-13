# Together Car Works — two modes in one APK

## Standalone scanner (default product)

**No factory diagnostic app required.**

| Entry | Code |
|-------|------|
| Home | `MainScreen`, `AiCopilotHomeScreen` |
| Connect | `DiagnosticConnector`, `DirectVciSession`, `StandaloneVciController` |
| Transports | ELM327 USB, ELM327 BT, OEM VCI USB/BT |

Paths: `app/src/main/kotlin/com/caseforge/scanner/vci/`, `ui/main/`, `agent/ObdUsbTool.kt`, `agent/ObdBluetoothTool.kt`

## Factory-tablet overlay (optional)

**Uses the preinstalled OEM diagnostic app via accessibility.**

| Entry | Code |
|-------|------|
| Overlay service | `overlay/FullScreenOverlayService.kt` |
| Accessibility | `agent/ScannerAccessibilityService.kt` |
| UI scraper | `engine/EngineScraper.kt` |

Toggle: Settings → **Show overlay on the factory diagnostic app** (`overlayOnOemDiag`).

## Compatibility seam (vendor package IDs only)

All Android package IDs and `/sdcard/…` paths for the factory stack live in **one file**:

`app/src/main/kotlin/com/caseforge/scanner/oem/OemTabletCompat.kt`

plus `res/values/oem_compat_strings.xml` for the accessibility manifest filter.

Standalone code should **not** import `OemTabletCompat` unless it checks foreground factory app or exports vehicle DBs.

## Rebrand audit

```powershell
pwsh -NoProfile -File scripts/run-rebrand-grep.ps1
```

Fails if forbidden vendor branding appears outside `OemTabletCompat.kt` and `oem_compat_strings.xml`.
