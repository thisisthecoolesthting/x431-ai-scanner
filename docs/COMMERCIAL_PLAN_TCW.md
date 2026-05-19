# Together Car Works — Commercial Release Plan

Product: **Together Car Works** (TCW)
Repo: `C:\Users\reasn\Documents\Claude\Projects\DEv1\_x431-work` (`github.com/thisisthecoolesthting/x431-ai-scanner`)
Package id (frozen for in-app upgrades): `com.caseforge.scanner`
Hardware target: 10" Android tablets shipped with OEM diagnostic stacks (API 24+; tested on API 28/30/34).

Rebrand law that governs every section below:

- Every user-visible string and class/file/folder/branch must read **Together Car Works** / **TCW**. No "Launch", "X431", "x431", "cnlaunch", "CaseForge", "caseforge" survives anywhere except the single private constant `OEM_DATA_PATH = "/sdcard/cnlaunch/"` (and equivalent candidate paths) inside `VehicleDatabasePathResolver`.
- Branch names: `feat/...` or `fix/...`, never containing `launch` or `x431`.
- Launcher icon contains no "X" mark.
- Package id stays `com.caseforge.scanner` so in-app updates keep working; release notes for first 1.0 build promise "future major version will migrate package id with a one-time reinstall."

---

## A. Product positioning

Together Car Works is an AI-driven OBD-II scanner for working technicians. Plug in the included USB OBD cable (or pair an ELM327 Bluetooth dongle, or use the OEM VCI when the tablet has one), tap **Scan**, and the app reads every controller it can reach, looks up each DTC offline first, drafts a plain-English repair story with likely root causes and tests, and lets you hand the customer a branded PDF or text the link directly. Built for shop reality: works without cell signal, recovers cleanly when the cable wiggles, tells you exactly why it failed when something goes wrong, and never blames the technician.

Differentiators:

- **AI repair-story PDF** from every scan: DTCs, freeze frame, top 3 likely causes, tests in order, parts list, customer-facing summary.
- **Offline DTC dictionary** for generic + most OEM ranges so the scanner gives a useful answer in a no-signal bay.
- **Multi-transport** with USB OBD as primary, OEM VCI USB/Bluetooth opt-in for live-data depth on supported brands, and ELM327 Bluetooth as the no-cable fallback.
- **Shop hand-off LAN export** — one tap pushes the OEM vehicle database to the office PC so the AI can train against real cars without anyone touching cables again.
- **Voice notes per session** captured under the hood (tech can dictate "1996 Camry, no start, fuel pressure 38") — attached to the report and searchable.
- **Recalls + TSB lookup** auto-fires the moment a VIN appears, surfaced on the home card.
- **Quick share**: SMS, email, or save-as-PDF the report; customer never needs the app.
- **Visible everything** — every action shows a spinner, progress bar, or ticker; nothing ever "just sits there."

---

## B. Information architecture / screen map

Persistent top bar on every screen (slot order, left to right): **Transport pill** (`USB OBD` / `OEM USB` / `OEM BT` / `ELM327 BT` / `—`, colored), **VIN chip** (tap to copy / re-scan), **Battery V** (live `12.6 V` from ATRV when ELM path; `—` otherwise), **Busy spinner** (small indeterminate ring, only renders when something is in flight), **Build sha** (tiny, right-aligned, tap to open Diagnostics). Below it, a one-line **status ticker** (re-uses `LiveActivityTicker`) so the user always knows what the app is doing.

| Screen | Route | Primary action | In-flight indicators |
|---|---|---|---|
| **Home** | `home` | "Connect" or "Scan" (whichever is next) | Connect dot-pulse, scan linear progress with module-count, ticker mirrors agent activity |
| **Connection drawer** | bottom sheet from any screen | "Connect" / "Disconnect" | Probe spinner, per-transport status dots (USB, OEM USB, ELM BT, OEM BT) |
| **Scan results / Report** | `report` | "Generate Repair Story" + "Share PDF" | DTC count tag, AI-think dot pulse, PDF generation linear bar |
| **Live Data** | `live_data` | "Add PID" / "Record" / "Stop" | Per-tile sparkline, refresh ms counter, record dot blinks |
| **Service / Resets** | `service` | "Run reset" (per service) | Step-by-step progress card, abort button |
| **Bidirectional** | `bidirectional` | "Activate test" | Active-state pill, 5-sec safety timer ring |
| **History** | `history` | "Open session" / "Share" | Lazy-load shimmer, share progress |
| **Recalls / TSB** | `recalls` | "Lookup VIN" | NHTSA call spinner, retry banner |
| **Manual VIN / YMM** | `vehicle_profile` | "Save profile" | VIN-decode spinner, recalls fan-out |
| **Settings** | `settings` | per-row save | Per-toggle saved-checkmark blink |
| **Data Transfer** | `export_data` | "Health-check PC" then "Send" | 6-step state ticker (see §D), upload MB/s + ETA |
| **Update Center** | `updates` | "Check now" / "Install" | Download bar with MB/MB, install modal with PackageInstaller status |
| **Diagnostics** | `vci_diagnostics` | "Run probe" | Per-probe row with green/red, copyable log |
| **Transfer Log** | `transfer_log` | "Copy" / "Email" | Rolling 500-line buffer; auto-scrolls when active |
| **About** | `about` | "Open GitHub release" | none |

