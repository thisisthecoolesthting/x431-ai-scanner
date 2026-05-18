# F10 VCI Direct Control — Spike Report

**Date:** 2026-05-18  
**Spike Author:** Claude (subagent, Cowork session)  
**Sources used:** JADX decompile of x431Diagnose V8.00.029, findings/010-vci-bluetooth-protocol.md  
**Output:** `drafts/F10-VCI-SPIKE/vci/` — 4 Kotlin files, ~700 lines  

---

## What We Got

### Confirmed from decompile (hard evidence)

| Fact | Source | Confidence |
|------|--------|-----------|
| SPP UUID = `00001101-0000-1000-8000-00805F9B34FB` | `tb/a.java` lines 77-80, both UUID fields | **HIGH** |
| BLE service UUID = `0000fff0-0000-1000-8000-00805f9b34fb` | `sb/b.java` line 510 (constructor) | HIGH (BLE path only) |
| BLE ISSC UUID = `49535343-FE7D-4AE5-8FA9-9FAFD205E455` | `sb/b.java` line 276 (onServicesDiscovered) | HIGH (ISSC BLE only) |
| Frame layout: 2-byte header + 2-byte opcode (BE) + 2-byte length (BE) + payload + 1-byte XOR checksum | `CommunicationCOM.comReceiveData()` | **HIGH** |
| Checksum = XOR over bytes[2 .. totalLen-2] (i.e. opcode+length+payload) | `CommunicationCOM.getCrcByDataLength(arr, 2, len-3)` | **HIGH** |
| Voltage suffix: some firmware appends 2-byte voltage word after checksum | `CommunicationCOM.comReceiveData()` voltage stripping block | HIGH |
| Transport layer is hex-ASCII lines for the LocalSocket IPC path | `LocalSocketClient.recv()` → `BufferedReader.readLine()` + `ByteHexHelper.hexStringToBytes()` | **HIGH** |
| Socket timeout: 20,000 ms default | `LocalSocketClient` field `timeout = 20000` | HIGH |
| Receive buffer: 32,768 bytes | `LocalSocketClient.connect()` → `setReceiveBufferSize(32768)` | HIGH |
| Command types: ONE_TO_ONE (1) and ONE_TO_MORE (2) | `DiagnoseRequestCommand` constants | HIGH |
| Transparent mode flag (`isTransparentMode`) routes data differently | `CommunicationCOM.setMultiChannelMode()` | HIGH |
| BLE MTU negotiation: requests 512 on API ≥ 23 | `sb/b.java onMtuChanged` | HIGH |
| Auto-reconnect: 3 attempts | `sb/b.java` field `f56121v = 3` | HIGH |
| VCI names matching: prefix "98943" for MaxFlight mode | `BluetoothActivity.o0()` | HIGH |

### What the decompile does NOT reveal

| Gap | Why It's Hidden | What To Do |
|-----|----------------|------------|
| **Header magic bytes** (bytes[0-1]) | Assembled in NDK, never in Java layer | Frida hook on `LocalSocketClient.send(byte[])` |
| **Actual wire opcodes** for DTC read, clear, live data, VIN | Opcode dispatch lives in `CommunicationCOM.receiveData(byte[], int)` — a native JNI method | Ghidra/IDA on `libCommunication*.so`, or Frida hook |
| **Whether SPP transport is raw binary or hex-ASCII** | LocalSocket uses hex-ASCII, but the NDK might send raw to the BT socket | tcpdump on BT interface or Frida on SPP `OutputStream.write()` |
| **Handshake payload** after SPP connect | Not in Java layer | Packet capture on first connect |
| **G5 detection logic** (`CToJavaImplements.isMatchForG5`) | Native method | Hook JNI or capture with G5 device attached |
| **Multi-system proprietary scan** (ABS, SRS, TCM) | Entire dispatch mediated by `.so` | Binary RE of diagnostic `.so` + protocol captures per system |
| **Opcode for active tests / actuate** | FEEDBACK_ACTIVITYTEST=9 hints at something, but not the wire value | Dynamic test: trigger actuation in X431 and capture |

---

## What Was Built

