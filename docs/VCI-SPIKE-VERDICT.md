# Direct VCI spike verdict (branch `spike/direct-vci`)

**Date:** 2026-05-18  
**Branch:** `spike/direct-vci` (not merged to `main` without operator approval)

## Shipped on spike branch

- `app/src/main/kotlin/com/caseforge/scanner/vci/` — VciFrame, VciOpcodes, VciSocketClient, VciCommunicator (from F10 drafts, package `com.caseforge.scanner.vci`)
- Settings toggle `directVciExperimental` + **Direct VCI probe** screen (bonded VCI connect, Mode 03 read attempt, hex/binary transport switch)
- `scripts/frida-vci-intercept.js` — runtime capture hooks for LocalSocket + BT OutputStream
- JVM unit tests: `VciFrameTest`, `VciCommunicatorTest`, `EngineDriverDirectVciTest` (run `gradle :app:testDebugUnitTest`)

## Defaults (until tablet confirms)

| Setting | Default | Notes |
|---------|---------|--------|
| Header magic | **0x55 0xAA** | `VciFrame.DEFAULT_HEADER` / `VciProtocolConfig.header` |
| Transport | **raw binary** | `vciUseHexEncoding = false` on socket client |
| Hex-ASCII | Off | Toggle in probe UI only if binary DTC read returns empty/garbage |

Do not change production settings until a successful Mode 03 read on a vehicle with X431 **not** in the foreground.

## Header + transport probe sweep (tablet)

Use **Settings → Direct VCI (experimental) → Open Direct VCI probe** with ignition ON, engine off (or per X431 norm), VCI bonded, X431 app **force-stopped**.

1. **Baseline:** header `0x55 0xAA`, binary transport, tap **Connect** then **Read DTCs (Mode 03)**. Note hex log lines and whether any DTCs appear.
2. **Header sweep:** run **Sweep headers** (or manually pick each entry in `HEADER_CANDIDATES` — four pairs: `55 AA`, `AA 55`, `FE 01`, `40 C8`). After each candidate, retry Mode 03. Stop on first non-empty DTC list or plausible OBD positive response in the log.
3. **Transport sweep:** if all four headers fail with binary, enable **Hex encoding**, repeat connect + Mode 03 (and header sweep if needed).
4. **Frida cross-check (optional):** with X431 running a normal scan, run `scripts/frida-vci-intercept.js` and compare captured header bytes + line vs binary to the winning probe settings.
5. **Persist winner:** when a combo works, use probe **Save as default** (writes `vciHeaderByte0/1`, `vciUseHexEncoding`, `vciProtocolConfirmed` via `SettingsRepo`).

CI builds on every push to `spike/direct-vci`; install the Actions artifact APK before field test.

## Confidence (from decompile + spike code)

| Item | Status |
|------|--------|
| SPP UUID `00001101-0000-1000-8000-00805F9B34FB` | HIGH (tb/a.java) |
| Frame: 2-byte header + BE opcode + BE length + payload + XOR checksum | HIGH |
| Checksum XOR over opcode+length+payload | HIGH |
| Header magic 0x55 0xAA | INFERRED — try first; Frida required |
| SPP payload raw binary vs hex-ASCII | UNKNOWN — probe UI toggles `useHexEncoding` |

## Tablet proof still required

`VciCommunicator.readDtcs()` against a vehicle with X431 **not** running must be validated on the workshop tablet. Until then:

- **Verdict:** SPIKE READY FOR TABLET — not PRODUCTION MERGE.
- Copy this summary to private repo `together-decompile/findings/020-vci-spike-result.md` after field test.

## Next operator step

1. Install APK from `spike/direct-vci` CI artifact (or merge after Ricky approves).
2. Enable **Direct VCI (experimental)** in Settings → **Open Direct VCI probe**.
3. Run header sweep (binary first), then hex if needed; save winning settings.
4. Run Frida capture during a normal X431 scan; confirm header + transport.
5. Re-test probe with saved defaults on a second key cycle.