Out-of-shell flows that still get progress UI: Bluetooth permission grant (chip turns amber → green), MANAGE_EXTERNAL_STORAGE grant (banner persists until granted, then auto-rescans).

---

## C. Visual design system

### Palette

Light theme:

| Token | Hex | Use |
|---|---|---|
| `tcw.bg` | `#F4F6F8` | App background |
| `tcw.surface` | `#FFFFFF` | Cards, sheets |
| `tcw.surfaceElev` | `#ECEFF3` | Pressed state, drawer |
| `tcw.ink` | `#0F1620` | Primary text |
| `tcw.inkSubtle` | `#4A5466` | Secondary text |
| `tcw.line` | `#D6DCE5` | Dividers, outlines |
| `tcw.primary` | `#0B5FFF` | Brand action (Together blue) |
| `tcw.primaryHover` | `#0A55E6` | Pressed action |
| `tcw.success` | `#10A26A` | Connected, voltage OK |
| `tcw.warn` | `#E0A500` | Slow link, low voltage |
| `tcw.danger` | `#D63B3B` | Errors, disconnect, DTC red badge |
| `tcw.accent` | `#FF7A1A` | Wrench-mark amber (highlights, "Together Car Works" wordmark dot) |

Dark theme:

| Token | Hex |
|---|---|
| `tcw.bg` | `#0B0F14` |
| `tcw.surface` | `#141A22` |
| `tcw.surfaceElev` | `#1B232C` |
| `tcw.ink` | `#EAEEF4` |
| `tcw.inkSubtle` | `#A6B0BD` |
| `tcw.line` | `#2A323D` |
| `tcw.primary` | `#5C90FF` |
| `tcw.primaryHover` | `#779FFF` |
| `tcw.success` | `#3FCB8E` |
| `tcw.warn` | `#F2C84B` |
| `tcw.danger` | `#FF6262` |
| `tcw.accent` | `#FF9B4A` |

### Typography

- Display / titles: **Inter Tight** SemiBold 22–28sp
- Body: **Inter** Regular 14–16sp
- Data / VIN / DTCs / voltage: **JetBrains Mono** Medium 14–18sp (numeric tabular for stable columns)

Fallback to system `sans-serif` and `monospace` when fonts can't load (Compose `FontFamily` declared in `ui/theme/Typography.kt`).

### Spacing scale

`4 · 8 · 12 · 16 · 20 · 24 · 32 · 48 dp`. Cards padding 16, list rows 12, sheet padding 20 horizontal / 8 vertical.

### Component vocabulary

- **Status pill** (small, 24 dp tall, 8 dp radius, colored): transport, VIN, battery V, connection state.
- **Action tile** (square-ish, 88 dp min height): icon top-left, title, one-line subtitle, optional progress underline.
- **Progress card**: title row + linear bar + footer line ("412 / 580 MB · 12.4 MB/s · ETA 0:14"). Used for transfer + update.
- **Error banner** (`tcw.danger` background tint, white text, dismiss "X", optional one-tap remediation button on right).
- **Ticker** (full-width 28 dp tall, scrolls when text > width, dot-pulse on left while text is mutating).
- **Transport chip** (filter chip with icon: USB ⚡, OEM ⚙, BT 🅑, ELM 🅔 — implemented as drawables, not emoji).
- **Empty state card** (illustration + 1-line title + 1-line subtitle + one outlined CTA).

### Motion vocabulary

- **Indeterminate spinner**: `CircularProgressIndicator` inline 16 dp next to its label whenever the operation has no known total.
- **Linear bar with known progress**: `LinearProgressIndicator(progress)` for any operation with known totals (zip, upload, download, install). Always include the percent + raw fraction text under the bar.
- **Dot-pulse** ("alive"): three 6 dp dots animating opacity 0.3 → 1.0 in 600 ms; rendered inside the ticker when AgentStatus is non-empty.
- **Marquee ticker**: `LiveActivityTicker` scrolls when text overflows; pauses 1.5 s at start, scrolls 60 dp/s, pauses 1.5 s at end, loops.
- **State chip flash**: 250 ms scale 1.0 → 1.08 → 1.0 on transport pill when state changes.

Rule applied universally (per operator decree): no button stays unresponsive; every long action gets either an inline spinner, a linear bar, a dot-pulse, or a ticker update — and ideally two of them.

### Brand mark + launcher

Wordmark: **TOGETHER CAR WORKS** in Inter Tight SemiBold, all caps, tight tracking. Above it, a small mark: **two overlapping wrenches forming a "T"** (no gears, no "X"), one wrench `tcw.primary` blue, the other `tcw.accent` amber, with a `tcw.ink` 2 dp stroke. Square corners. App icon: same mark on `tcw.surface` background; adaptive icon foreground = mark, background = solid `tcw.primary`.

