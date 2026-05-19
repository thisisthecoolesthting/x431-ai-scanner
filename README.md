# Together Car Works

Together Car Works (TCW) is an AI-driven OBD-II scanner for working technicians. The app runs on the same Android tablet as the OEM diagnostic stack: it can drive the OEM diagnostic app via accessibility, connect directly over USB OBD / OEM VCI / ELM327 Bluetooth, and produce repair stories and PDFs the shop can hand to customers.

## What it does

- **Auto-start on VIN** — when a 17-character VIN appears, the agent can kick off a diagnostic session automatically.
- **Drives the OEM diagnostic app** — reads DTCs, freeze frame, and live data; navigates module menus; runs bidirectional tests; produces a triage report.
- **Report ingest** — share an OEM diagnostic PDF to this app (or drop one into the watched directory) and the AI triages it.
- **Floating bubble** — a draggable bubble over the OEM app gives on-demand AI commentary on whatever screen you are on.
- **Customer write-up** — generates invoice-ready explanations.

## Important — autonomy and safety

This build is configured for **fully autonomous actuation**: the agent can run bidirectional tests (relays, solenoids, injectors, etc.) without asking. Bidirectional tests can move real hardware and, on the wrong module, can do damage.

Use the **Kill Switch** in Settings to stop the agent and block new sessions. Every action the agent takes is recorded in `agent_actions.log` (app private storage). If you want a per-action confirmation flow instead, turn off **Fully autonomous actuation** in Settings.

## Building

**Option 1 — Windows batch.** From this folder:

1. Double-click **`build.bat`** (downloads JDK/SDK on first run, then builds `app/build/outputs/apk/debug/app-debug.apk`).
2. Enable USB debugging on the tablet, then double-click **`install.bat`** to push via ADB.

**Option 2 — GitHub Actions.** Push to GitHub; `.github/workflows/build.yml` builds on every push and uploads `tcw-android-debug` artifacts.

**Option 3 — Android Studio.** Open the project, sync Gradle, Build → Build APK.

### First launch

The setup wizard walks through: Claude API key, accessibility service, overlay permission, optional screen capture, and optional vehicle-database export to the office PC.

## Architecture (high level)

```
OEM diagnostic app (foreground)
        ↑   ↓
ScannerAccessibilityService  ←  AgentRunner (Claude tool-use loop)
        ↑                              ↓
   ScreenCaptureService / OverlayService / ReportWatcherService
```

| Area | Role |
|------|------|
| `agent/ScannerAccessibilityService.kt` | Reads + drives the OEM diagnostic UI |
| `vci/` | Direct USB/BT links (ELM327, OEM VCI) |
| `engine/` | Capability map, health monitor, scraper |
| `ingest/` | PDF share + report watcher |

Package id remains `com.caseforge.scanner` for in-app upgrade compatibility; user-visible branding is **Together Car Works**.

## License / compliance

This app does not modify or repackage the OEM diagnostic software. It interacts with it as a user would. Read your OEM license terms before deploying commercially.
