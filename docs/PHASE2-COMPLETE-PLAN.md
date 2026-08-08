# PHASE 2 COMPLETE BUILD PLAN — `spike/direct-vci` to ship-ready

**You are Cursor on the `spike/direct-vci` branch. Codex CLI is your peer. Claude is out.** This is the plan to take the existing VCI scaffold and ship it as production-ready Phase 2 (standalone, no OEM diagnostic tablet needed) gated behind the experimental feature flag, merged into main when green.

## What's already on this branch

- `vci/VciFrame.kt` `VciOpcodes.kt` `BluetoothVciClient.kt` `VciCommunicator.kt`
- Settings toggle `directVciExperimental`
- Direct VCI **probe** screen (bonded VCI connect, Mode 03 attempt, hex/binary toggle)
- `scripts/frida-vci-intercept.js`
- `docs/VCI-SPIKE-REPORT.md` and `VCI-SPIKE-VERDICT.md`

## North-star for this build

When done, a technician can:

1. Boot the tablet, open Together, toggle "Direct VCI (experimental)" ON.
2. **OEM diagnostic tablet never launches.** Together connects to the VCI dongle over Bluetooth SPP directly.
3. Read DTCs (Mode 03), clear (Mode 04), pending (Mode 07), live PIDs (Mode 01), VIN (Mode 09) — generic OBD-II, all OEMs.
4. Together's AI features (predictive next-test, cross-module correlator, voice mode, repair-story PDF, recall flag) still work — they consume the Direct-VCI data path the same way they consumed the OEM diagnostic tablet-scraped data path before.
5. OEM-specific functions (programming, coding, service, bidirectional) are GATED — they fall back to overlay mode (re-OEM OEM diagnostic tablet) until OEM protocol packs land. Surface this clearly in the UI.

Phase 2 v1 ship = generic OBD-II works standalone. OEM-specific stays overlay-mode-only. That's the line.

## Two-unknown resolution (Day 1)

The probe screen on this branch can guess. Approach: brute-force first, Frida if brute-force fails.

### Brute-force first (no Frida, fast)
1. Tablet test the probe screen built into Together on this branch's APK.
2. With OEM diagnostic tablet NOT running, bonded VCI plugged into a vehicle, press "Connect" → "Read DTCs".
3. Try both transport modes (raw binary, hex-ASCII) — toggle in the probe UI.
4. Try both header magic candidates — 0x55 0xAA first; if no response, sweep 0xAA 0x55, 0xFE 0x01, 0x40 0xC8 (these are common OEM tool patterns).
5. If ANY combo returns a parseable response → header + transport are locked in. Write the answer into `VciFrame.kt` as defaults, drop the toggle from the probe UI. Move on.

### Frida fallback (if brute-force exhausted)
Run `scripts/frida-vci-intercept.js` against a running OEM diagnostic tablet doing a scan. Captures real wire frames. Read header + transport from the capture. ~30 min total once Frida-server is on the tablet.

Acceptance for Day 1: `VciCommunicator.readDtcs()` returns one real DTC code from a connected vehicle without OEM diagnostic tablet running. Document the confirmed header + transport in `docs/VCI-SPIKE-VERDICT.md`.

## Stream allocation (5 streams parallel)

### Stream A — Cursor Composer (you, interactive)
Day 1: protocol validation via probe UI / Frida. Day 2-3: wire `EngineDriver` to consume `VciCommunicator` when `directVciExperimental` is ON (instead of `ScannerAccessibilityService`). Day 4-5: smoke + polish.

### Stream B — Codex CLI session 1: Mode coverage
Open a Codex session on this branch. Paste:
> "Read docs/PHASE2-COMPLETE-PLAN.md on this branch. Implement Mode 01 PID parsing in VciCommunicator.kt: support PIDs 0x05 (coolant temp), 0x0C (RPM), 0x0D (speed), 0x10 (MAF), 0x11 (throttle), 0x14-0x1B (O2 sensors), 0x42 (control module voltage). Each with the correct scaling formula per SAE J1979. Also Mode 09 PID 0x02 (VIN read - assemble 17 chars from 3 frames). Push commits to spike/direct-vci. Add unit tests in app/src/test/.../vci/ using synthetic Mode 01/09 frames."