### Assets to produce (each line is a generator prompt)

| File | Prompt |
|---|---|
| `res/mipmap-mdpi/ic_launcher.png` (48) | "Square app icon, flat vector, two overlapping wrenches forming a stylized letter T, one wrench in cobalt blue (#0B5FFF) and the other in amber (#FF7A1A), thin dark ink outline (#0F1620), solid off-white background (#F4F6F8), centered, no text, 48x48 px, crisp edges, no gradients" |
| `res/mipmap-hdpi/ic_launcher.png` (72) | same prompt, 72x72 px |
| `res/mipmap-xhdpi/ic_launcher.png` (96) | same prompt, 96x96 px |
| `res/mipmap-xxhdpi/ic_launcher.png` (144) | same prompt, 144x144 px |
| `res/mipmap-xxxhdpi/ic_launcher.png` (192) | same prompt, 192x192 px |
| `res/drawable/ic_launcher_foreground.xml` (adaptive vector) | "Vector drawable, 108x108 viewport, two-wrench T mark centered in middle 72x72, no background, two color layers (#0B5FFF, #FF7A1A) with 2px dark outline (#0F1620)" |
| `res/drawable/ic_launcher_background.xml` | "Vector drawable, 108x108 solid color rectangle fill #0B5FFF" |
| `res/drawable/ic_notification.xml` (monochrome) | "Vector drawable, 24x24, single white silhouette of the two-wrench T mark, transparent background, for Android status bar" |
| `res/drawable/splash_logo.xml` | "Vector drawable, 320x120, two-wrench T mark on left (60x60) plus wordmark 'TOGETHER CAR WORKS' next to it in Inter Tight SemiBold dark ink, transparent background" |
| `res/drawable/empty_no_vci.png` | "Friendly minimal line illustration, 240x160, OBD-II port on a car dashboard with no cable plugged in, blue + amber two-color palette matching app, white background, no text" |
| `res/drawable/empty_no_db.png` | "Minimal line illustration, 240x160, empty tablet shelf with a small folder icon and a downward arrow suggesting 'load data', blue + amber palette, white background, no text" |
| `res/drawable/empty_receiver_offline.png` | "Minimal line illustration, 240x160, desktop PC with offline cloud icon and a small wifi-broken glyph, blue + amber palette, white background, no text" |
| `res/drawable/empty_no_dtcs.png` | "Minimal line illustration, 240x160, a clipboard with a single green checkmark, blue + amber palette, white background, no text, conveys 'no codes found'" |
| `res/drawable/empty_update_available.png` | "Minimal line illustration, 240x160, an app icon with a downward arrow and a small sparkle, blue + amber palette, white background, no text" |
| `res/drawable/empty_update_success.png` | "Minimal line illustration, 240x160, a green check inside a soft rounded square, with confetti-like accents in amber, blue + amber palette, white background, no text" |

---

## D. P0 — Data transfer redesign (must actually work)

### Problem inventory (root causes seen in shop)

1. `lan-export-receiver.ps1` not running on the PC, or running on a different shell window with no port-bind log.
2. Windows Defender Firewall silently blocks inbound 8765.
3. Router has Wi-Fi client isolation enabled (tablet on guest SSID).
4. Tablet on different subnet (cellular, hotspot, or VLAN).
5. `MANAGE_EXTERNAL_STORAGE` not granted → `walkTopDown` returns 0 files → empty zip → multipart body 22 bytes → PC accepts 200 OK → operator believes it worked.
6. Multipart body silently truncated on slow Wi-Fi; OkHttp reports success because PC closed the socket.
7. Hardcoded IP `192.168.1.129` doesn't match the actual PC.

### Pre-flight health probe

Before any upload, send `GET http://{receiverIp}:{port}/health` with 3 s connect / 5 s read timeout. Response body shape:

```
{"ok":true,"name":"TCW Receiver 1.2","savePath":"D:\\TCWBundles","freeBytes":482103296,"version":"1.2.0"}
```

Render a **PC pill** in the send card: green dot + "PC ready · D:\TCWBundles · 482 GB free · 38 ms" / red dot + remediation hint. Re-poll every 30 s while the card is visible.

### Receiver bootstrap improvements (`scripts/lan-export-receiver.ps1`)

- Auto-bind to all interfaces (`http://+:8765/`). On `HttpListenerException` (port in use, requires URL ACL), print the exact `netsh http add urlacl` command.
- Print the listening bind (`LISTENING on 0.0.0.0:8765`) + `savePath` to stdout and to `%TEMP%\tcw-receiver.log` (rolling 1 MB).
- Allowlist hint: detect if Windows Firewall Public profile blocks 8765 and print the exact `New-NetFirewallRule` line.
- Endpoints:
  - `GET /health` → JSON above.
  - `POST /upload?name=<filename>&sha256=<hex>&size=<bytes>` → raw stream (Content-Length required), writes one file directly to `savePath`, returns `{"ok":true,"path":"D:\\TCWBundles\\<filename>","bytes":<n>,"sha256":"<computed>"}`.
  - `POST /upload-multipart` (legacy) → keep working for older builds; same response shape.
  - `PATCH /upload?name=...&offset=<n>` → resumable chunk append (200 with updated bytes count, 416 if offset mismatch).
