# Task 201 — Standalone-mode bugs: CTA copy, live ticker, VCI no-connect

HIGH PRIORITY. Three real product bugs reported by Ricky on tablet build #85 with `directVciExperimental` ON.

## Bug 1 — Dashboard still says "Take over X431"
Stream D was supposed to flip CTA copy to "Connect to VCI" when the flag is on. It didn't. In `app/src/main/kotlin/com/caseforge/scanner/ui/dashboard/DashboardScreen.kt`, read the flag from a `StateFlow<Boolean>` (live) and swap the button label. Also: when the flag is ON, `MainActivity.kt` must NOT auto-launch the X431 package.

## Bug 2 — No live activity indicator
Together looks frozen during any background work. Add a persistent **live ticker** strip in `OverlayRoot.kt` shown on every ScreenKind:
- Pulsing dot (animated alpha 0.3 ⇆ 1.0 every ~1s) when any EngineDriver coroutine is active.
- Rolling label: last `AgentActionLog` event's action, truncated to 60 chars, fades on update.
- "idle" in dim grey when nothing's happening.
24dp height, monospace text, `surfaceContainerLow` background, below the top bar, full width.
Add a `StateFlow<String?>` (`AgentStatus.lastAction`) backed by AgentActionLog (AgentActionLog itself stays read-only — read its tail and re-emit).

## Bug 3 — Direct VCI scan never connects to the car (THE BIG ONE)
Tapping any scan/diag action with `directVciExperimental` ON returns nothing. The Bluetooth socket isn't opening or isn't reading.

Add loud step-by-step logging in `VciSocketClient.connect()`:
- `BluetoothAdapter.getDefaultAdapter()` null check
- Runtime permission `BLUETOOTH_CONNECT` granted? (Android 12+)
- `bondedDevices()` enumeration — log every paired device's name + MAC
- VCI matcher: which prefix matched (or none)
- `createRfcommSocketToServiceRecord(SPP_UUID)` exception
- `socket.connect()` blocks / times out / throws — surface exact reason
- First read from input stream — byte count + first 8 bytes hex

Add a Settings entry **"Direct VCI connection diagnostics"** opening a new `ui/diag/VciDiagnosticsScreen.kt` that runs the chain end-to-end and shows step-by-step pass/fail.

Likely culprits, ranked:

1. **X431 still owns the SPP socket.** Two apps can't share an RFCOMM channel. Before our connect, detect if X431 is running and prompt the user to force-stop it (or do it programmatically via Accessibility if possible).
2. **`BLUETOOTH_CONNECT` not requested at runtime.** Manifest declares it, but Android 12+ needs a runtime request. Add a runtime ask the first time `directVciExperimental` is flipped on.
3. **Wrong protocol — VCI is BLE not SPP.** Some newer Launch VCIs use BLE-only. The SPP UUID `00001101-...` won't work. Decompile findings show BLE service `0000fff0-...` + ISSC characteristics `49535343-FE7D-4AE5-8FA9-9FAFD205E455`. The diagnostics screen should detect protocol type and switch automatically.
4. **VCI name doesn't match our prefix set** (`VCI-`, `Launch-`, `X431-`). Diagnostics screen should list bonded devices and let the user pick if no prefix matches.

## Files

MODIFY:
- `app/src/main/kotlin/com/caseforge/scanner/ui/dashboard/DashboardScreen.kt` — Bug 1
- `app/src/main/kotlin/com/caseforge/scanner/MainActivity.kt` — Bug 1 (gate X431 auto-launch on flag)
- `app/src/main/kotlin/com/caseforge/scanner/overlay/compose/OverlayRoot.kt` — Bug 2 ticker
- `app/src/main/kotlin/com/caseforge/scanner/agent/AgentStatus.kt` — add `lastAction: StateFlow<String?>` (do NOT modify AgentActionLog itself; just observe and re-emit)
- `app/src/main/kotlin/com/caseforge/scanner/vci/VciSocketClient.kt` — Bug 3 logging + BLE-detect fallback
- `app/src/main/kotlin/com/caseforge/scanner/ui/settings/SettingsScreen.kt` — Bug 3 diagnostics entry

CREATE:
- `app/src/main/kotlin/com/caseforge/scanner/ui/diag/VciDiagnosticsScreen.kt` — step-by-step pass/fail UI
- `app/src/main/kotlin/com/caseforge/scanner/vci/VciBleClient.kt` — fallback when SPP fails (BLE characteristics from decompile)

## Acceptance

- Standalone-mode dashboard reads **"Connect to VCI"**; X431 never auto-launches.
- Live ticker visible on every overlay screen, pulses + updates during work.
- "Direct VCI connection diagnostics" runs the chain and prints exact failure reason if any step fails.
- After diagnostics surface the root cause and it's addressed, Mode 03 read returns real DTCs from a connected vehicle in standalone mode.
- CI green on `fix/standalone-bugs`. Self-merge.

## Done
Move to `cursor-dispatch/done/`. This bug fix unblocks Phase 2 actually being useful — top priority above any new feature work.

# Notes from Cursor

- Merged PR #8 (`b292266`) on `fix/standalone-bugs` — CI green.
- Bug 1: `directVciExperimentalFlow` + dashboard **Connect to VCI**; X431 skip when flag on.
- Bug 2: `LiveActivityTicker` in overlay; `AgentStatus.lastAction` from action log tail.
- Bug 3: runtime BT permissions, `VciDiagnosticsScreen`, SPP logging, BLE GATT probe, device picker (`vciSelectedBtAddress`).
- Tablet: run **Settings → Direct VCI connection diagnostics** before scan; force-stop X431 if SPP step fails.
