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