- Response body always plain JSON; never empty.
- Companion install script `scripts/install-tcw-receiver.ps1` registers an autostart Scheduled Task running PowerShell hidden so the operator never has to remember it.
- **Fallback raw-TCP receiver** at port 8766 (`scripts/tcw-raw-receiver.ps1`) — opens a `TcpListener`, reads first line as `name|size|sha256`, then dumps `size` bytes to disk and replies with same line. The tablet auto-falls-back if the HTTP upload fails twice with HTTP errors.

### Transfer protocol decision

**Primary: raw `POST /upload?name=...&size=...&sha256=...` with `Content-Length`** (not multipart). Reasons:

- Eliminates multipart boundary issues that have silently dropped tail bytes on flaky Wi-Fi.
- PC writes one zip directly to disk while reading the socket (no intermediate parsing).
- `Content-Length` is the contract; if the socket closes early, OkHttp's `RequestBody.writeTo` throws and we know we failed.
- SHA-256 is computed on the tablet during zip → sent in URL → re-hashed on PC, mismatch returns 422.

**Fallback: keep `MultipartBody` path** for back-compat with older receiver scripts, but degrade only with explicit operator opt-in in Settings.

### Resumable upload

When zip size > 200 MB or after a transient failure mid-upload:

- Tablet sends `HEAD /upload?name=...` first → receiver returns `{"have":<bytesOnDisk>}`.
- Tablet `PATCH /upload?name=...&offset=<have>` with the remaining bytes.
- On final write, receiver compares the streamed sha256 to the URL-supplied one; on mismatch, deletes file and returns 422.

### Permission flow

**Two paths — pick by API level, never skip the legacy one (Gemini caught this bug).**

On API 30+ (`Build.VERSION.SDK_INT >= R`):
1. On screen entry, call `VehicleDatabaseStorageAccess.needsAllFilesAccess()`.
2. If true, show a `Card` with plain copy: "Together needs **All files access** to read the vehicle databases the tablet's diagnostic app saved. Tap below to open Android settings, flip the switch for Together Car Works, then come back."
3. Single button: "Allow file access" → `ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION`.

On API ≤ 29:
1. Call `VehicleDatabaseStorageAccess.hasLegacyReadPermission(context)`.
2. If false, request `Manifest.permission.READ_EXTERNAL_STORAGE` via `ActivityCompat.requestPermissions()`. Card copy: "Together needs **Storage** access to read the vehicle databases the OEM diagnostic app saved on this tablet."
3. On grant, re-scan inventory and proceed.

When the screen resumes after any grant, re-scan; diff against last inventory and display `+12 files / +84 MB found` so the operator sees the grant actually helped.

### Visible state machine on the send card

A 6-step row of dots, each becoming active/complete/failed:

| # | State | Ticker text | What's animating |
|---|---|---|---|
| 1 | Checking PC | "Pinging PC at 192.168.1.129…" | Dot-pulse |
| 2 | Scanning files | "Scanning vehicle database (412 files, 580 MB)" | Indeterminate spinner |
| 3 | Zipping | "Zipping 312 / 412 files · 412 MB" | Linear bar w/ known total |
| 4 | Uploading | "Uploading 412 MB · 12.4 MB/s · ETA 0:14" | Linear bar w/ MB/s + ETA |
| 5 | Verifying | "Receiver re-hashing…" | Dot-pulse |
| 6 | Done | "Saved to D:\TCWBundles\tcw-bundle-2026-05-18-0853.zip · 412 MB · 38 s" | Green check |

Each row click-to-expand reveals raw transfer log lines from that step. **Retry** button persists after success so the operator can re-send to a different folder or after the customer arrives. Replaces the current behavior where the card hides retry after success.

### Transfer log

Add `TransferLog` ring buffer (500 entries) at `transfer/TransferLog.kt`; every step appends `[ISO ts] [stage] message`. New screen `ui/transfer/TransferLogScreen.kt` shows the log with **Copy all** (clipboard) and **Email to support** (`ACTION_SEND` text/plain) buttons. Also reachable from Diagnostics.

### Error mapping with one-tap remediation

| Symptom | Banner copy | Action button |
|---|---|---|
| `/health` returns network error | "PC receiver not reachable. Run the receiver on your PC or open Settings." | "Settings" |
| `/health` works but upload fails with timeout | "Wi-Fi is blocking the upload — likely client isolation. Switch tablet to the shop Wi-Fi (not guest)." | "Wi-Fi settings" |
| Firewall ECONNREFUSED on port | "PC is online but firewall is blocking port 8765. Allow it on the PC." | "Show fix command" (shows the exact `New-NetFirewallRule`) |
| `MANAGE_EXTERNAL_STORAGE` denied | "Together needs All files access to read the vehicle databases." | "Allow file access" |
| 0 files found after grant | "No vehicle databases on this tablet yet. Open the diagnostic app, connect to a vehicle once, then come back." | "Rescan" |
| Receiver subnet mismatch | "Tablet is on 10.0.0.x but PC is at 192.168.1.x — connect to the same Wi-Fi or update the PC IP." | "Edit PC IP" |
| Partial upload | "Upload interrupted at 312 MB / 412 MB. Resume from where we stopped?" | "Resume" |
| sha256 mismatch (422) | "Upload corrupted in transit. Retry?" | "Retry" |

