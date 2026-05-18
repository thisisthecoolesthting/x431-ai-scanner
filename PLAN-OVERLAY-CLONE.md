# Launch AI — Overlay-First, Clone-Second Build Brief

**Owner:** Ricky / CaseForge
**Target hardware:** Launch X431 PRO / PROS / V+ Android tablet
**Date:** 2026-05-18
**Status:** Plan for review by Gemini 2.5 Pro + DeepSeek, then handoff to Sonnet / Haiku / Cursor / Codex

---

## 1. Vision (plain English)

One app. **Launch AI** is what the technician sees, all day. The Launch X431 diagnostic app becomes a hidden subsystem that does the protocol work with the VCI dongle; its UI never reaches the user's eyes. The technician's daily flow is: open Launch AI → tap "Full Scan" → see results in our brand → done.

We achieve this in **two phases**, sequenced so each phase ships value on its own and the user can fall back to the previous state at any time.

---

## 2. Phase sequencing & why

| Phase | What | Touches X431 APK? | Reversibility |
|---|---|---|---|
| **1. Overlay** | Launch AI draws a full-screen overlay over X431. We scrape X431's UI via accessibility; render it as our own Compose screens; user taps in our UI translate to X431 taps. | No — original X431 untouched. | Total. Dismiss overlay → vanilla X431 reappears. |
| **2. Clone** | Decompile X431, rename package, sign with our debug keystore, install alongside original. Strip splashes / EULAs / promo dialogs. Retarget Launch AI's overlay at the cloned package. | Yes — but only the clone. Original X431 stays installed and pristine. | High. Disable the cloned engine; Launch AI falls back to the original X431. |

We do Phase 1 first because:

1. Validates the custom-UI experience against an unmodified X431, so we know the overlay/scraping pattern works before we spend cycles on the clone.
2. Phase 2 is much cheaper once we know exactly which X431 popups and splashes need to be stripped — Phase 1 surfaces that list naturally.
3. If Launch ever updates X431 and Phase 2's clone breaks, Phase 1's overlay still works against the new official version.

---

## 3. Phase 1 — Overlay (detailed)

### 3.1 Architecture

```
┌──────────────────────────────────────────┐
│   Launch AI Compose UI (overlay window)  │  ← user sees this
├──────────────────────────────────────────┤
│   EngineState (typed model)              │
│   ▲                       ▼              │
│   │           AgentRunner / Driver       │
│   │                       │              │
│   EngineScraper           │ tap/type/scroll
│   (reads AccessibilityNodeInfo)          │
│   ▲                       ▼              │
├──────────────────────────────────────────┤
│   X431 PRO app (foreground, hidden)      │  ← actually drives the VCI
└──────────────────────────────────────────┘
```

The X431 app stays foreground so Android's AccessibilityService can both *read* its UI tree and *dispatch* taps into it. Our full-screen overlay sits on top, covering X431 visually.

### 3.2 New / changed files

| File | Purpose |
|---|---|
| `agent/EngineState.kt` | Typed model: `data class EngineState(screen: ScreenKind, vehicleVin: String?, currentMenu: List<String>, dtcs: List<Dtc>, liveData: Map<String, Double>, busy: Boolean, lastUpdate: Long, raw: ScreenSnapshot)`. `ScreenKind` is a sealed class: `HomeMenu`, `VehicleSelect`, `FullScanProgress`, `FullScanResults`, `DtcDetail`, `LiveDataView`, `ActuationTest`, `Unknown(raw)`. |
| `agent/EngineScraper.kt` | Pure function: `ScreenSnapshot → EngineState`. Pattern-matches X431's accessibility tree against known shapes. Returns `Unknown` when it doesn't recognize the screen — never throws. |
| `agent/EngineDriver.kt` | High-level operations independent of X431's menu structure: `suspend fun fullScan(): FullScanResult`, `readDtcs()`, `clearCodes()`, `liveData(pids: List<String>): Flow<LiveSample>`, `actuate(testId: String): ActuationResult`. Each method consults `CapabilityMap` for the menu path, drives X431 via accessibility, watches `EngineScraper` output for completion. |
| `agent/CapabilityMap.kt` + `assets/capabilities.json` | Declarative menu descriptors per feature. Versioned. Hot-patchable from GitHub raw URL. Example: `{"id":"full_scan","path":["Diagnose","Auto Scan"],"done_when":{"text_contains":"Scan complete"},"timeout_s":120}`. |
| `overlay/FullScreenOverlayService.kt` | Replaces / extends `OverlayService`. Adds a second `WindowManager.addView` for a MATCH_PARENT overlay containing a `ComposeView`. Window type `TYPE_APPLICATION_OVERLAY`, flags `FLAG_NOT_TOUCH_MODAL` so taps inside our overlay work but taps inside our "transparent" zones pass through to X431. |
| `overlay/OverlayHost.kt` | Compose entry point that lives inside the overlay window. Owns a NavController for our screens (Dashboard, ScanProgress, ScanResults, LiveData, Procedure, BehindCurtain). |
| `overlay/screens/*.kt` | Mirror the dashboard / quick-action / live-data screens we already have, but rendered inside the overlay window instead of MainActivity. Reuse existing composables where possible. |
| `overlay/BehindCurtainPane.kt` | Debug escape hatch: button "Show me X431" that temporarily fades the overlay to 0% opacity so the tech can see the underlying X431 UI. Useful when an abstraction breaks or when we want to verify the engine is doing the right thing. |
| `agent/EngineHealthMonitor.kt` | Watchdog: detects X431 crash (foreground package changes to launcher), VCI Bluetooth drop, accessibility service death. Surfaces a banner in our UI; tries to recover (relaunch X431, re-bind a11y) before nagging the user. |