### `vci/VciFrame.kt` (~180 lines)
- Wire frame model with encode/decode/validate
- XOR checksum computation matches `CommunicationCOM.getCrcByDataLength` exactly
- Handles hex-ASCII and binary decode paths
- Voltage suffix stripping
- `VciFrame.build(opcode, payload)` convenience constructor
- Extension functions `ByteArray.toHexString()` / `String.hexToByteArray()` — no Android dependency

### `vci/VciOpcodes.kt` (~200 lines)
- `KnownOpcode` enum with direction and confidence annotations
- Every entry is explicitly annotated: `CONFIRMED` vs `INFERRED` vs `UNKNOWN`
- `ResolvedOpcode` sealed class wraps unknown opcodes without crashing
- `FEEDBACK_ID_LABELS` map derived directly from `DiagnoseConstants` — 30+ entries
- All OBD-II standard mode bytes (01/03/04/07/09) represented

### `vci/VciSocketClient.kt` (~210 lines)
- SPP connection via `BluetoothDevice.createRfcommSocketToServiceRecord(SPP_UUID)`
- Coroutine-scoped receive loop (binary + hex-ASCII modes switchable via `useHexEncoding`)
- `frames: Flow<VciFrame>` hot flow backed by buffered channel
- `ConnectionState` state machine with `StateFlow`
- `findBondedVciDevices()` helper matching known VCI name prefixes
- Mirrors all timeout/buffer/reconnect constants from decompile
- `VciException` sealed class hierarchy

### `vci/VciCommunicator.kt` (~300 lines)
- `readDtcs()` — Mode 03 stored DTCs, full SAE J1979 2-byte DTC decoding
- `clearCodes()` — Mode 04
- `fullScan()` — Mode 03 + Mode 07 pending + VIN; single ECU only
- `livePid(pids)` — Mode 01 cold Flow with per-PID formula decoding (RPM, speed, coolant temp, throttle, fuel level, MAF)
- `actuate()` — explicit STUB with warning log; opcode unconfirmed
- `readVin()` — Mode 09 info type 02
- `parseDtcPayload()` / `parsePidResponse()` / `parseVinPayload()` — all internal, unit-testable
- `safeRequest {}` error boundary

---

## How Close Is "Runnable DTC Read" in 1 Week?

**Optimistic path (3-4 days if the VCI passes OBD-II through transparently):**

1. Pair the VCI hardware via Android BT settings.
2. Call `VciSocketClient.connect(macAddress)`.
3. The VCI should forward Mode 03 frames to the vehicle OBD-II bus.
4. `VciCommunicator.readDtcs()` should return a decoded `List<Dtc>`.

**Risk: hex-ASCII vs binary at the SPP level.**  
The LocalSocket IPC sent hex-ASCII lines.  We don't know if the NDK bridge:  
  (a) sent raw binary to the BT socket (most likely), or  
  (b) also hex-encoded to the BT socket.  
Set `VciSocketClient(useHexEncoding = true)` to test option (b).  Both modes are implemented.

**Risk: header magic bytes.**  
If bytes[0-1] of the frame are not `0x55 0xAA`, the VCI will silently discard every frame.  The header is only used for framing — if the VCI rejects frames, try:
- `0x55 0xAA` (default in code, common in Launch tools)
- `0xAA 0x55` (byte-swapped)
- `0x00 0x00` (empty header, some devices ignore it)
- Capture one real frame with Frida to confirm.

**Risk: OBD-II passthrough may not be direct.**  
The X431 VCI may require a proprietary "start diagnostic session" frame before it will forward OBD-II Mode 03 to the bus.  If `readDtcs()` times out with no response, the handshake is likely missing.

---

## What the 2-Week Build-Out Spike Needs

### Week 1: Protocol confirmation

| Task | Tool | Time |
|------|------|------|
| Frida hook `LocalSocketClient.send(byte[])` — log raw hex of every sent frame | Frida on rooted/debuggable APK | 2 hrs |
| Frida hook `LocalSocketClient.recv()` — log every received frame | Same session | 1 hr |
| Map 5-10 FEEDBACK_* constants to wire opcodes by triggering each function in X431 UI | Manual X431 operation + Frida log | 4 hrs |
| Confirm header magic bytes | First line of Frida log | Immediate |
| Confirm hex-ASCII vs binary at SPP level | Check Frida log format | Immediate |