### Settings additions

- **Receiver PC IP** — `TextField`, default `192.168.1.129`, persisted in `SettingsRepo.receiverPcHost`. Validates `IPv4` shape or hostname.
- **Receiver port** — default `8765`.
- **mDNS lookup** — button that calls `NsdManager.discoverServices("_tcw._tcp.")` if the receiver script registers itself (which it does in the new install script); on first hit, offers "Use 192.168.1.42 (officepc)?"

### Acceptance test (fresh install)

1. Sideload TCW APK on a wiped tablet that already has the OEM diagnostic app installed and one vehicle's database downloaded.
2. Run `scripts/install-tcw-receiver.ps1` once on a Win10/11 PC on the same Wi-Fi.
3. Grant Bluetooth + All Files Access when prompted.
4. Tap home-screen send card.
5. Expected: PC pill turns green within 5 s; 6-step state machine completes; final card shows "Saved to `D:\TCWBundles\tcw-bundle-<ts>.zip` · ≥100 MB · <60 s for 500 MB on 802.11ac"; the zip exists on the PC at the reported path with the reported size; sha256 matches.

---

## E. P0 — UI/UX fix list for shipping

Every fix below ships behind the universal **progress + ticker** rule.

### Rebrand sweep (sub-task of every lane; K2 owns the bulk)

- `res/values/strings.xml`: rename `app_name` to `Together Car Works`; rewrite `accessibility_service_description` and `accessibility_service_summary` to never name the OEM app (replace with "the vehicle diagnostic app" or "the OEM diagnostic app"); rename every `export_*` string with `cnlaunch` → "vehicle database"; receiver hints reference `scripts\lan-export-receiver.ps1` only, with no OEM brand.
- `AndroidManifest.xml`: `android:label="Together Car Works"` on `<application>` and `MainActivity`; remove any `android:label` referencing the OEM brand; rewrite all `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` values to remove "X431" / "Launch" wording; rename `Theme.CaseForge` → `Theme.TogetherCarWorks` (style stays the same; rename references repo-wide).
- `app/build.gradle.kts`: drop `caseforge.claudeApiKey` from `local.properties`, replace with `tcw.claudeApiKey`; keep both for one release to avoid breaking dev machines.
- Repo-wide grep target = zero remaining matches for `\b(Launch|X431|x431|cnlaunch|CaseForge|caseforge)\b` except:
  - `package com.caseforge.scanner` lines (package id stays).
  - The private constant `OEM_DATA_PATH = "/sdcard/cnlaunch/"` inside `VehicleDatabasePathResolver`.
- File/class renames per the mapping in the brief:
  - `transfer/CnlaunchPathResolver.kt` → `transfer/VehicleDatabasePathResolver.kt`
  - `transfer/CnlaunchStorageAccess.kt` → `transfer/VehicleDatabaseStorageAccess.kt`
  - `transfer/CnlaunchZipper.kt` → `transfer/VehicleDatabaseZipper.kt`
  - `transfer/CnlaunchQuickSend.kt` → `transfer/VehicleDatabaseQuickSend.kt`
  - `ui/transfer/CnlaunchOneTapSendCard.kt` → `ui/transfer/OneTapSendCard.kt`
  - `vci/VciSocketClient.kt` → `vci/BluetoothVciClient.kt` (and references)
  - `vci/VciUsbClient.kt` → `vci/OemUsbVciClient.kt`
  - `DiagnosticConnector.LinkKind.LAUNCH_USB/LAUNCH_BT` → `OEM_USB/OEM_BT`
  - `DiagnosticConnector.UserTransport.LAUNCH_USB/LAUNCH_BT` → `OEM_USB/OEM_BT`
  - `SettingsRepo.linkTransport` accepted aliases include `oem_usb`/`oem_bt`; keep `launch_*` accepted on read only, written-back as `oem_*` for back-compat with installed prefs.
- `ObdUsbTool.connect()` success message: `"USB OBD cable: $msg @ ${dev.deviceName}"` — already neutral; verify.
- `Updater.kt` user-facing strings replace OEM brand → "Together Car Works"; OkHttp `User-Agent` set to `Together-Car-Works/<versionName>`.
- PDF/report headers: `pdf/*.kt` (or `report/*.kt`) — wherever headers are rendered, brand title "Together Car Works · Vehicle Report"; footer line "tcw.io · build <sha>". No OEM brand anywhere on the PDF.
- GitHub workflow file `.github/workflows/build.yml`: artifact name `tcw-android-debug`, APK rename `tcw-${{ github.run_number }}.apk`, release body copy "Together Car Works build #N from <sha>". Keep tag name `latest` (Updater hardcodes it). Cache key renamed to `tcw-debug-keystore-v1`.

