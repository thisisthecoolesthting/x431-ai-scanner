# Direct VCI spike verdict (branch `spike/direct-vci`)

**Date:** 2026-05-18  
**Branch:** `spike/direct-vci` (not merged to `main` without operator approval)

## Shipped on spike branch

- `app/src/main/kotlin/com/caseforge/scanner/vci/` — VciFrame, VciOpcodes, VciSocketClient, VciCommunicator (from F10 drafts, package `com.caseforge.scanner.vci`)
- Settings toggle `directVciExperimental` + **Direct VCI probe** screen (bonded VCI connect, Mode 03 read attempt, hex/binary transport switch)
- `scripts/frida-vci-intercept.js` — runtime capture hooks for LocalSocket + BT OutputStream

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
3. Run Frida capture during a normal X431 scan; confirm header + transport.
4. Re-test probe with hex toggle if DTC list is empty.