### 3.3 Open questions for Phase 1

1. **Window input flags.** Need to confirm `FLAG_NOT_TOUCH_MODAL` + selective `FLAG_NOT_TOUCHABLE` regions work for our "tap our button → translate to X431 tap" pattern without losing focus dispatch. Verified pattern on Pixel; Launch tablets use custom Android skins.
2. **Compose inside overlay window.** Standard pattern: `ComposeView` attached via `WindowManager.addView` + custom `ViewTreeLifecycleOwner` + `ViewTreeSavedStateRegistryOwner`. Works but the lifecycle wiring is finicky.
3. **Status bar / nav bar.** Overlay should hide both during operation. Use immersive sticky on `WindowManager.LayoutParams`.
4. **Keyboard input.** When user types into our overlay (e.g., VIN), we need the IME to render above our overlay. `FLAG_ALT_FOCUSABLE_IM` + soft input mode adjust.

---

## 4. Phase 2 — Clone & rebrand (detailed)

### 4.1 Process

1. Pull original APK from the tablet:
   ```
   adb shell pm path com.cnlaunch.x431padv
   adb pull <path> x431-original.apk
   ```
2. Decompile:
   ```
   apktool d -o x431-clone x431-original.apk
   ```
3. Edit `x431-clone/AndroidManifest.xml`:
   - `package="com.cnlaunch.x431padv"` → `package="com.caseforge.launchai.engine"`
   - All `<provider android:authorities="com.cnlaunch.x431padv.*">` → `com.caseforge.launchai.engine.*`
   - Application label → `"Launch AI Engine"`
4. Strip splash / EULA / promo activities (comment out, or set `android:enabled="false"`).
5. Auto-accept license dialogs that survive — small smali patches to bypass the `OK` button waits.
6. Update `res/values*/strings.xml` brand references where it matters.
7. Rebuild + zipalign + sign:
   ```
   apktool b x431-clone -o launchai-engine-unsigned.apk
   zipalign -v 4 launchai-engine-unsigned.apk launchai-engine-aligned.apk
   apksigner sign --ks ~/.android/debug.keystore --ks-pass pass:android launchai-engine-aligned.apk
   ```
8. Install alongside original (different package name → no conflict):
   ```
   adb install -r launchai-engine-aligned.apk
   ```
9. In Launch AI, add the new package to `ScannerAccessibilityService.X431_PACKAGES` and make `EngineDriver` prefer the clone when present.

### 4.2 Caveats

- **SafetyNet / Play Integrity.** If Launch's app uses Google Play Integrity for license validation, the resigned clone will fail integrity checks and refuse to talk to the VCI. Mitigation: keep the original X431 installed as the fallback engine; Phase 1's overlay still works against the original.
- **VCI Bluetooth keys.** Some VCI dongles bond to the *specific install* via Bluetooth bonding keys. Test with the real VCI before declaring victory.
- **Update channel.** Launch's auto-updater won't touch the clone (different package). We re-clone after each Launch update we care about. Acceptable because Launch ships updates quarterly.
- **Storage / file paths.** X431 stores diagnostic logs under its package's external dir. Clone has a different external dir. User's existing scan history stays with the original install — only new scans through the clone use the new dir. Document this.

### 4.3 Legal note

The user owns the X431 tablet and an active Launch subscription. Repackaging is for personal use on hardware they own. Original APK is not redistributed. This is the user's stated intent; we don't ship the clone anywhere.

---

## 5. Stability & non-regression strategy

Goal: even after we ship Phase 2, the user can always get back to a working state in under a minute.

| Failure mode | Recovery path |
|---|---|
| Overlay misbehaves (z-order, frozen) | Tap-and-hold our bubble for 3s → emergency dismiss → vanilla X431. |
| Clone refuses VCI / fails integrity | Toggle "Use original engine" in Settings → Launch AI retargets `com.cnlaunch.x431padv`. |
| X431 update breaks CapabilityMap | Hot-patch JSON pushed from GitHub raw URL; fetched at app start with 24h cache. |
| Launch AI itself crashes | OverlayService is a foreground service with restart-on-crash; bubble survives main process death. |
| Accessibility service revoked by OS | Health monitor banner: "Tap to re-grant accessibility" → opens the right Settings page. |