### Fix list, by screen

See full plan in earlier draft — every lane's executor prompt references its specific items.

### Additional fixes from Sonnet + Gemini reviews (must land before 1.0)

**Sonnet:**
- Service / Bidirectional gating: when **disconnected entirely**, tapping the tile must open the Connection drawer first (the same way Scan and Live Data do today). The OEM-required empty state is a *secondary* gate that renders after a non-OEM connection is established. Both gates must exist. Owner: C4.

**Gemini:**
- Legacy `READ_EXTERNAL_STORAGE` request on API ≤ 29 (covered in §D Permission flow above). Owner: K1 / C5.

---

## E.1 — Ignition state awareness (P0, called out explicitly)

Reading DTCs and live data on the wrong ignition state is one of the most common bay mistakes. The app must know the ignition state, surface it, and warn before an action that doesn't match.

### Detection (engine `engine/IgnitionMonitor.kt` — new file, owned by **C1**)

When a transport is connected, poll for ignition state every 2.5 s:

- **ELM327 path:** read PID `0x0C` (engine RPM, Mode 01). If `RPM > 250` → `EngineRunning`. If `RPM == 0` and the controller responds to AT commands → `KeyOnEngineOff`. If no response → `Unknown`.
- **OEM VCI path:** if a controller responds to module-presence probes but RPM is 0, `KeyOnEngineOff`. If RPM > 250, `EngineRunning`. Otherwise `Unknown`.
- Stop polling when disconnected; reset to `Unknown`.

Expose a `StateFlow<IgnitionState>` from `IgnitionMonitor`. States: `Unknown`, `KeyOnEngineOff`, `EngineRunning`, `EngineCranking` (RPM 50–250, transient — display as "Cranking…").

### Top-bar pill (owned by **C1**)

Next to the transport pill, render an **Ignition pill**:

| State | Pill color | Label |
|---|---|---|
| `Unknown` | gray | `—` |
| `KeyOnEngineOff` | blue | `Key ON · Engine OFF` |
| `EngineCranking` | amber | `Cranking…` |
| `EngineRunning` | green | `Engine running` |

Tap the pill → expand a small card with the 2-line guidance:

> Stored DTCs read best with **key on, engine off**.
> Live data, freeze frame, and pending DTCs need the **engine running**.

### Contextual gate before action (owned by **C2** for Scan + Report, **C3** for Live Data)

When the operator taps **Scan** while `EngineRunning`:

- Show a warning banner (amber, not red — they can proceed) inside the Scan dialog:
  "Engine is running. Stored DTCs read most reliably with **key on, engine off**. Turn the engine off, then tap Scan again — or continue anyway."
