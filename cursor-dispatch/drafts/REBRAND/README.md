# CaseForge Scanner AI

An AI agent that sits on top of the **Launch X431 PRO / PROS / V+** diagnostic scanner app and operates it on the technician's behalf. The agent runs on the same Android tablet the X431 app runs on; it reads the X431 app's UI via Android's Accessibility framework, taps and types into it like a human would, and uses Anthropic's Claude (with tool use) to decide what to do next.

## What it does

- **Auto-start on VIN** — when the X431 app shows a 17-character VIN, the agent kicks off a diagnostic session automatically.
- **Drives the X431 app** — reads DTCs, freeze frame, and live data; navigates module menus; runs bidirectional tests; produces a final triage report.
- **Report ingest** — share an X431 PDF report to this app (or drop one into the watched directory) and the AI triages it.
- **Floating bubble** — a draggable bubble over the X431 app gives you on-demand AI commentary on whatever screen you're on.
- **Customer write-up** — generates invoice-ready explanations.

## Important — autonomy and safety

This build is configured for **fully autonomous actuation**: the agent will run bidirectional tests (relays, solenoids, injectors, etc.) without asking. Bidirectional tests can move real hardware and, on the wrong module, can do damage.

Use the **Kill Switch** in Settings to stop the agent and block new sessions. Every action the agent takes is recorded in `agent_actions.log` (app private storage) — review it after each session. If you want a per-action confirmation flow instead, flip **Fully autonomous actuation** off in Settings (a confirmation UI is not yet wired — wiring it is a half-hour task; ask me to add it).

## Build / install — no Android Studio required

**Option 1 — Double-click on Windows.** From this folder:

1. Double-click **`build.bat`**. It downloads JDK 17 + Android SDK + Gradle 8.9 into `.build-cache/` (~2 GB the first time, ~10–20 min on a fast connection), then builds `app/build/outputs/apk/debug/app-debug.apk`. Re-runs reuse the cache and finish in under a minute.
2. Plug your X431 tablet in over USB with **Developer options → USB debugging** enabled.
3. Double-click **`install.bat`** to push the APK to the tablet via ADB.

**Option 2 — GitHub Actions.** Push this repo to GitHub. The included `.github/workflows/build.yml` builds an APK on every push and uploads it as a downloadable artifact — nothing local to install.

**Option 3 — Android Studio** still works if you prefer it. Open the folder, let it sync, Build → Build APK.

### First launch on the tablet

The app's Home screen walks you through four permissions in order:

- **Claude API key** — paste in Settings.
- **Accessibility Service** — toggle "CaseForge Scanner Agent" on.
- **Display over other apps** — grant when prompted.
- **Screen capture** — accept the system prompt the first time the agent calls the screenshot tool.

## How it works (architecture)

```
Launch X431 app (foreground)
        ↑   ↓
        |   |  AccessibilityService reads the UI tree and dispatches taps/typing
        |   |
ScannerAccessibilityService  <—  AgentRunner (Claude tool-use loop)
        ↑                              ↑
        |                              |   tools: read_screen, tap, type,
        |                              |          scroll, back, wait_for,
        |                              |          capture_screenshot,
        |                              |          finish_session
        |                              ↓
        |                       Anthropic Messages API
        |
   ScreenCaptureService (MediaProjection)  — frames for the vision tool
   OverlayService — floating bubble for manual triage
   ReportWatcherService — watches X431 PDFs in shared storage
   ShareReceiverActivity — accepts shared PDFs
```

The agent loop in `AgentRunner.kt` is the heart of the project: it sends the running conversation + tool schemas to Claude, gets back `tool_use` blocks, executes them on the accessibility service, packs the results into `tool_result` blocks, and repeats until Claude calls `finish_session`. The loop has a hard cap (40 steps) and a kill switch.

## File map

| Path | What |
|---|---|
| `app/src/main/AndroidManifest.xml` | All components, services, permissions |
| `app/src/main/res/xml/accessibility_service_config.xml` | A11y config — bound only to X431 packages |
| `ai/ClaudeClient.kt` | Hand-rolled Messages-API client with tool use + vision |
| `ai/Prompts.kt` | All prompt copy — iterate here |
| `agent/ScannerAccessibilityService.kt` | Reads + drives the X431 UI |
| `agent/AgentTools.kt` | Tool schemas the agent is allowed to call |
| `agent/AgentRunner.kt` | The tool-use loop |
| `agent/AgentActionLog.kt` | Append-only audit log |
| `ingest/ShareReceiverActivity.kt` | "Share to CaseForge" PDF entry |
| `ingest/PdfReportParser.kt` | PdfBox-Android extractor |
| `ingest/ReportWatcherService.kt` | Watches X431 reports dir |
| `overlay/OverlayService.kt` | Floating bubble |
| `overlay/ScreenCaptureService.kt` | MediaProjection screenshot |
| `ocr/ScreenOcr.kt` | ML Kit on-device OCR |
| `ui/...` | Compose screens (Home, Settings, Triage) |
| `data/SettingsRepo.kt` | Encrypted Claude key + prefs |

## Caveats and known limits

- **X431 app package names.** The accessibility service is wired only to the known X431 package names (`com.cnlaunch.x431padv`, etc.). If your tablet's X431 app uses a different package, add it to `accessibility_service_config.xml` and `ScannerAccessibilityService.X431_PACKAGES`.
- **Older Android versions.** Some X431 tablets ship with Android 7 or 8. `minSdk = 24` covers them, but a few APIs (e.g. `setSpecialUseFGS`) only activate on newer OS.
- **Scoped storage.** On Android 11+, the X431 app may write reports under app-private storage that we can't see. The share-target flow is the reliable path; the directory watcher is best-effort.
- **EULA.** This app does not modify, repackage, or reverse the Launch software. It interacts with it as a user would. Read your X431 license terms before deploying commercially.

## Next steps you'll probably want

- Per-test confirmation UI for the "fully autonomous" mode (so the kill switch isn't the only brake).
- Repair-info MCP/tool that the agent can call to look up TSBs and known fixes.
- Vehicle history (Room) so the agent remembers previous sessions per VIN.
- A "demo mode" that records a real session and replays it for testing without burning Claude tokens.