**Frida one-liner for the hook:**
```javascript
Java.perform(() => {
  const LSC = Java.use('com.cnlaunch.diagnosemodule.utils.LocalSocketClient');
  LSC.send.implementation = function(buf) {
    const hex = Array.from(buf).map(b => ('0' + (b & 0xFF).toString(16)).slice(-2)).join('');
    console.log('SEND>> ' + hex);
    return this.send(buf);
  };
  LSC.recv.implementation = function() {
    const result = this.recv();
    console.log('RECV<< ' + result);
    return result;
  };
});
```

### Week 2: Build working DTC read + gap fills

| Task | Time |
|------|------|
| Update `VciFrame.DEFAULT_HEADER` with confirmed magic bytes | 30 min |
| Promote inferred opcodes to confirmed in `VciOpcodes.kt` | 1 hr |
| Fix `useHexEncoding` flag based on SPP-level observation | 30 min |
| Add `HANDSHAKE_INIT` frame to `VciSocketClient.connect()` flow | 2 hrs |
| Wire `VciCommunicator` into `EngineDriver` as an alternative backend | 4 hrs |
| Unit tests for `parseDtcPayload` / `parsePidResponse` (no Android needed) | 2 hrs |
| Integration test on real VCI + OBD-II vehicle | 4 hrs |

**Total estimate for "reads DTCs reliably on a real car": 2-3 days of focussed work, assuming Frida session is completed first.**

---

## Unknown Opcodes That Need Runtime Packet Capture

The following functions in the decompile map to frame types but their wire opcodes are unknown.  These are the capture targets ranked by priority for the app's core value prop:

| Priority | Function | DiagnoseConstants key | Why Capture |
|----------|----------|-----------------------|-------------|
| 1 | Read stored DTCs (any ECU) | `FEEDBACK_FAULTCODES = "27"` | Core feature |
| 2 | Clear DTCs | `ALERT_OK_COMMAND = "00"` after DTC screen | Core feature |
| 3 | Live datastream | `FEEDBACK_DATASTREAM = "18"` | Core feature |
| 4 | Auto full scan | `UI_TYPE_FAULTCODE = "700"` | Core feature |
| 5 | Read VIN | `FEEDBACK_GET_VIN = "48"` | Context for AI |
| 6 | Active test trigger | `FEEDBACK_ACTIVITYTEST = "9"` | Premium feature |
| 7 | Freeze frame | `FEEDBACK_FREEZEFRAME = "14"` | Premium feature |
| 8 | Special function | `FEEDBACK_SPECIA_FUNCTION = "36"` | Premium feature |

---

## Feasibility Verdict

**Brutally honest:**

OBD-II passthrough (Mode 01/03/04) is **very likely to work within 1 week** once the header magic bytes are confirmed.  The VCI is fundamentally an OBD-II bridge and generic mode 03 frames are unlikely to need proprietary session setup.

Multi-system proprietary scan (reading ABS/SRS/TCM DTCs like X431 does) is a **2-4 week additional effort** after Frida capture, because each system uses a different diagnostic protocol (ISO 14229 UDS, KWP2000, ISO 15765 CAN) that the VCI handles via the `.so`-encoded vehicle database.  Reimplementing this from scratch is a large project; the spike approach should focus on capturing the CNLaunch framing for 2-3 common systems and reverse-engineering those.

**The 1-week DTC-read goal is achievable for OBD-II generic codes.  Full multi-system scan is not.**

---

## Files Delivered

```
drafts/F10-VCI-SPIKE/
├── vci/
│   ├── VciFrame.kt          # Wire frame model, encode/decode/checksum
│   ├── VciOpcodes.kt        # Opcode catalogue (inferred + confirmed)
│   ├── VciSocketClient.kt   # BT SPP connection, Flow<VciFrame>
│   └── VciCommunicator.kt   # High-level API: readDtcs/clearCodes/fullScan/livePid/actuate
└── SPIKE-REPORT.md          # This file
```

**Line count:** VciFrame ~180, VciOpcodes ~200, VciSocketClient ~210, VciCommunicator ~300 = ~890 lines total.

---

*End of spike report.*
