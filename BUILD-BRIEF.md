# Launch AI — Build Brief for the Opus Orchestrator

**To:** the Opus window that will coordinate this build
**From:** the Opus window that scoped it
**User:** Ricky (reasner196@gmail.com)
**Repo:** github.com/thisisthecoolesthting/x431-ai-scanner
**Workspace:** `E:\Projects\CaseForge\x431-ai-scanner` (Windows)
**Sandbox mount:** `/sessions/<id>/mnt/CaseForge/x431-ai-scanner` (Linux for bash)
**Target device:** Launch X431 PRO / PROS / V+ tablet (Android, automotive workshop)
**Date scoped:** 2026-05-18
**Total estimated work:** ~40 agent-hours, parallelizable to ~6 wall-clock hours with 6-10 concurrent agents

---

## 1. The mission in one paragraph

Build "Launch AI" — an Android app that sits in front of the Launch X431 diagnostic app on a technician's tablet. The technician sees only Launch AI; X431 runs invisibly underneath as a diagnostic engine, talking to the VCI dongle and the vehicle's OBD-II port. Launch AI scrapes X431's UI via Android AccessibilityService, builds a typed state model, renders our own custom Compose UI on a full-screen overlay, and translates user actions in our UI back into X431 taps via the same accessibility channel. AI reasoning (Anthropic Claude) handles diagnosis, root-cause, and customer-facing write-ups. Optional second-source data comes from a generic ELM327 OBD-II Bluetooth dongle.

**Architecture decided:** two-APK design that feels like one app.
- APK #1: **Launch AI** (this repo) — the UI, the AI, the orchestrator.
- APK #2: **X431** (Launch's original app, untouched for now) — the diagnostic engine.

Phase 2 (clone X431, strip splash/EULA, rebrand) is **deferred indefinitely**. The user said yes to clone later but no further work on it during this build cycle. Vehicle-coverage updates from Launch then flow through the original X431 with zero maintenance on our side.

---

## 2. What's already done (do NOT redo)

