# Task 206 — ELM327 USB-OBD cable transport (PRIMARY transport)

Ricky wants the tablet → car's OBD-II port via a USB cable that looks like a serial port. That's an ELM327-style USB-OBD adapter (FTDI/CH340/PL2303/CP21xx). Standard ELM327 AT commands, no Launch VCI, no Bluetooth. This is now the **PRIMARY** transport — first thing the app tries, default in Auto.

## Different from task 202

- 202 = Launch VCI dongle over USB OTG (proprietary VciFrame protocol).
- 206 = Generic ELM327 USB-OBD cable (standard SAE J1979 / ELM327 commands).

Two separate transports. Both live in the Connection drawer.

## Reuse what exists

`app/src/main/kotlin/com/caseforge/scanner/agent/ObdBluetoothTool.kt` already speaks ELM327 over Bluetooth. Reuse the command/parse logic — only the transport changes.

## Build

CREATE:
- `vci/transport/UsbSerialTransport.kt` — generic USB serial wrapper using `com.github.mik3y:usb-serial-for-android` (already added in 202). Byte-level read/write + `Flow<ByteArray>`. Both 202's VciUsbClient and this task's ELM327 path consume it.
- `agent/ObdElmEngine.kt` — extracted shared ELM327 command/parse logic.
- `agent/ObdUsbTool.kt` — twin of ObdBluetoothTool, over UsbSerialTransport.
- `engine/ObdEngineDriver.kt` — implements `EngineDriver` interface routing through ObdElmEngine. So all of Together's downstream features (predictive next-test, correlator, voice, recall flag, repair-story PDF) keep working with ELM327 data.

MODIFY:
- `agent/ObdBluetoothTool.kt` — delegate parsing to `ObdElmEngine` (no behavior change).
- `vci/VciUsbClient.kt` — switch its serial layer to UsbSerialTransport.
- `ui/main/MainScreen.kt` + `ui/main/ConnectionDrawer.kt` — add "USB OBD Cable" row (primary, listed first).
- `res/xml/usb_device_filter.xml` — add ELM327 chip VID/PIDs: FTDI 0403:6001, CH340 1a86:7523, PL2303 067b:2303, CP21xx 10c4:ea60.
- `AndroidManifest.xml` — USB host intent filter covers new VID/PIDs.

## Auto-detect order on USB attach

1. Try Launch VciFrame handshake (~1 s timeout). If valid → VciUsbClient.
2. Else send `ATZ`. If response contains "ELM327" → ObdUsbTool.
3. Else surface as unknown serial device with manual transport picker.

## ELM327 init sequence

```
ATZ ; ATE0 ; ATL0 ; ATS0 ; ATH0 ; ATSP0 ; 0100
```

Then SAE J1979 modes: `03` (DTCs), `04` (clear), `01 XX` (live PID), `09 02` (VIN, multi-frame).

## Acceptance

- Plug generic ELM327 USB-OBD cable from car → tablet → Together connects without pairing or Bluetooth.
- Mode 03 returns DTCs. Mode 01 live PIDs render. Mode 09 returns VIN.
- AI features keep working on ELM327 EngineState.
- Connection drawer: USB OBD Cable / Launch VCI (USB) / Launch VCI (Bluetooth) / ELM327 (Bluetooth) — Auto-detect in that order.
- CI green on `feat/elm327-usb-cable`. Self-merge.

## Done

Move to `cursor-dispatch/done/`. After 206 lands, Together connects via any combination of (Launch VCI / generic ELM327) × (USB / Bluetooth). Four transports. Tech picks what they have.

# Notes from Cursor

- **PR #13** squash-merged to `main` at `2a49ef5` (CI green).
- Shipped: `UsbSerialTransport`, `ObdElmEngine`, `ObdUsbTool`, `ObdEngineDriver`, `DiagnosticConnector` (Auto = ELM327 USB → Launch USB → optional BT).
- Connection drawer: USB primary, transport chips, Bluetooth opt-in toggle → pairing dialog → `ACTION_BLUETOOTH_SETTINGS`.
- `bluetoothTransportEnabled` defaults **false** — no BT scan/connect until toggled.
- Tablet smoke on real ELM327 USB cable still operator-owned.
