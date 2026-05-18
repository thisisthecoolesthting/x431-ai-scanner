# Task 202 — USB OTG transport for VCI (in addition to Bluetooth)

Real-world need: many techs can't get the VCI Bluetooth pairing to work. Launch VCIs support USB connection too — plug the VCI dongle directly into the tablet via a USB OTG cable. We must support both paths in standalone (Direct VCI) mode.

## What to build

A second `VciTransport` implementation that uses Android's USB Host API + a USB serial library to read/write VciFrames over USB CDC-ACM (or FTDI/CH340/PL2303 — whatever chip the VCI uses internally).

Make the transport pluggable: `VciCommunicator` holds a `VciTransport` interface, and either `VciSocketClient` (Bluetooth SPP) or new `VciUsbClient` can be plugged in.

## Files

CREATE:
- `app/src/main/kotlin/com/caseforge/scanner/vci/VciTransport.kt` — common interface (`connect`, `disconnect`, `sendFrame(VciFrame)`, `frames: Flow<VciFrame>`, `connectionState: StateFlow<...>`).
- `app/src/main/kotlin/com/caseforge/scanner/vci/VciUsbClient.kt` — USB OTG transport implementing `VciTransport`. Use the `com.github.mik3y:usb-serial-for-android:3.7.3` library (Gradle: maven { url 'https://jitpack.io' } already present in `settings.gradle.kts`). Supported drivers: CDC-ACM, FTDI, Prolific, CH340, Silicon Labs CP21xx — that covers ~every Launch VCI chip variant.
- `app/src/main/res/xml/usb_device_filter.xml` — vendor/product ID filter for known Launch VCI chips. Start permissive (match any USB serial device), then narrow once we know which VID/PID Launch uses (check `lsusb` from a phone or read from decompiled X431).

MODIFY:
- `app/src/main/kotlin/com/caseforge/scanner/vci/VciSocketClient.kt` — extract its public surface into the new `VciTransport` interface; keep the SPP impl behind that.
- `app/src/main/kotlin/com/caseforge/scanner/vci/VciCommunicator.kt` — accept any `VciTransport`; not specifically Bluetooth.
- `app/src/main/kotlin/com/caseforge/scanner/ui/diag/VciDiagnosticsScreen.kt` (from task 201) — add a "Transport" picker: Auto / USB / Bluetooth. Auto = try USB first, fall back to Bluetooth.
- `app/src/main/AndroidManifest.xml` — add `<uses-feature android:name="android.hardware.usb.host" android:required="false" />` and a `<intent-filter>` on MainActivity for `android.hardware.usb.action.USB_DEVICE_ATTACHED` so the system surfaces our app when the VCI is plugged in.
- `app/build.gradle.kts` — add `implementation("com.github.mik3y:usb-serial-for-android:3.7.3")`.

## Pairing-free workflow

1. Tech plugs VCI into tablet via USB OTG cable.
2. Android shows the standard "Open Together with this USB device?" dialog. Tech taps OK.
3. Together auto-opens to the Direct VCI mode if `directVciExperimental` is on. Otherwise it shows a one-shot "VCI detected on USB — switch to standalone mode?" prompt.
4. Connection is instant; no pairing dance.

## Acceptance

- Plug VCI into tablet via OTG → Together detects + connects without Bluetooth.
- `VciDiagnosticsScreen` shows USB transport pass/fail; can manually switch transport.
- Mode 03 DTC read works over USB on a real vehicle without X431 running and without Bluetooth pairing.
- Bluetooth path still works for techs that DO have pairing working.
- Auto-transport tries USB first (if device present), Bluetooth fallback (if bonded device matches).

## Done

Branch `feat/vci-usb-transport`, self-merge on CI green. Move this prompt to `cursor-dispatch/done/`. Update the autonomy law in CLAUDE.md if relevant changes touch it (likely not).
