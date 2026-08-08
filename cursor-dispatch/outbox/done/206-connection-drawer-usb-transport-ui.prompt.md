# Task 206 — Connection drawer: USB + Bluetooth transport picker

**Depends on:** task 202 merged (`feat/vci-usb-transport`).

## Goal

[ConnectionDrawer.kt](app/src/main/kotlin/com/caseforge/scanner/ui/main/ConnectionDrawer.kt) still shows a single Connect button with Bluetooth-only copy. Expose the same transport choices as diagnostics:

- **Auto** — USB first, then Bluetooth (default; matches `SettingsRepo.vciTransportMode`)
- **USB** — OTG cable only
- **Bluetooth** — bonded SPP only

Show attached USB device count when present. On Connect, pass pending [VciUsbAttachState](app/src/main/kotlin/com/caseforge/scanner/vci/VciUsbAttachState.kt) device into [StandaloneVciController](app/src/main/kotlin/com/caseforge/scanner/ui/main/StandaloneVciController.kt) / [DirectVciSession.ensureConnected](app/src/main/kotlin/com/caseforge/scanner/vci/DirectVciSession.kt).

## Done-when

- Main screen connection drawer shows Auto / USB / Bluetooth chips.
- Connect uses `VciConnector` with selected mode.
- Copy no longer says "USB in task 202".

Branch `feat/connection-drawer-usb`, self-merge on CI green.