### Stream C — Codex CLI session 2: EngineDriver Direct-VCI route
> "Read EngineDriver.kt (existing) and VciCommunicator.kt (new). Refactor EngineDriver so that when settings.directVciExperimental is true, every public method (runCapability, fullScan, readDtcs, clearCodes, liveData, actuate) routes to VciCommunicator instead of walking OEM diagnostic tablet menus via ScannerAccessibilityService. When directVciExperimental is false, behavior is unchanged (overlay mode). Add a feature-flag enum so tests can swap. Push to spike/direct-vci. Add unit tests covering both routes."

### Stream D — Codex CLI session 3: Standalone UI flows
> "Read OverlayRoot.kt, ScreenKind sealed class, ModuleListScreen.kt, ReportScreen.kt. When standalone mode is on (directVciExperimental == true), the same ScreenKind tree should render BUT without the 'Peek OEM diagnostic tablet' hint (since OEM diagnostic tablet isn't running). Replace the 'Take over OEM diagnostic tablet' dashboard CTA copy with 'Connect to VCI' when in standalone mode. Make the standalone path also skip launching OEM diagnostic tablet in MainActivity. Push to spike/direct-vci."

### Stream E — Cursor Background Agent: data-folder consumption (parallel, only if data is delivered)
If Ricky has copied `/sdcard/vehicle database/` to the PC, mount it. Background Agent task:
> "Crawl the vehicle database/ folder for any plain-text or XML files containing DTC code→description mappings and menu tree definitions. Output two assets: (1) assets/dtc-descriptions.json keyed by OEM + DTC code → description, (2) assets/menu-trees.json keyed by OEM + capability id → ordered menu path. Wire these into VciCommunicator's response parser so Mode 03 returns the description alongside the code. Push to spike/direct-vci."

If data not yet delivered, Stream E waits. The other 4 streams don't depend on it.

## Acceptance gate per Phase 2 ship

Required (all must pass before merging spike/direct-vci → main):
- [ ] Mode 03 DTC read returns real codes from a connected vehicle, OEM diagnostic tablet not running.
- [ ] Mode 04 clears codes; verified by re-reading and getting empty result.
- [ ] Mode 01 live PIDs render in LiveDataScreen with correct units and scaling.
- [ ] Mode 09 VIN read returns 17 valid chars; matches NHTSA decode.
- [ ] Settings toggle "Direct VCI (experimental)" cleanly switches between standalone and overlay modes.
- [ ] CI green on spike/direct-vci branch.
- [ ] Tablet smoke: tech can complete a full scan + clear cycle without OEM diagnostic tablet running.
- [ ] AI features (next-test, correlator, voice, repair-story) still work on the standalone data path.

When all green → open PR `spike/direct-vci → main`, self-merge.

## Merge strategy

Phase 2 must stay safe. Even after merge:
- Feature flag `directVciExperimental` defaults to **false**.
- Toggle is in a "Developer / Experimental" Settings section, not the main Settings.
- A banner shows when standalone mode is active so the tech remembers they're on the experimental path.
- The classic overlay-over-OEM diagnostic tablet mode remains the default and is never broken by this work.

## Kickoff — paste into Cursor Composer on this branch

```
You are on branch spike/direct-vci. Read docs/PHASE2-COMPLETE-PLAN.md, .cursor/rules/together-rules.md, HANDOFF-TO-CURSOR.md.

Execute the 5-stream parallel plan. You take Stream A interactively. Spawn three Codex CLI sessions for Streams B, C, D with the prompts from the plan. Stream E (data folder) waits until Ricky delivers /sdcard/vehicle database/ — if you don't have it yet, run with 4 streams.

Day 1 priority: resolve the two protocol unknowns via brute-force probe testing on the tablet (or Frida fallback). Confirm header magic and transport mode. Bake the confirmed answers into VciFrame.kt defaults.

Then push through Mode 01, 03, 04, 07, 09 to acceptance-gate green. CI green on spike/direct-vci. THEN open PR to main.

Do not merge spike/direct-vci into main without going through the acceptance gate checklist in the plan. Don't ask permission for routine work otherwise. Keep the experimental flag defaulted to false on merge.

Go.
```

When this kickoff runs to completion, Phase 2 ships standalone-capable Together to the tablet's in-app updater.
