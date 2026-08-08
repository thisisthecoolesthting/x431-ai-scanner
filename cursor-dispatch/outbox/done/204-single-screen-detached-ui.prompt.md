# Task 204 — Single-screen detached UI (no wizard, no X431, no overlay mode)

**Goal:** Together opens into ONE screen. That's the app. Phase 1 overlay code is dead code in the tree but never reachable from any user flow.

## What to kill

- **Setup wizard** — `ui/wizard/SetupWizardScreen.kt` no longer launches on first run. First-run permissions (Bluetooth, USB, microphone, etc.) get requested inline on the main screen as needed.
- **"Take over X431" CTA** — gone from the dashboard. The whole concept of "X431 underneath" is gone from the user experience.
- **MainActivity X431 auto-launch** — never launches the `com.cnlaunch.x431padv` package. Ever.
- **Floating bubble OverlayService** — disabled in normal mode. The bubble is dead in the user flow.
- **`directVciExperimental` flag** — flipped to true permanently. Then remove the flag entirely from the code in a follow-up cleanup pass (don't delete this commit; just stop reading the flag).

## The one screen

Replace MainActivity's content with **`MainScreen.kt`** — a single Compose screen that contains:

- **Top bar**: "Together Scanners AI" + warm-amber brand. Status indicators on the right: VCI connection state (red/yellow/green dot + label), live-action ticker (from task 201's design — pulsing dot + last AgentActionLog line).
- **VIN + Vehicle card** at the top. Shows current VIN if connected; placeholder "Connect VCI to read VIN" otherwise.
- **Six big action tiles** in a 2x3 grid:
  1. **Scan** → reads all DTCs, opens the existing ReportScreen
  2. **Live Data** → opens LiveDataScreen with sensor stream
  3. **Service** → list of service-reset capabilities (oil reset, EPB, TPMS, throttle relearn, etc.)
  4. **Bidirectional** → list of actuation tests
  5. **Recalls/TSB** → NHTSA cross-reference for current VIN
  6. **History** → past scans + repair stories
- **Bottom bar**: AI prompt input ("Tell me what's wrong / Ask Together…") — same speech-to-text + send button as the existing dashboard.
- **Connection drawer** (slide-in from the right edge or a "Connect" pill in the top bar): shows transport picker (USB/Bluetooth/Auto), bonded device list, USB device list, connect/disconnect, diagnostics shortcut.

Everything else (settings, log, history, customer DB, reports, recalls) is reachable via icon buttons in the top bar — gear, history, notes — same icons we have today.

## What to keep

The existing per-ScreenKind screens (ReportScreen, LiveDataScreen, ActuationScreen, ModuleListScreen, etc.) are still where the user lands when they tap an action tile. We're not throwing those away — just changing the entry point.

EngineDriver, VciCommunicator (post-202 with USB transport), AgentRunner, the AI features (predictive next-test, cross-module correlator, voice, repair-story PDF, recall flag, multi-step sequences) — all stay. They consume the same EngineState data path from Direct VCI.

## Files

CREATE:
- `app/src/main/kotlin/com/caseforge/scanner/ui/main/MainScreen.kt` — the single-screen entry point.
- `app/src/main/kotlin/com/caseforge/scanner/ui/main/ConnectionDrawer.kt` — slide-in connection panel.
- `app/src/main/kotlin/com/caseforge/scanner/ui/main/ActionTile.kt` — reusable tile composable.

MODIFY:
- `app/src/main/kotlin/com/caseforge/scanner/MainActivity.kt` — set content to MainScreen, remove all X431 launch logic, remove wizard gate.
- `app/src/main/AndroidManifest.xml` — remove the SetupWizardActivity entry point if it's separate; keep MainActivity as the LAUNCHER.

DO NOT delete (yet):
- `ui/wizard/*` — leaves it as dead code for one cycle in case we need to roll back.
- `overlay/OverlayService.kt` (the bubble) — same reason.
- The X431 accessibility hooks — same reason.

## Permissions inline

Move all the wizard's permission requests into the main screen — request lazily when the action that needs them is tapped:
- Bluetooth → first time the tech opens the Connection drawer
- USB → handled by the system via the `USB_DEVICE_ATTACHED` intent filter
- Microphone → first time the tech taps the speech mic
- Accessibility → no longer required (overlay mode is dead)
- Notifications → first time the tech enables voice mode or a long-running scan

## Acceptance

- Cold-launch the app → MainScreen, no wizard, no X431, no choice screens.
- Tap **Scan** → if not connected, opens Connection drawer; if connected, runs Mode 03 read and shows ReportScreen with the result.
- Connection drawer shows USB and Bluetooth options, plus a "Diagnostics" link to the VciDiagnosticsScreen from task 201.
- VIN auto-populates when VCI is connected and Mode 09 succeeds.
- Top-bar live-action ticker shows pulse + last action during any work.
- No reference anywhere in the user flow to X431, "overlay", "take over", "peek".
- All existing AI features (next-test card on Report, recall card on Report, voice mic, repair-story PDF) still work end-to-end on the standalone data path.
- CI green on `feat/single-screen-ui` branch. Self-merge.

## Done

When merged, post in chat: **"Single-screen detached UI shipped — task 203 server is up — ready for the cnlaunch data folder."** Then wait for Ricky's data upload before starting task 205.

Move this prompt to `cursor-dispatch/done/`.

# Notes from Cursor

- Merged PR #10 → `main` @ `7e288b3` (squash). CI green after import fix `b1ff44c`.
- Added `ui/main/*`: MainScreen, ActionTile, ConnectionDrawer, StandaloneVciController, RecallsScreen; MainActivity routes to `main` by default.
- `directVciExperimental` + `wizardComplete` default **true** in SettingsRepo.
- Legacy wizard/dashboard/home routes removed from MainActivity navigation (files kept in tree).
- Connection drawer: Bluetooth connect + diagnostics; USB noted as task 202.
- Service/Bidirectional tiles open ModuleListScreen (Service category) / ActuationScreen.
- **205 blocked** until operator confirms cnlaunch LAN upload.