- Two buttons: **Scan anyway** and **OK, I'll turn it off**.
- If `Unknown`, no warning (don't nag when the data is missing).
- If `KeyOnEngineOff`, proceed silently.

When the operator taps **Live Data** while `KeyOnEngineOff`:

- Banner: "Engine isn't running. Most live PIDs will be zero or limited. Start the engine for full live data — or continue anyway."
- Buttons: **Continue anyway** and **OK**.
- If `EngineRunning`, proceed silently.

When the operator taps **Bidirectional** while `EngineRunning`:

- Banner: "Some actuator tests require engine off; some require engine running. Check the test description before activating."
- Single dismiss button.

### Home-screen hint card (owned by **C1**)

On the home screen, above the Action Tile grid, render a soft-info card **only when ignition is `Unknown`** (i.e. not yet connected or no RPM response):

> **Before you scan:** Key ON, engine OFF reads stored codes most reliably. Start the engine for live data and pending codes.

When ignition is known, the card hides (the top-bar pill does the job).

### Settings (owned by **C5**)

Add a toggle `Settings → Diagnostics → Show ignition warnings` (default `true`). When off, the contextual gates above proceed silently.

### Acceptance

- Connect via USB OBD cable on a real vehicle with engine off. Within 5 s, the Ignition pill turns blue `Key ON · Engine OFF`. The home hint card hides.
- Start the engine. Within 5 s, the pill turns green `Engine running`.
- Tap **Scan** while engine is running. The amber warning appears. Tap **Scan anyway** → scan proceeds.
- Tap **Live Data** while engine is off. The amber warning appears. Tap **Continue anyway** → live data proceeds (mostly zeros).
- Toggle the Settings switch off. Repeat — no warnings.

---

## E.2 — Speed + UI hardening pass (ChatGPT review additions)

ChatGPT's review mostly reinforced items already in the plan, but these concrete additions are worth landing before 1.0.

### Startup and perceived loading speed (owned by **C1**)

- Keep the Android splash lightweight: no database open, no network, no Bluetooth scan, no receiver probe before first frame.
- Render Home immediately with cached `SettingsRepo` values and last-known state: last VIN, last transport, last battery voltage, last receiver host.
- Defer heavy work until after first composition:
  - USB device inventory after Home is visible.
  - Receiver `/health` probe only when Data Transfer card/screen is visible.
  - Update check only when Update Center opens or a background throttle allows it.
  - Offline DTC dictionary index loaded lazily when Report/Scan first needs explanations.
- Acceptance: cold launch to interactive Home is under 2.5 s on the target tablet; no spinner blocks the first Home render.

### Connection speed rules (owned by **C1**)

- USB-first remains the default. Probe USB serial devices immediately and auto-connect to the most likely ELM327 cable.
- Do **not** auto-scan Bluetooth when `bluetoothTransportEnabled == false`. If enabled, probe bonded devices in parallel with OEM USB probing, but never block USB connect on Bluetooth.
- Cache the last-good transport and device address. Home primary CTA should be **Reconnect USB OBD** / **Reconnect ELM327 BT** when available.
- All transport probes emit a row-level state: `Waiting`, `Probing`, `Connected`, `Failed(reason)`, so the drawer never looks frozen.

### Function latency rules (owned by each feature lane)

- **Scan (C2):** start with Mode 03/07 fast pass for ELM, render DTCs as they arrive, then enrich with explanations/PDF in the background.
- **Live Data (C3):** cap visible PID polling to selected PIDs only; default to 6 high-value PIDs (RPM, coolant, speed, fuel trim short/long, battery voltage) to keep refresh smooth.
- **Report/AI (C2):** build the plain report immediately from local data; stream AI repair-story text into the card as it arrives; PDF export uses the latest completed text.
- **Transfer (K1/C8):** upload runs as a foreground/background-safe job; leaving the screen does not cancel unless the user taps Cancel.
- **Updater (K3/C6):** background update check is throttled to once per 12 hours and never blocks launch.

### UI state-machine hardening (owned by **C1**, then followed by all lanes)

Every user action must follow this state model:

`Idle → Confirm? → Running(progress/ticker) → Success(summary + next action) | Failed(reason + recovery)`

Rules:

- Disable the initiating button while `Running`, but keep **Cancel** visible for long operations.
- Never show only a toast for a state change. Toasts can supplement, not replace, the visible card/ticker.
- Success states stay actionable: **Scan again**, **Send again**, **Share**, **Open log**, **View report**.
- Failed states must include a remediation button: **Retry**, **Open settings**, **Switch transport**, **Copy log**, **Show PC command**.
- Stale state rule: when transport disconnects, dependent screens show a reconnect banner instead of keeping old values as if live.

### Gloved-use hardening (owned by **C1** and checked in **H**)

- Primary action buttons minimum 56 dp high, action tiles minimum 96 dp high.
- No critical action behind a tiny icon-only button; icon buttons must have text alternative nearby.
- Destructive actions like **Clear Codes** require a full-width confirmation dialog with plain consequences.
- Touch targets on the Connection drawer and Data Transfer card must remain usable in landscape and portrait.

---

## F. P1 — New features (commercial)

| # | Feature | Files / new routes | Why |
|---|---|---|---|
| F1 | **Vehicle profile** (VIN auto from `0902` + manual override + YMM picker) | `ui/main/VehicleProfileScreen.kt`, `data/VehicleProfile.kt` | Always know the car; powers recalls, repair story, history grouping |
| F2 | **DTC explainer with offline AI fallback** | `data/DtcDictionary.kt` + bundled `assets/dtc_generic.json`, `engine/DtcExplainer.kt` | Plain-English DTC info in a no-signal bay |
| F3 | **Repair-story PDF from sessions** | `report/RepairStoryBuilder.kt`, `report/PdfRenderer.kt` | The customer-facing artifact that justifies the bill |
| F4 | **Live data PID picker, graphs, min/max, record/playback** | `ui/live/LiveDataScreen.kt` rewrite, `engine/PidCatalog.kt` | The "I bet that O2 sensor is lazy" moment |
| F5 | **Battery voltage chip** (`ATRV` every 5 s on ELM) | `engine/VoltagePoller.kt`, top-bar in `MainActivity` | Tech sanity check |
| F6 | **Smart auto-reconnect with last-good transport memory** | `vci/AutoReconnect.kt`, `SettingsRepo.lastGoodTransport` | Cable wiggle no longer ends the session |
| F7 | **Recalls quick lookup from current VIN** | `recalls/NhtsaClient.kt`, home card badge | Adds value to every connection |
| F8 | **Quick share** (SMS / email / save PDF) | `report/ShareIntents.kt` | Closes the loop with the customer |
| F9 | **History persistence for direct-cable scans** | extend `StandaloneVciController.runFullScan` | Today, direct ELM scans don't appear in History |
| F10 | **"Update everything"** — app + vehicle database sync | `agent/UpdateAll.kt`, `transfer/VehicleDatabaseConsumer.kt` | One button to be current |
| F11 | **Settings overhaul** — receiver IP, build channel, theme, voice, vehicle profile | `ui/settings/SettingsScreen.kt`, `SettingsRepo` additions | Every operator-visible knob in one place |
| F12 | **Tech notes voice memo per session** | `notes/VoiceMemoRecorder.kt` | "1996 Camry, no start, fuel pressure 38" without typing |
| F13 | **Camera VIN scan** | `ui/main/VinCameraScanScreen.kt`, ML Kit or ZXing fallback if available | Fast fallback when OBD VIN is missing and manual typing is painful |
| F14 | **Shop export formats** | `report/ShopExport.kt` | Export PDF + CSV + plain text bundle that can be attached to RO/shop-management systems |
| F15 | **Technician profiles** | `data/TechnicianProfile.kt`, Settings | Track who ran a scan and stamp report footer without adding multi-user auth |
| F16 | **Predictive maintenance notes** | `engine/MaintenanceHints.kt`, Report screen | Local rules from mileage + DTC + live data; AI wording only after the local hint exists |

---

## G. Execution plan — parallel lanes

Doctrine: up to 4 Codex lanes (K1–K4) first, then up to 8 Cursor lanes (C1–C8). K2 (rebrand) lands first; K1, K3, K4 own disjoint subtrees and can run in parallel via git worktrees.

### Wave 1 — K lanes

#### K1 — Data transfer P0 fix
- Branch: `fix/data-transfer-actually-works`
- Owns: `app/src/main/kotlin/com/caseforge/scanner/transfer/`, `app/src/main/kotlin/com/caseforge/scanner/ui/transfer/`, `scripts/lan-export-receiver.ps1`, `scripts/install-tcw-receiver.ps1` (new), `scripts/tcw-raw-receiver.ps1` (new)
- Done-when: `/health` probe + 6-step state machine + sha256 verify + resumable upload + transfer log, all per §D.

#### K2 — Rebrand sweep
- Branch: `chore/rebrand-tcw-sweep`
- Owns: cross-cutting strings, manifest, theme, gradle, About screen, scripts/run-rebrand-grep
- Done-when: `rg "\b(Launch|X431|x431|cnlaunch|CaseForge|caseforge)\b"` returns 0 lines outside `com.caseforge.scanner` package id and `OEM_DATA_PATH` constant.

#### K3 — Updater hardening + Update Center
- Branch: `fix/updater-hardening-and-update-center`
- Owns: `app/src/main/kotlin/com/caseforge/scanner/agent/Updater.kt`, new `ui/updates/UpdateCenterScreen.kt`
- Done-when: NPE fix, UpdaterPhase MutableStateFlow, Update Center screen with linear progress + modal install + history.

#### K4 — Asset generation harness
- Branch: `feat/tcw-launcher-icons-and-empty-states`
- Owns: all `res/mipmap-*/ic_launcher*.png`, `res/drawable/ic_launcher_foreground.xml`, `res/drawable/ic_launcher_background.xml`, `res/drawable/ic_notification.xml`, `res/drawable/splash_logo.xml`, `res/drawable/empty_*.png`, `res/mipmap-anydpi-v26/ic_launcher.xml`
- Done-when: APK builds with new icons; launcher shows two-wrench T mark (no "X").

### Wave 2 — C lanes (after K1/K2/K3/K4 merged)

C1 — Home + Connection drawer polish + transport pill + voltage chip
C2 — Scan + Report (PDF + share + history)
C3 — Live Data (units, picker, graphs, record)
C4 — Service + Bidirectional gating + OEM-required empty state
C5 — Settings overhaul (receiver IP, theme, channel, voice, vehicle profile)
C6 — Update Center entry wiring
C7 — Manual VIN / YMM + recalls on home
C8 — Diagnostics + Transfer Log viewer
C9 (if capacity) — Startup/perceived-speed polish and cached Home state
C10 (if capacity) — Camera VIN scan + shop export formats

### Merge order

1. K2 (rebrand) → everyone rebases.
2. K4 (icons) → independent.
3. K1 (transfer) → depends on K2 renames.
4. K3 (Updater) → depends on K2.
5. C1–C4 in parallel after K-wave.
6. C5–C8 after C1.

---

## H. Ship checklist

**Transport coverage** — USB OBD auto-connect; OEM USB; ELM327 BT; OEM BT.
**Data transfer** — fresh install end-to-end test produces ≥100 MB zip on PC with matching sha256; resume after kill works; every error path renders right remediation.
**Updates** — N→N+1 in-app upgrade with PackageInstaller; Update Center renders all phases.
**Theme + accessibility** — dark + light legible; BT_CONNECT gated to API 31+; MANAGE_EXTERNAL_STORAGE flow.
**Reports + history** — PDF + share + direct-cable scan in History.
**Brand audit (CI-enforced via `scripts/run-rebrand-grep.ps1`)** — zero forbidden words outside `com.caseforge.scanner` package id and `OEM_DATA_PATH` constant.
**Release notes** — TCW 1.0 keeps `com.caseforge.scanner` so in-app updates work; future 2.0 migrates package id with 30-day notice.
