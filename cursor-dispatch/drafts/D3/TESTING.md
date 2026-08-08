# TESTING.md — Together Scanners AI

**Build Cycle Testing Checklist**

This document provides a manual test checklist for every behavior shipped in the current build cycle. Each scenario includes verification steps and remediation hints for failures.

---

## A: Overlay Foundation

### A1: Overlay launches from MainActivity "Take over X431" button
- [ ] **How to verify:** Open MainActivity, locate the "Take over X431" button, tap it, and confirm the overlay appears.
- **If it fails:** Check that MainActivity has the button wired to `overlayLauncher.launch()` and that overlay permissions are not revoked.

### A2: EngineDriver.fullScan() completes
- [ ] **How to verify:** Run CI unit tests for `EngineDriver.fullScan()` and confirm all tests pass (proxy for manual verification).
- **If it fails:** Check CI logs for timeout errors or missing mock drivers; verify `DriverManager` initialization completes before scan starts.

### A3: Health banner appears when accessibility is revoked
- [ ] **How to verify:** While overlay is visible, revoke accessibility permissions in device Settings, then return to the app and confirm banner appears.
- **If it fails:** Check that `AccessibilityManager.isAccessibilityEnabled()` is polled on app resume and `HealthBanner.show()` is called when false.

### A4: Every ScreenKind has a distinct renderer
- [ ] **How to verify:** Search codebase for all `ScreenKind` enum values and confirm each one has a corresponding renderer class.
- **If it fails:** Add missing renderer class or add missing `ScreenKind` enum case; ensure no renderers are orphaned or duplicated.

### A5: Long-press on bubble launches full-screen overlay
- [ ] **How to verify:** In the app UI, long-press on a diagnostic bubble for ≥500ms and confirm full-screen overlay appears.
- **If it fails:** Check `ViewConfiguration.getLongPressTimeout()` is being respected and the gesture slop threshold isn't masking the gesture as a drag.

### A5: Single-tap on bubble still starts agent session
- [ ] **How to verify:** Tap once on a diagnostic bubble and confirm agent session starts (verify via session ID in logs).
- **If it fails:** Ensure `GestureDetector.onSingleTapConfirmed()` fires before long-press timeout; check event dispatch order.

### A6: overlayOnX431 toggle persists across app restart
- [ ] **How to verify:** Toggle "Overlay on X431" on in settings, close and fully kill the app, reopen it, and confirm toggle remains on.
- **If it fails:** Verify toggle state is written to `SharedPreferences` with key `overlayOnX431_enabled` before app closes.

### A6: X431 foreground auto-launches overlay when toggle on
- [ ] **How to verify:** Set toggle on, background the app, start X431 diagnostic app, then foreground the test app and confirm overlay launches automatically.
- **If it fails:** Check `BroadcastReceiver` for `ACTION_USER_FOREGROUND` is registered and `overlayLauncher.autoLaunch()` is called when toggle is true.

---

## B: Capability Registry

### B1: First launch offline still loads bundled capabilities
- [ ] **How to verify:** Disconnect device from network, force-clear app cache, launch app, and confirm diagnostic capabilities are available.
- **If it fails:** Verify bundled `capabilities.json` is packaged in APK assets and `AssetCapabilityLoader` is called when network unavailable.

### B1: Second launch within 24h hits cache
- [ ] **How to verify:** Launch app online, note timestamp, launch again within 24h offline, confirm no network request is made and capabilities load from cache.
- **If it fails:** Check `CapabilityCache.isFresh()` uses correct TTL (86400 seconds) and cache lookup precedes network request.

### B2-B9: capabilities.json has ≥240 entries (≥30 per OEM × 8 OEMs)
- [ ] **How to verify:** Open `capabilities/capabilities.json`, count total entries and entries per OEM (Bosch, Autel, Snap-on, Launch, Foxwell, Innova, BlueDriver, XTOOL), confirm ≥240 total and ≥30 per OEM.
- **If it fails:** Add missing OEM entries to capabilities file; validate JSON structure with schema validator; check for duplicate entries.

---

## C: Visual Polish

### C1: Dynamic colors apply on Android 12+ device
- [ ] **How to verify:** Run on Android 12+ (or emulator), confirm all overlay text, buttons, and backgrounds use Material 3 dynamic color tokens, not hardcoded values.
- **If it fails:** Replace hardcoded color literals with `android.R.attr.colorPrimary` and `DynamicColors.wrapContextIfAvailable()`.

### C1: No raw sp/Color literals in overlay
- [ ] **How to verify:** Run `grep -r "0x[0-9A-Fa-f]" --include="*Overlay*.kt" .` and `grep -r "@dimen/" --include="*Overlay*.kt" .` to search for raw literals.
- **If it fails:** Replace all `0xAARRGGBB` literals with named color resources and all hardcoded sp values with `@dimen/` references.

### C2: Onboarding shows on first run, not second
- [ ] **How to verify:** Force-clear app storage, launch, confirm onboarding flow appears; close and reopen, confirm onboarding does not appear.
- **If it fails:** Verify onboarding flag is set in `SharedPreferences` after completion and checked on app startup.

---

## D: Reliability

### D1: 3-second dead-space hold dismisses overlay
- [ ] **How to verify:** Open overlay, long-press on any empty (non-interactive) area for ≥3 seconds, confirm overlay closes.
- **If it fails:** Check `OverlayView.onLongPress()` detects empty region and calls `dismiss()` after 3000ms elapsed.

### D2: Process crash → overlay restored within 5s
- [ ] **How to verify:** Open overlay, forcefully kill app process via `adb shell kill`, wait ≤5s, and confirm overlay reappears when app relaunches.
- **If it fails:** Verify crash handler calls `OverlayRestoreService` and `Bundle` with overlay state is saved before crash occurs.

### D2: Device reboot → overlay restored when X431 launched
- [ ] **How to verify:** Set overlay toggle on, reboot device, launch X431, confirm test app receives broadcast and overlay auto-launches.
- **If it fails:** Check `BootBroadcastReceiver` persists overlay toggle to disk and `onX431Foreground` broadcasts are received after boot completes.

---

## Definition of Done

Per build brief §12 acceptance items:

✓ All overlay gestures (tap, long-press, hold-dismiss) respond within 100ms.
✓ Capability registry loads offline and caches for 24h.
✓ Dynamic colors render on Android 12+; no raw color literals in overlay code.
✓ Onboarding displays once per app lifecycle.
✓ Overlay persists across process crash and device reboot.
✓ Health banner appears when accessibility is revoked.
✓ No unhandled exceptions in EngineDriver, CapabilityLoader, or GestureHandler.
✓ CI unit tests all pass; manual test checklist all items checked.

---

**Product:** Together Scanners AI  
**Last updated:** 2026-05-18