### 5.1 Testing matrix (must pass before each release)

1. Cold install → setup wizard → first full scan → finish_session, no manual intervention.
2. Full scan with engine running, A/C on, all modules present.
3. Read DTCs on a vehicle with codes; clear codes; rescan shows clean.
4. Live data: RPM + coolant + speed streaming for 60s without disconnect.
5. Key programming dry-run (cancel before commit) on a Ford and a GM vehicle.
6. Lose VCI Bluetooth mid-scan → health monitor surfaces; reconnect; resume.
7. X431 app force-quit mid-scan → health monitor relaunches; agent retries.
8. Overlay rotate device → state preserved.
9. Tap-and-hold bubble 3s → overlay dismisses cleanly.
10. Toggle "Use original engine" / "Use clone engine" → both work end-to-end.

Each scenario gets a manual test checklist in `TESTING.md` and an action-log assertion (so the GitHub Actions CI can spot regressions even though it can't actually run on the tablet).

---

## 6. Handoff format for downstream agents

Each phase is broken into ~5-10 self-contained tickets. Each ticket has:

- **Title** (one line)
- **Files to create/edit** (absolute paths)
- **Acceptance criteria** (3-5 concrete bullets)
- **Tablet test scenario** (what the human checks after deploy)
- **Don't break** (regression watchpoints — files NOT to touch, behaviors NOT to alter)
- **Estimated effort** (hours)

Example ticket:

> ### T1-A: EngineState model
> **Files:** create `app/src/main/kotlin/com/caseforge/scanner/agent/EngineState.kt`
> **Acceptance:**
> - `EngineState` and `ScreenKind` defined as described in §3.2.
> - `@Serializable` so we can log snapshots.
> - Has a `fun isStale(thresholdMs: Long = 3000): Boolean`.
> - Unit test in `app/src/test/.../EngineStateTest.kt` covering all `ScreenKind` variants.
> **Tablet test:** none (pure model).
> **Don't break:** AgentRunner, AgentTools — don't import EngineState there yet. Phase 1 ticket T1-D wires it up.
> **Effort:** 1h.

Tickets get filed as GitHub Issues with a label `phase-1-overlay` or `phase-2-clone` so any agent (Sonnet, Haiku, Cursor, Codex) can claim one and ship it without stepping on others.

---

## 7. Open design questions (asks for the reviewer agents)

These are the questions where I'd specifically like Gemini's and DeepSeek's second opinion:

1. **Overlay vs. Picture-in-Picture.** Could we get a cleaner UX by running X431 in PiP and Launch AI in full screen, instead of overlay-over-foreground? What goes wrong with PiP that we'd miss?
2. **Capability map as JSON vs. Kotlin DSL.** JSON is hot-patchable but stringly-typed. A sealed-class DSL is type-safe but requires app rebuild for menu changes. Sweet spot?
3. **Accessibility scrape latency.** X431's scan-results screen has hundreds of nodes. What's the right caching / debounce strategy so we don't pin a CPU core scraping every text-change event?
4. **Clone integrity check workarounds.** If Launch ships Play Integrity in a future update and the clone fails, what's our least-bad fallback?
5. **Streaming UI updates from agent to overlay.** Should EngineDriver emit `Flow<EngineEvent>` that the overlay collects, or push to `AgentStatus`-style singletons? Trade-offs?
6. **Onboarding the technician.** First time they see the overlay covering X431, they will be confused. What's the smoothest "this is normal, X431 is still working behind this" first-run moment?
7. **Anything we're forgetting.** Anti-features? Footguns? Easy wins?

---

## 7b. What's already built (foundation in code, not yet on tablet)

As of this writing, the following Phase-1 scaffolding is in the repo on `main`, awaiting CI build #37:

- `engine/EngineState.kt` — sealed `ScreenKind` (NoEngine, HomeMenu, VehicleSelect, DiagnoseMenu, FullScanProgress, FullScanResults, DtcDetail, LiveDataView, ActuationTest, Settings, Dialog, Unknown).
- `engine/EngineScraper.kt` — heuristic accessibility-tree → EngineState. Pure function, never throws.
- `engine/CapabilityMap.kt` — 20 baseline capabilities (full_scan, read_dtcs, clear_dtcs, live_data, freeze_frame, actuation, oil_reset, epb, sas, tpms, battery_register, throttle_relearn, dpf_regen, injector_coding, key_program, abs_bleed, gear_learn, suspension, ecu_coding, module_program).
- `overlay/FullScreenOverlayService.kt` — MATCH_PARENT overlay window with ComposeView, LifecycleOwner+ViewModelStoreOwner+SavedStateRegistryOwner plumbing, 750ms scraper loop, capability dispatch.
- `overl