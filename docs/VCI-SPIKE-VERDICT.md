# Phase 2 VCI Spike Verdict

**Branch:** `spike/direct-vci` (not merged to main)  
**Date:** 2026-05-18

## Status: RUNTIME VALIDATION PENDING

### Shipped on spike branch

- `VciFrame`, `VciSocketClient`, `VciOpcodes`, `VciCommunicator` (from decompile + open-protocol heuristics)
- `scripts/frida-vci-intercept.js` for tablet packet capture
- Settings toggle **Direct VCI (experimental)** — defaults off
- Full wire-format notes in `docs/VCI-SPIKE-REPORT.md`

### Confirmed from decompile

- SPP UUID `00001101-0000-1000-8000-00805F9B34FB`
- Frame layout: 2-byte header + BE opcode + BE length + payload + XOR checksum
- Hex-ASCII encoding used on LocalSocket IPC layer (may or may not apply at raw SPP)

### Still unknown (requires Frida on live tablet)

1. Header magic bytes (placeholder `0x55 0xAA`)
2. Raw binary vs hex-ASCII on direct SPP
3. Handshake opcode sequence before Mode 03

### Next steps

1. Run Frida script during a normal X431 scan; log 50+ frames
2. Flip `useHexEncoding` on `VciSocketClient` based on capture
3. Attempt `VciCommunicator.readDtcs()` with X431 **not** running
4. Ricky approves merge to main only after successful bench/vehicle read
