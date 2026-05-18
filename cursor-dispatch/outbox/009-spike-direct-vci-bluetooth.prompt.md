# Task 009 — Phase 2 Spike: Direct VCI Bluetooth Protocol

**Goal:** Phase 2 spike on direct VCI Bluetooth protocol. Work on branch `spike/direct-vci`. Two-week budget. Goal: prove Mode 03 DTC read direct-via-VCI without X431 running. Resolve two critical unknowns via Frida runtime capture.

## What ships

1. Foundation files from `drafts/F10-VCI-SPIKE/`: VciFrame.kt, VciSocketClient.kt, VciOpcodes.kt, VciCommunicator.kt.
2. Frida runtime capture scripts (JavaScript) to intercept VCI Bluetooth traffic on actual tablet, extract header magic bytes + transport encoding.
3. Updated `SPIKE-REPORT.md` with findings: header format, opcode map, Mode 03 DTC read sequence, binary vs hex-ASCII resolution.
4. Proof-of-concept DTC read via direct VCI (no X431 app needed).

## Critical unknowns to resolve

1. **Header magic bytes** — likely 0x55 0xAA, need runtime verification via Frida.
2. **Transport encoding** — raw binary vs hex-ASCII over SPP, need packet capture on actual VCI device.

## Files to read

- `drafts/F10-VCI-SPIKE/` (all foundation sources)
- `E:\Projects\together-decompile\decompiled\sources\com\cnlaunch\bluetooth\` (VCI driver source, unobfuscated)
- `together-decompile/findings/010-vci-bluetooth-protocol.md` (wire frame spec, opcode candidates)
- `together-decompile/findings/011-diagnostic-engine.md` (DiagnoseService Messenger dispatcher for reference)

## Files to write/modify

- **Branch:** `spike/direct-vci` (do NOT commit to main)
- **Create:** `app/src/main/kotlin/com/caseforge/scanner/vci/VciFrame.kt` (copy from drafts, rename package)
- **Create:** `app/src/main/kotlin/com/caseforge/scanner/vci/VciSocketClient.kt`
- **Create:** `app/src/main/kotlin/com/caseforge/scanner/vci/VciOpcodes.kt`
- **Create:** `app/src/main/kotlin/com/caseforge/scanner/vci/VciCommunicator.kt`
- **Create:** `scripts/frida-vci-intercept.js` — Frida hook to capture Bluetooth traffic
- **Update:** `SPIKE-REPORT.md` with findings

## Acceptance

- Code compiles on spike branch.
- Frida script runs on tablet, captures VCI Bluetooth frames.
- Header magic bytes confirmed via runtime capture.
- Transport encoding (binary vs hex-ASCII) verified.
- Mode 03 DTC read sequence tested end-to-end (read DTCs without X431 running).
- SPIKE-REPORT.md updated with complete findings + next steps for Phase 2 main build.

## Notes

- Two-week budget. No pressure for production-ready code; goal is research + proof.
- Codex CLI on the spike branch recommended for heavy protocol work.
- Do NOT merge spike branch to main. Results flow to Phase 2 mainline via SPIKE-REPORT.md.
- Decompiled source reference: `E:\Projects\together-decompile\decompiled\sources\com\cnlaunch\bluetooth\*` (34,995 files, unobfuscated bluetooth driver).

## Done

Once findings complete, commit all to spike branch, do NOT push to main. File a Phase 2 ticket referencing `SPIKE-REPORT.md` for the mainline team.