Pushed to `main` on GitHub (last commit before this brief was build #36):

- Setup wizard (5 steps, lifecycle-aware re-checks on resume)
- Dashboard UI with reactive VIN, action grid, voice mic, TTS
- Settings (encrypted Claude API key, theme, kill-switch, approval, notes, model selection)
- Agent runner (Claude tool-use loop with retries, 800ms backoff, 4096 max tokens, `tool_choice: "any"`)
- 10 agent tools: read_screen, tap, type, scroll, back, wait_for, capture_screenshot, vin_lookup (NHTSA), repair_info_lookup, propose_actuation, finish_session
- Accessibility service + screen-capture service + overlay bubble + foreground services
- Action log, history database (Room), customer/RO/reports DB schema
- NHTSA VIN decode + recalls
- OEM playbooks
- ELM327 Bluetooth driver (ObdBluetoothTool — full Mode 01/03/04/07 stack)
- In-app updater (pulls from GitHub releases latest tag)
- GitHub Actions CI building APKs on every push
- Round 17 features: camera tool, acoustic tool, PDF report builder, OEM playbooks, customer DB, RO DB, cost tracker, boot receiver, light/dark theme

Local on disk but **not yet pushed** (sitting in `E:\Projects\CaseForge\x431-ai-scanner\` waiting for `push.bat`):

| File | Status | What it is |
|---|---|---|
| `app/src/main/kotlin/com/caseforge/scanner/engine/EngineState.kt` | NEW | Typed model: ScreenKind sealed class, Dtc, EngineState |
| `app/src/main/kotlin/com/caseforge/scanner/engine/EngineScraper.kt` | NEW | Pure function: ScreenSnapshot → EngineState. Pattern-matches X431 menus. |
| `app/src/main/kotlin/com/caseforge/scanner/engine/CapabilityMap.kt` | NEW | 20 baseline capabilities seeded |
| `app/src/main/kotlin/com/caseforge/scanner/overlay/FullScreenOverlayService.kt` | NEW | MATCH_PARENT overlay window hosting ComposeView |
| `app/src/main/kotlin/com/caseforge/scanner/overlay/compose/OverlayRoot.kt` | NEW | Dark-mode Compose UI inside overlay |
| `app/src/main/kotlin/com/caseforge/scanner/ui/obd/ObdScanScreen.kt` | NEW | Standalone OBD-II diagnostic screen |
| `app/src/main/kotlin/com/caseforge/scanner/ui/wizard/SetupWizardScreen.kt` | EDIT | Wording clarifications + "How it works" callout |
| `app/src/main/kotlin/com/caseforge/scanner/agent/ObdBluetoothTool.kt` | EDIT | Added `clearCodes()`, `isConnected()`, `connectedDeviceName()` |
| `app/src/main/kotlin/com/caseforge/scanner/MainActivity.kt` | EDIT | `onTakeOverX431` callback launches FullScreenOverlayService |
| `app/src/main/kotlin/com/caseforge/scanner/ui/dashboard/DashboardScreen.kt` | EDIT | "Take over X431" headline button |
| `app/src/main/AndroidManifest.xml` | EDIT | Registered FullScreenOverlayService |
| `app/build.gradle.kts` | EDIT | Added `androidx.savedstate:savedstate-ktx:1.2.1` |
| `PLAN-OVERLAY-CLONE.md` | NEW | Earlier draft of architecture (this brief supersedes it) |

**FIRST ACTION for the orchestrator:** ask the human to run `push.bat` from `E:\Projects\CaseForge\x431-ai-scanner\` so the foundation lands in CI. Build #37 should go green within ~3 minutes. Verify the green check at https://github.com/thisisthecoolesthting/x431-ai-scanner/actions before dispatching agents.

---

## 3. Architecture

```
                     ┌─────────────────────────────────────┐
                     │    Launch AI APK (this repo)        │
                     │                                     │
  ┌──────────┐       │   ┌───────────────────────────┐    │
  │ User taps│──────────▶│  OverlayRoot (Compose)    │    │
  │ in our UI│       │   │  inside MATCH_PARENT      │    │
  └──────────┘       │   │  TYPE_APPLICATION_OVERLAY │    │
                     │   └────────────┬──────────────┘    │
                     │                │                    │
                     │   ┌────────────▼──────────────┐    │
                     │   │  EngineDriver             │    │
                     │   │  (CapabilityMap-aware)    │    │
                     │   └─────┬─────────────────▲───┘    │
                     │         │ tap/type/back   │ scrape │
                     │   ┌─────▼─────────────────┴───┐    │
                     │   │ ScannerAccessibilityService│    │
                     │   └─────┬─────────────────▲───┘    │
                     │         │ AccessibilityNode tree   │
                     └─────────┼─────────────────┼────────┘
                               │                 │
                     ┌─────────▼─────────────────┴────────┐
                     │       X431 PRO app (untouched)     │
                     │       Foreground, hidden by overlay│
                     │                                    │
                     └─────────┬──────────────────────────┘
                               │ Launch's proprietary protocol
                               │ Bluetooth / USB
                     ┌─────────▼──────────────────────────┐
                     │    Launch X431 VCI dongle          │
                     │    OBD-II → vehicle bus            │
                     └────────────────────────────────────┘
```

Optional second data source for Mode-1 OBD-II only:
```
  ELM327 dongle ──Bluetooth SPP──▶ ObdBluetoothTool ──▶ ObdScanScreen
```

### Key invariants

1. **Accessibility requires foreground.** X431 must be the foreground Android app for accessibility to operate it. Our overlay covers it visually but X431 is the focused window.
2. **Overlay is just visual.** We don't intercept input destined for X431 (mostly). `FLAG_NOT_TOUCH_MODAL` lets taps inside our overlay work; taps in our "transparent zones" pass through to X431. During Peek mode (overlay alpha → 0), we also set `FLAG_NOT_TOUCHABLE` so the tech can directly touch X431.
3. **EngineScraper never throws.** Unknown screens return `ScreenKind.Unknown(hint)`. The Compose layer renders a graceful fallback that lets the user peek through to X431.
4. **CapabilityMap is the contract.** Every X431 capability we expose has a Capability descriptor in `CapabilityMap.kt`. Adding a new capability is "add an entry; the UI picks it up automatically."

---

## 4. Ticket breakdown (the work to dispatch)

All tickets are self-contained: each can be executed by an agent in isolation, on its own branch, against the current `main`. Mark a ticket DONE only when CI is green AND the tablet test passes (or N/A is justified).

### EPIC A — Phase 1 overlay polish & wiring (already partially done)

**A1: Verify overlay builds + renders on the tablet.**
- *Owner:* Sonnet (small, sensitive to compile errors)
- *Files:* `FullScreenOverlayService.kt`, `OverlayRoot.kt`
- *Acceptance:* CI green for build that contains these files. Tech installs, taps "Take over X431 (custom UI)", overlay appears within 2 seconds covering whatever's on screen. Top bar reads "Launch AI" with current ScreenKind subtitle. Three icon buttons (Peek / Minimize / Dismiss) work.
- *Tablet test:* tap each escape hatch and verify behavior.
- *Don't break:* original bubble OverlayService — both must coexist.
- *Effort:* 0.5h if no compile errors; up to 3h if Compose-in-Service lifecycle has issues.

**A2: EngineDriver high-level operations.**
- *Owner:* Haiku (mechanical wiring against existing primitives)
- *Files:* create `app/src/main/kotlin/com/caseforge/scanner/engine/EngineDriver.kt`
- *Public API:*
  ```kotlin
  class EngineDriver(private val a11y: ScannerAccessibilityService, private val log: AgentActionLog) {
      suspend fun runCapability(id: String): Result<JsonObject>
      suspend fun fullScan(): Result<FullScanResult>
      suspend fun readDtcs(module: String? = null): Result<List<Dtc>>
      suspend fun clearCodes(): Result<Unit>
      fun liveData(pids: List<String>): Flow<LiveSample>
      suspend fun actuate(testId: String): Result<ActuationResult>
  }
  ```
- *Acceptance:* `runCapability("oil_reset")` walks the CapabilityMap path with 900ms inter-step delay, waits for `doneWhen` marker within `timeoutSec`, returns Result.success. Logs every step to AgentActionLog. Uses `Result<>` not exceptions.
- *Tablet test:* tap a capability card in overlay → action log shows step-by-step progress → marker found → result appears in our UI.
- *Don't break:* AgentRunner (Claude tool-use loop) — leave it untouched. EngineDriver is a separate code path.
- *Effort:* 4h.

**A3: EngineHealthMonitor.**
- *Owner:* Haiku
- *Files:* create `app/src/main/kotlin/com/caseforge/scanner/engine/EngineHealthMonitor.kt`
- *Watches:* foreground package (`UsageStatsManager` or AccessibilityEvent.WINDOW_STATE_CHANGED), accessibility service liveness (`ScannerAccessibilityService.instance() != null`), Bluetooth state if relevant.
- *Acceptance:* Banner appears in OverlayRoot.errorBanner when X431 crashes, accessibility service revoked, or VCI disconnects. Auto-retries once before nagging. Exposes state as `StateFlow<HealthState>`.
- *Effort:* 3h.

**A4: Polish OverlayRoot per-screen renderers.**
- *Owner:* Sonnet
- *Files:* `overlay/compose/OverlayRoot.kt` and break out into `overlay/compose/screens/*.kt` if it grows past 400 lines.
- *Acceptance:* Each ScreenKind has a distinct, on-brand renderer. Loading states use `CircularProgressIndicator`. DTC list groups by module if module field present. Live data renders as cards with units. Unknown screen still falls back to "Peek to see X431" hint.
- *Effort:* 3h.

**A5: Tap-and-hold bubble to launch full-screen overlay.**
- *Owner:* Haiku
- *Files:* `overlay/OverlayService.kt`
- *Acceptance:* Long-press on the bubble (>800ms) → starts FullScreenOverlayService. Single tap retains existing start-agent behavior.
- *Effort:* 1h.

**A6: Settings toggle: "Show overlay automatically when X431 opens."**
- *Owner:* Haiku
- *Files:* `data/SettingsRepo.kt`, `ui/settings/SettingsScreen.kt`, `agent/ScannerAccessibilityService.kt`
- *Acceptance:* New boolean pref `overlayOnX431` (default false). When true, `onAccessibilityEvent` detecting X431 foreground → starts FullScreenOverlayService.
- *Effort:* 2h.

### EPIC B — Capability map fan-out

**B1: Capability JSON loader + hot-patch fetcher.**
- *Owner:* Haiku
- *Files:* create `app/src/main/kotlin/com/caseforge/scanner/engine/CapabilityRegistry.kt`
- *Acceptance:* On app start, load bundled `assets/capabilities.json` merging into baseline. Try fetching `https://raw.githubusercontent.com/thisisthecoolesthting/x431-ai-scanner/main/capabilities/capabilities.json` with 5s timeout, 24h cache. Last-good cache survives offline.
- *Effort:* 3h.

**B2-B9: OEM capability files (8 parallel agents).**
- *Owner:* 8 × Haiku, in parallel.
- *Files:* `capabilities/capabilities.<oem>.json` for each of Ford / GM / Stellantis / Toyota / Honda / VAG / BMW / Mercedes.
- *Schema (must match Kotlin `Capability` data class):*
  ```json
  {
    "id": "ford_pats_relearn_2017_plus",
    "label": "PATS key relearn (Ford 2017+)",
    "category": "Programming",
    "path": ["diagnose", "ford", "immobilizer", "pats", "relearn"],
    "done_when": "successful",
    "timeout_sec": 600,
    "oem_scope": ["ford", "lincoln"],
    "note": "Requires master key + 2 new keys present."
  }
  ```
- *Acceptance:* Each file has ≥30 entries. All `category` values valid (Scan/Codes/LiveData/Service/Bidirectional/Programming/Coding/Module). `oem_scope` populated. Path arrays lowercase. The orchestrator merges all eight into a single `capabilities/capabilities.json` checked into the repo.
- *Reference for menu paths:* X431 PRO/V+ menu trees publicly documented in Launch's user manuals and forums; the agent can web-search for "Launch X431 PRO Ford [function] menu path" and pattern-match.
- *Effort:* 2h per OEM × 8 OEMs = 16h total but fully parallel.

### EPIC C — Visual polish

**C1: Material3 dynamic colors + typography pass.**
- *Owner:* Sonnet
- *Files:* `ui/theme/Theme.kt`, all overlay screens
- *Acceptance:* All text uses MaterialTheme.typography (no raw `fontSize`). All color usage goes through colorScheme. Dark + light themes both render legibly.
- *Effort:* 4h.

**C2: Onboarding overlay (first-run only).**
- *Owner:* Sonnet
- *Files:* `overlay/compose/OverlayOnboarding.kt`, hook into FullScreenOverlayService
- *Acceptance:* First time the user opens the overlay, a brief explainer modal shows: "Launch AI is now driving X431 underneath this UI. Tap the eye to peek at X431 if you ever need to see it directly." Has "Don't show again" checkbox stored in SettingsRepo.
- *Effort:* 2h.

### EPIC D — Reliability & rollback

**D1: Emergency dismiss gesture.**
- *Owner:* Haiku
- *Files:* `overlay/FullScreenOverlayService.kt`
- *Acceptance:* Press and hold any non-button area for 3 seconds → overlay fades and dismisses. Surfaces a Toast: "Overlay dismissed. X431 is now visible."
- *Effort:* 1.5h.

**D2: Persistent overlay-state restoration after a crash.**
- *Owner:* Haiku
- *Files:* `overlay/FullScreenOverlayService.kt`, `App.kt`
- *Acceptance:* If the overlay service is killed by Android (memory pressure, etc.) and `overlayOnX431=true`, the BootReceiver / health monitor relaunches it within 5 seconds when X431 is detected foreground again.
- *Effort:* 2h.

**D3: Testing harness.**
- *Owner:* Haiku
- *Files:* `TESTING.md` at repo root
- *Acceptance:* Manual checklist covering all of §5.1 in the original PLAN-OVERLAY-CLONE.md. Each scenario gets a pass/fail box and a "what to do if it fails" hint.
- *Effort:* 2h.

### EPIC E — AI fallback (deferred, but ticket scaffolded)

**E1: Local LLM fallback investigation.**
- *Owner:* Sonnet (research, not build)
- *Files:* `docs/LOCAL-LLM.md`
- *Acceptance:* Document covers: which Q4-quantized models fit in ~2GB RAM on the X431 tablet (Phi-3-mini, Gemma 2B, Llama 3.2 1B), which Android inference library to use (MLC LLM, llama.cpp Android, ONNX Runtime), expected tok/sec, integration plan with the existing `ClaudeClient` interface so it's a drop-in.
- *Effort:* 3h.

---

## 5. Dispatch order & dependencies

```
Foundation (already merged on push)
   │
   ▼
A1 verify overlay renders  ◀── must pass before anything else dispatches
   │
   ├── A2 EngineDriver (Haiku, 4h)
   ├── A3 HealthMonitor   (Haiku, 3h)
   ├── A4 Polish OverlayRoot (Sonnet, 3h)
   ├── A5 Bubble long-press (Haiku, 1h)
   ├── A6 Auto-overlay toggle (Haiku, 2h)
   ├── B1 CapabilityRegistry (Haiku, 3h)
   ├── B2-B9 OEM maps × 8 (Haiku, parallel, 2h each)
   ├── C1 Visual polish    (Sonnet, 4h)
   ├── C2 Onboarding modal (Sonnet, 2h)
   ├── D1 Emergency gesture (Haiku, 1.5h)
   ├── D2 State restoration (Haiku, 2h)
   └── D3 Testing harness   (Haiku, 2h)
        │
        ▼
   (all merge to main)
        │
        ▼
   E1 Local LLM research (Sonnet, 3h) — non-blocking, can run any time
```

After A1 passes, everything else fans out in parallel. Minimum 6-agent concurrent dispatch keeps wall clock around 4-6 hours. Maximum useful concurrency is about 10 (more agents = more merge conflicts on the few shared files like Settings + Manifest).

### Shared-file conflict map

| File | Tickets that touch it | Coordination |
|---|---|---|
| `AndroidManifest.xml` | A6 | One ticket only — no conflict |
| `data/SettingsRepo.kt` | A6, C2, D1 | Sequence: A6 → C2 → D1, or each agent does separate add-only commits |
| `MainActivity.kt` | (none in this batch) | Foundation already wired it |
| `overlay/FullScreenOverlayService.kt` | A1, A3, D1, D2 | Sequence after A1: A3 + D1 + D2 in series |
| `overlay/compose/OverlayRoot.kt` | A1, A3, A4, C1, C2 | Sequence after A1: A3 → A4 → C1 → C2 |

The orchestrator must enforce ordering by gating dispatch (don't fire A4 until A1 is merged on `main`).

---

## 6. Branching strategy

- **One PR per ticket.** Branch name `agent/<ticket-id>-<slug>`, e.g., `agent/A2-engine-driver`.
- **PR title:** `[A2] EngineDriver high-level operations`.
- **PR description:** copy the ticket from §4 verbatim plus a "what changed" summary.
- **Merge condition:** CI green + 1 reviewer (orchestrator or human).
- **No squash for foundation work** — preserve agent attributions.
- **Force-pushes forbidden** on `main`. Only `git push` after fast-forward merge.

---

## 7. CI / build pipeline

- GitHub Actions config: `.github/workflows/build.yml`. Builds debug APK on every push to `main` and every PR open/sync.
- Output: APK uploaded as workflow artifact AND published to the `latest` release tag. Stable download URL: `https://github.com/thisisthecoolesthting/x431-ai-scanner/releases/latest/download/app-debug.apk`.
- In-app updater on the tablet polls that URL and offers an update when the SHA changes.
- Tablet doesn't have internet from inside the workshop sometimes — orchestrator should ALSO have the human run `copy-apk-to-d.bat` after each build so a copy lands on `D:\` for sideloading via USB if needed.
- **CI green budget:** orchestrator should never merge a PR with red CI. If a PR is red, the ticket goes back to the agent for a fix attempt; after 2 failed attempts, escalate to human.

---

## 8. Secrets & credentials (locations, not values)

| Secret | Where it lives | Who reads it |
|---|---|---|
| Claude API key | `local.properties` → BuildConfig.CLAUDE_API_KEY_DEFAULT | Bundled in APK; runtime override in encrypted prefs |
| OpenRouter API key | `E:\Projects\CaseForge\.env` line 10, also `~/OneDrive/Desktop/.env` line 17 | For agent design review only — not bundled in APK |
| GitHub PAT (workflow scope) | User's git credential cache + setup-github.bat history | For pushes |
| ADB pairing key | Tablet ↔ Windows machine; assumed paired already | For sideloading test builds |

**The orchestrator should NOT ask the user for these values.** They're already where they need to be. Agents that need them read from the listed locations.

---

## 9. What I need from the human (Ricky) — the short list

For the orchestrator to deliver this without getting stuck:

1. **Run `push.bat`** in `E:\Projects\CaseForge\x431-ai-scanner\` to land the foundation that's sitting on disk. This is the one and only required step before agent dispatch.
2. **Confirm the GitHub PAT is still valid** (the one used last time has workflow scope). If CI starts failing with auth errors, the orchestrator asks for a new PAT.
3. **Be available for tablet tests** — about 10 manual smoke tests across the build cycle, each ~30 seconds. Orchestrator queues them into a single batched message when reasonable.
4. **Make ONE decision when the orchestrator asks:** "Hybrid AI (cloud + local fallback)" or "Cloud-only AI for now"? The orchestrator can default to cloud-only and ship E1 research separately if the user doesn't answer.
5. **Confirm legality assumption:** the user owns the tablet, owns the X431 subscription, and is using this for personal workshop work. No redistribution. (Restated for the record because Phase 2 cloning will reference this if/when we revisit it.)

Things the human does NOT need to do:
- Touch any code
- Manage merge conflicts (orchestrator does that)
- Pick which agent gets which ticket (orchestrator does that)
- Deal with branch hygiene (orchestrator does that)
- Re-clone X431 (deferred)
- Provide any new API keys (everything is already where it needs to be)

---

## 10. Rollback procedure

If the build cycle goes sideways at any point and the user wants to revert:

1. Orchestrator force-pushes `main` to the last known-good build SHA (recoverable from the `latest` GitHub release tag — that tag points at whatever was last verified working).
2. CI rebuilds; in-app updater on the tablet picks up the older APK on next poll.
3. If the in-app updater is also broken, manual sideload via:
   ```
   adb install -r app-debug-latest.apk
   ```
   (the user has this APK file at `E:\Projects\CaseForge\x431-ai-scanner\app-debug-latest.apk` from previous builds.)

The Phase 1 overlay can also be disabled at runtime: long-press bubble or use the notification "Dismiss overlay" action. So even a buggy overlay never bricks the user's workflow — they can fall through to using X431 directly.

---

## 11. Risk register

| Risk | Likelihood | Severity | Mitigation |
|---|---|---|---|
| Compose-in-Service lifecycle wiring has a subtle bug | Medium | High | A1 verifies before any other agent dispatches; orchestrator gates everything on A1 green. |
| 750ms scraper polling pegs CPU on full-scan progress screen | Medium | Medium | A2 EngineDriver should debounce: only re-scrape on accessibility events, not on a timer. Re-spec A2 if first implementation feels janky. |
| Overlay z-order quirks on Launch's custom Android skin | Low | High | Peek button + emergency dismiss gesture (D1) are escape hatches. Orchestrator schedules D1 early. |
| X431 menu structure differs across PRO / PROS / V+ models | High | Low | CapabilityMap paths are substring matches, tolerant of slight wording differences. OEM agents (B2-B9) should explicitly note model variants when known. |
| Anthropic API rate-limits during heavy testing | Low | Medium | Existing backoff (1/2/4/8s on 429/529/503) handles this. Orchestrator alerts user if it sees repeated 429s in action log. |
| Two agents stomp each other's edits on a shared file | Medium | Medium | §5 conflict map + serial dispatch on shared files. |
| OEM capability JSON has malformed entries | Medium | Low | B1 CapabilityRegistry validates entries on load, drops invalid ones, logs warnings. Bad data degrades gracefully — never crashes the app. |

---

## 12. Definition of done for this build cycle

The user can:

1. Install the latest APK (build ≥#40-ish) via the in-app updater.
2. Open Launch AI; complete the existing setup wizard.
3. Tap **"Take over X431 (custom UI)"** on the dashboard.
4. See a full-screen Launch AI overlay covering X431.
5. Pick a category tab (Scan / Codes / LiveData / Service / etc.) and tap a capability card.
6. Watch the action log show step-by-step X431 navigation in real time.
7. See the result (DTCs / live data / "successful" marker) rendered in our UI.
8. Tap **Peek** to verify X431 is responding underneath.
9. Tap **Dismiss** to exit cleanly.
10. Repeat 3-9 indefinitely without crashes.

Plus:

11. `capabilities/capabilities.json` in the repo has ≥240 OEM-specific entries (≥30 per OEM × 8 OEMs).
12. CI is green on `main`.
13. `TESTING.md` checklist has every scenario marked pass.
14. No regression in the existing dashboard / setup wizard / VIN detection / agent loop / Settings flow.

---

## 13. Communication protocol for the orchestrator

When the orchestrator is running this build cycle:

- **Status reports to the human:** every ~3 completed tickets, a one-paragraph summary ("Just merged A2/A3/A4. CI green. 2 tablet tests queued for you whenever — see message.").
- **Tablet test requests:** batched, never one-at-a-time. Each batch is a numbered checklist the user can run through in 2-3 minutes.
- **Blockers:** surface immediately. Don't burn cycles guessing at user intent.
- **End-of-cycle:** a final summary of all merged tickets + a fresh BUILD-BRIEF.md updated for the NEXT build cycle (Phase 2 clone, or Local LLM, or new features).

The orchestrator should NOT:

- Make architectural decisions the user hasn't approved.
- Touch the original X431 APK (Phase 2 deferred).
- Push to GitHub except via PR + merge — never direct to `main`.
- Bypass CI red.
- Spawn more than 10 concurrent agents (diminishing returns + merge mayhem).

---

## 14. Appendix — glossary

| Term | Meaning |
|---|---|
| **X431** | Launch's diagnostic app + VCI dongle ecosystem. Tablet ships with this preinstalled. |
| **VCI** | Vehicle Communication Interface — Launch's Bluetooth/USB dongle that plugs into the car's OBD-II port. |
| **PRO / PROS / V+** | X431 hardware tablet model tiers. Slight UI variation between them. |
| **Overlay** | Android `TYPE_APPLICATION_OVERLAY` window drawn over other apps. |
| **Accessibility** | `AccessibilityService` API — lets us read other apps' UI and dispatch taps. |
| **Capability** | A diagnostic procedure (full scan, oil reset, key program, etc.) expressed as a menu path + done-marker. |
| **CapabilityMap** | The registry of all known capabilities. Baseline in Kotlin; OEM extensions in JSON. |
| **DTC** | Diagnostic Trouble Code (e.g., P0420 = catalyst efficiency below threshold). |
| **OEM** | Original Equipment Manufacturer (Ford, GM, Toyota, etc.). |
| **Mode 01/03/04/07** | SAE J1979 generic OBD-II protocol modes — live data, stored DTCs, clear codes, pending DTCs. |
| **Bidirectional** | Diagnostic test where the tool actively commands the ECU (e.g., cycle the fuel pump on). |
| **Phase 1** | Overlay over original X431. What we're building now. |
| **Phase 2** | Cloned + rebranded X431. Deferred. |

---

## 15. End-of-brief checklist for the orchestrator

Before dispatching the first agent, confirm:

- [ ] `push.bat` was run; CI built green (≥ #37).
- [ ] Read this entire brief.
- [ ] Read `PLAN-OVERLAY-CLONE.md` for additional context.
- [ ] Reviewed the current state of `engine/`, `overlay/`, and `MainActivity.kt` to verify foundation lines up with this brief.
- [ ] Dispatched A1 (verify overlay) as the gating ticket.
- [ ] Pre-queued the parallel batch (A2-A6, B1-B9, C1-C2, D1-D3) to fire as soon as A1 merges.

Good luck. The hardest part of this is keeping agents from stepping on each other on shared files — get §5 right and the rest is mechanical.
