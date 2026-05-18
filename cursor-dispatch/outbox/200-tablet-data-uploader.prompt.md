# Task 200 — In-app tablet→PC LAN transfer for the cnlaunch data folder

## Goal

Add a Setup-wizard step (and a Settings entry) that lets the tablet ship `/sdcard/cnlaunch/` to a PC on the same LAN over HTTP, without needing USB MTP, ADB, or cloud accounts. This unblocks the Phase 2 data-folder consumption (DTC descriptions, menu trees, OEM .so libraries).

## How it works

When the tech taps **"Export tablet data to PC"** in Setup or Settings:

1. Together starts a tiny HTTP server inside the app (NanoHTTPD or Ktor server module — both run fine on Android) on a high port (e.g., 8765).
2. The tablet displays a screen with:
   - The tablet's LAN IP + port (e.g., `http://192.168.1.42:8765`)
   - A QR code of that URL
   - A 6-digit one-time pass code (random per session, expires when the server stops)
   - A "Stop server" button
3. From the PC, the tech opens the URL in any browser. The browser shows a simple page asking for the pass code.
4. After code entry, the page offers a download link: `cnlaunch-bundle.zip`. The app streams a zip of `/sdcard/cnlaunch/` on the fly. No temp file on tablet storage.
5. PC downloads to wherever the tech chooses. Server auto-stops on download complete OR after a 10-minute idle timeout.

## Files

CREATE:
- `app/src/main/kotlin/com/caseforge/scanner/transfer/LanFileServer.kt` — wraps NanoHTTPD (or Ktor `embeddedServer(Netty)`). Endpoints: `GET /` (pass-code page), `POST /auth` (validate code, set cookie), `GET /download` (streams the zip). Auto-shutdown logic.
- `app/src/main/kotlin/com/caseforge/scanner/transfer/CnlaunchZipper.kt` — wraps `/sdcard/cnlaunch/` into a `ZipOutputStream` on the fly. Skips temp files. Reports progress via Flow<Bytes>.
- `app/src/main/kotlin/com/caseforge/scanner/ui/transfer/ExportDataScreen.kt` — Compose UI showing URL + QR + pass code + Stop button + progress bar.
- `app/src/main/res/values/strings.xml` — add the few user-facing strings.

MODIFY:
- `app/src/main/kotlin/com/caseforge/scanner/ui/wizard/SetupWizardScreen.kt` — add a new optional step "Send tablet data to PC (recommended for offline AI)". Skippable.
- `app/src/main/kotlin/com/caseforge/scanner/ui/settings/SettingsScreen.kt` — add an "Export tablet data" entry that opens ExportDataScreen.
- `app/src/main/AndroidManifest.xml` — add `INTERNET` and `ACCESS_NETWORK_STATE` permissions if not already declared.

## Library

Use `org.nanohttpd:nanohttpd:2.3.1` for the embedded HTTP server (small, well-tested on Android). Add to `app/build.gradle.kts` dependencies. ZXing for the QR code (`com.google.zxing:core:3.5.3`) — already in the project? If yes, reuse; if no, add.

## Security guardrails

- Pass code is 6 random digits regenerated each session. Without it, `/download` returns 401.
- Server binds only to the active LAN interface (not 0.0.0.0 to all interfaces). Use `WifiManager` to find the actual IP. Detect cellular-only / no Wi-Fi → show error instead of binding.
- Server logs every connection to the existing AgentActionLog so the tech can audit.
- Defaults OFF; user must explicitly tap to start. No auto-startup, no background service after the export.

## Acceptance

- Setup wizard has the new optional step; can be skipped.
- Tapping the Settings entry opens ExportDataScreen; pass code shows; QR scannable.
- Browser on a same-LAN PC can enter the code and download `cnlaunch-bundle.zip` (verify with `Expand-Archive` on PC — produces a valid mirror of `/sdcard/cnlaunch/`).
- Stop button cleanly tears down the server. 10-min idle auto-stop also works.
- CI green on a feat/lan-data-export branch. PR to main, self-merge on green.

## Done

Move this prompt to `cursor-dispatch/done/` when the PR merges.
