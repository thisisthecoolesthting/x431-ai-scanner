# SONNET FINAL BUILD DIRECTIVE — Together Scanners AI

**To Cursor:** for everything from here on, **switch to Sonnet 4.5** in your model picker. The work below is code-heavy, multi-file, judgment-sensitive — Sonnet is the right tool. Haiku for sweeps; Sonnet for build-out.

## Goal (one sentence)

Ship a fully detached, X431-independent Together Scanners AI APK that opens directly into a single working screen — no wizard, no fallback, no "Take over X431" dialog — and connects to the VCI dongle over USB OTG or Bluetooth.

## Execution order — STRICTLY SEQUENTIAL

You may NOT skip ahead. Each task must merge to main green before the next one starts.

### 1. Task 203 — Fix the LAN file-transfer server
`cursor-dispatch/outbox/203-fix-file-transfer-server.prompt.md` — Ricky tested task 200's LAN export; the URL/IP the app displays doesn't open from his PC. **The transfer is the bottleneck** for getting the cnlaunch data folder over, which is the data the rest of the app needs. Fix this first.

### 2. Task 204 — Single-screen detached UI
`cursor-dispatch/outbox/204-single-screen-detached-ui.prompt.md` — kill the setup wizard, kill the overlay-mode CTAs, kill the bubble, kill the X431 auto-launch entirely. Together opens to ONE screen that is the app. Direct VCI is the default, not an experimental flag. Phase 1 overlay code stays in the tree as dead code or behind a hidden dev flag for forensic purposes, but is **never reached** in normal use.

### 3. Notify Ricky → he ships data
When 204 is merged, post in chat: **"Single-screen detached UI shipped — task 203 server is up — ready for the cnlaunch data folder."** Ricky will use the now-working LAN transfer to upload `/sdcard/cnlaunch/` from the tablet.

### 4. Task 205 — cnlaunch data integration (auto-triggers when data arrives)
`cursor-dispatch/outbox/205-cnlaunch-data-integration.prompt.md` — when the upload completes, the receiving end drops the zip at a known path. A watcher script (you write it as part of 205) extracts, indexes, and integrates the DTC dictionaries + menu trees into the app. **Do not start 205 until 204 has merged AND Ricky confirms data uploaded.**

## Model picker

- Tasks 203, 204, 205 → Sonnet 4.5 (interactive in Composer + Codex CLI sessions where parallelism helps)
- Mechanical sub-tasks inside any of those (find-replace, file moves, asset crawls) → Haiku
- Never Opus from here on — too expensive for the remaining work

## House rules still apply

`.cursor/rules/together-rules.md` — version pins, never-touch list, CI workflow, commit style. Re-read it if you haven't lately.

## When all three tasks ship green

Together Scanners AI is a real product:
- Boots directly to the diagnostic dashboard
- No X431 anywhere in the user experience
- VCI connects over USB or Bluetooth, your choice
- Vehicle data (DTC descriptions, menu trees) loaded from the cnlaunch bundle
- AI features (next-test, correlator, voice, recall flag, repair-story PDF) all work
- Ships behind no flag — it just works

Then hand back to Ricky for go-to-market.

---

**Stop reading. Go to task 203.**


---

# URGENT ADDENDUM — READ FIRST

Ricky reports recent builds still default to Bluetooth and never offer USB. Task 202 (USB OTG transport) was filed but missing from the Sonnet directive's order. Fixing now.

## Corrected execution order

0. Task 201 — standalone bugs (CTA, live ticker, VCI no-connect diagnostics). This builds the diagnostics surface task 202 plugs into.
1. Task 202 — USB OTG VCI transport. MANDATORY. Many techs cannot Bluetooth-pair the VCI dongle. USB cable must:
   - Show Android "Open Together with this USB device?" prompt on attach
   - Connect via USB serial (CDC-ACM / FTDI / CH340 / PL2303 / CP21xx — usb-serial-for-android covers all)
   - Appear in the Connection drawer transport picker as "USB" alongside "Bluetooth"
   - Be tried FIRST in Auto mode
2. Task 203 — Fix LAN file-transfer server
3. Task 204 — Single-screen detached UI. Connection drawer MUST expose both USB and Bluetooth.
4. Task 205 — cnlaunch data integration (triggered when Ricky uploads)

## Why 202 is non-negotiable

Cable is the reliable workshop connection: no pairing, no dropouts, faster, works on broken-BT tablets. You may not ship 204 without 202 merged.

If 203/204 already shipped without 202, do not roll back. Land 202 next and follow-up the Connection drawer to add the USB row.

---



---

# URGENT v2 — USB CABLE IS PRIMARY, BLUETOOTH IS OPT-IN

Per Ricky: the **USB OBD cable** (task 206) is the **first** transport the app tries and the default. Bluetooth is a secondary path that the tech must EXPLICITLY enable.

## Revised execution order

0. Task 201 — standalone bugs + diagnostics surface
1. **Task 206 — ELM327 USB-OBD cable (PRIMARY transport).** First thing the app tries. Default on Connection drawer.
2. Task 202 — Launch VCI USB OTG (secondary USB path for techs who own the Launch dongle).
3. Task 203 — Fix LAN file-transfer server.
4. Task 204 — Single-screen detached UI. Connection drawer ordering: USB OBD Cable → Launch VCI (USB) → Bluetooth (collapsed, requires toggle).
5. Task 205 — cnlaunch data integration (triggered when Ricky uploads).

## Bluetooth UX rule

Bluetooth is NOT auto-attempted. Behavior in Connection drawer:

- Top of drawer: USB transports (active by default).
- Below USB: a section labeled **"Bluetooth (optional — requires pairing)"** with a single toggle.
- Toggling Bluetooth ON does this exact UX:
  1. Pop a one-time explanation: "Pairing happens in Android's Bluetooth Settings, not inside Together. Tap Continue to open Bluetooth settings."
  2. "Continue" → fire `Intent(Settings.ACTION_BLUETOOTH_SETTINGS)`.
  3. Tech pairs the VCI / ELM327 over there.
  4. Tech returns to Together. The drawer now lists bonded devices Together recognizes — they pick one.
- If the toggle is OFF, the app NEVER scans for Bluetooth devices and NEVER attempts a BT connection.

This means a tech with a USB cable just plugs in and works. A tech wanting Bluetooth has to take an extra deliberate step (toggle + pair) — exactly Ricky's spec.

## If 204 already shipped without 206

204 shipped without USB cable support. Don't roll back. Land 206 next as the new primary, then a small follow-up to Connection drawer ordering + the Bluetooth opt-in toggle.

---

