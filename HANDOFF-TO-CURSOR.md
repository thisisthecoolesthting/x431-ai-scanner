# HANDOFF TO CURSOR — Together Scanners AI

You (Cursor, with Codex as a sub-agent) are taking over this build from Claude. Claude is no longer in the loop. Your goal: complete the app and ship a working APK to the tablet.

Use **every agent you have available** — Cursor Composer, Cursor Background Agents, Codex CLI — in maximum parallel fan-out. Don't ask the human (Ricky) for permission on routine work. Push directly to `main` on feature commits, fix CI red immediately, keep moving.

---

## What this app is

**Together Scanners AI** (formerly "Launch AI") is an Android app that runs on a Launch X431 PRO/V+ automotive diagnostic tablet. Phase 1 architecture: a full-screen Compose overlay that sits on top of Launch's stock X431 app, scrapes its UI via Android AccessibilityService, builds a typed state model, and drives X431 underneath with our own UI. The technician sees only Together; X431 runs invisibly. Claude (via the Anthropic API baked into the app) handles diagnosis reasoning at app runtime — but Claude is NOT involved in BUILDING the app from here.

Two repos:
- **Public:** `github.com/thisisthecoolesthting/x431-ai-scanner` — this repo, the buildable Android app, GitHub Actions CI publishes a rolling `latest` debug APK release.
- **Private:** `github.com/thisisthecoolesthting/together-decompile` — APK-verified findings from JADX decompile of x431Diagnose v8.00.029 (VCI Bluetooth protocol, diagnostic engine, menu tree, brand assets). Use `gh repo clone thisisthecoolesthting/together-decompile` to access.

---

## What's already shipped to `main` (do NOT rebuild)

- **Foundation** (`engine/`): EngineState, EngineScraper (UI scraper), CapabilityMap (244 baked entries), CapabilityCatalogStore (JSON hot-patch loader), EngineHealthMonitor.
- **Overlay** (`overlay/`): FullScreenOverlayService with state persistence + emergency dismiss; OverlayRoot Compose router; 7 ScreenKind renderers (ModuleList, LiveData, Actuation, Report, Loading, Error, ObdScan); first-run onboarding pager; Material3 theme with warm-amber brand seed `0xFFE07A1F`.
- **Agent loop**: existing Claude tool-use loop in `agent/` (DO NOT TOUCH); AgentRunner, AgentTools, AgentActionLog, ScannerAccessibilityService.
- **UI**: setup wizard, dashboard with reactive VIN, bubble launcher (long-press → full overlay), Settings (encrypted API key, overlayOnX431 toggle), TTS spoken status, action log + replay.
- **Data**: Customer/RO/Reports DB (Room), PDF report generator, cost tracker, OEM playbooks, NHTSA VIN decode + recalls, ELM327 Bluetooth driver.
- **Infra**: in-app updater, GitHub Actions CI (workflow at `.github/workflows/build.yml`), boot receiver for overlay restore, App.kt singletons.

**Latest commit at handoff:** `fd03fe3` — BootReceiver fix. Build #52 firing. If red, that's your first job (see task 001).

---

## What's drafted but NOT YET integrated (in `E:\Projects\launch-ai-dispatch\drafts\` — your job to integrate)

Each draft folder contains Kotlin source already written by Claude's sub-agents. Your job: integrate, compile-check, push.

| Folder | Feature | Status |
|---|---|---|
| `drafts/F1-NEXT-TEST/` | Predictive Next-Test (AI suggests next diagnostic step from DTC + freeze-frame) | code complete, needs integration |
| `drafts/F2-CORRELATOR/` | Cross-Module DTC Root-Cause Correlator | code complete |
| `drafts/F3-REPAIR-STORY/` | Evidence Capture (camera) + Repair Story PDF | code complete |
| `drafts/F5-VOICE/` | Hands-Free Voice Mode ("Hey Together…") | code complete |
| `drafts/F7-SEQUENCES/` | Multi-Step Test Sequences (relative compression, EVAP, etc.) | code complete |
| `drafts/F8-RECALL-FLAG/` | NHTSA Recall/TSB Auto-Flag (cross-reference VIN + DTCs) | code complete |
| `drafts/F10-VCI-SPIKE/` | Direct VCI Bluetooth Protocol (Phase 2 spike — bypass X431 entirely) | scaffolding only, see SPIKE-REPORT.md |
| `drafts/OEM-FIX/` | 47 OEM capability path corrections | drop-in replacement of 8 OEM JSONs |

---

## What's still TODO (your real work)

Numbered task prompts are in `cursor-dispatch/outbox/`. Pick them up in numeric order. Move to `cursor-dispatch/done/` when complete. House style for prompts matches Ricky's CaseForge convention.

### Critical path (in order)

1. **Confirm CI green** (task 001) — verify build #52+ green on `main`. If red, fix.
2. **Integrate F8** (task 002) — Recall/TSB Auto-Flag. Simplest. Validates the integration pattern.
3. **Integrate F3** (task 003) — Evidence Capture + Repair Story PDF. Highest customer-visible value.
4. **Integrate F1** (task 004) — Predictive Next-Test. Uses Claude tool-use at runtime via existing AgentRunner.
5. **Integrate F2** (task 005) — Cross-Module Correlator.
6. **Integrate F5** (task 006) — Voice Mode.
7. **Integrate F7** (task 007) — Multi-Step Test Sequences.
8. **Apply OEM corrections** (task 008) — replace 8 OEM JSON files; re-merge baseline; push.
9. **Phase 2 spike: Direct VCI** (task 009) — see `drafts/F10-VCI-SPIKE/SPIKE-REPORT.md`. Two-week budget. Goal: prove Mode 03 DTC read direct-via-VCI without X431. Document gaps.
10. **Tablet smoke pass** (task 010) — full TESTING.md checklist on the actual X431 tablet.

### Parallelization advice

You have Cursor Composer + Background Agents + Codex CLI. Use them like this:
- **Cursor Background Agent**: take the WHOLE task queue and run it autonomously. Push PRs / direct commits as appropriate.
- **Codex CLI**: feed it tasks 004, 005, 007 (heavier code) in parallel via the cursor-dispatch outbox.
- **Cursor Composer**: drive tasks 002, 003, 006, 008 yourself (interactive UI/Compose work).
- **Task 009 (VCI spike)**: dedicated Codex CLI session, runs against the private decompile repo. Two weeks of focused work. Worth a separate branch (`spike/direct-vci`).

Max useful concurrency: ~10 active streams. More than that and you'll hit merge mayhem.

---

## Build + deploy loop

CI workflow at `.github/workflows/build.yml`. Pinned versions:
- AGP 8.11.2, Kotlin 2.0.20, Gradle 8.13 (in both wrapper AND workflow `gradle-version`), Compose BOM per `app/build.gradle.kts`, Material3 latest stable, Room 2.6.1.

Build loop:
1. Make changes locally.
2. `git add . && git commit -m "..." && git push origin main` (or a feature branch + PR).
3. CI runs `gradle :app:assembleDebug --no-daemon`.
4. On green: a rolling `latest` GitHub Release is published with the debug APK at `https://github.com/thisisthecoolesthting/x431-ai-scanner/releases/latest/download/app-debug.apk`.
5. The tablet's in-app updater polls that URL.
6. On red: read failure log via `gh run view <id> --log-failed`. Fix. Re-push.

Working clone: `C:\Users\reasn\Desktop\x431-foundation-push\`. This is the canonical path. Do NOT use `E:\Projects\CaseForge\x431-ai-scanner\` — that's inside a different parent git repo pointing at a different remote.

---

## House rules — DO NOT VIOLATE

See `.cursor/rules/together-rules.md` for the full set. Quick highlights:

**Never touch:**
- `agent/AgentRunner.kt`, `agent/AgentTools.kt`, `agent/AgentActionLog.kt`, `agent/ScannerAccessibilityService.kt` (unless A6-style ticket explicitly says so), `ai/ClaudeClient.kt`, `ai/Prompts.kt`, `ai/RepairInfoLookup.kt`, `overlay/OverlayService.kt` (the bubble — A5 already touched it), `ui/wizard/*`, in-app updater code, `local.properties`, `.gradle/`, `build/`, top-level Gradle plugin versions.

**Always:**
- AGP/Kotlin/Gradle versions stay pinned. Bump only if a ticket explicitly says so.
- Commit messages: imperative mood, concise. Examples below.
- No emojis in code or commits.
- Tests are not in CI's `assembleDebug` path, but keep them compilable.
- The CLAUDE.md at repo root has the autonomy law for Claude sessions — preserve it; Cursor doesn't need to honor it but human users with Claude may.

**Commit message examples (Ricky's preferred style):**
```
Bundle: F8 Recall/TSB auto-flag (NHTSA cross-reference)
Fix CI: missing import androidx.compose.ui.unit.dp in ActuationScreen
F10 SPIKE: VciFrame encode/decode + checksum (XOR over bytes[2..len-2])
```

---

## Decompile findings (use these to inform feature implementation)

In the private repo `together-decompile/findings/`:
- `000-apk-acquisition.md` — how the APK was obtained (USB MTP).
- `010-vci-bluetooth-protocol.md` — wire frame format, SPP UUID, checksum algorithm, opcode candidates. The basis for F10.
- `011-diagnostic-engine.md` — DiagnoseService Messenger dispatcher, DiagnoseBusiness JNI bridge, DiagnoseLogicBusiness branch table. Use this to map X431 internal flows when EngineScraper isn't enough.
- `012-menu-tree-apk-verified.md` — Real X431 menu paths. 47 corrections already in `drafts/OEM-FIX/` — apply them (task 008).
- `013-brand-assets.md` — Drawables / strings / icons we'd swap for Together branding in the eventual Phase 2 standalone clone.

Decompiled source itself: `E:\Projects\together-decompile\decompiled\sources\com\cnlaunch\` — 34,995 files. Unobfuscated packages: `bluetooth`, `socket`, `diagnosemodule`, `diagnostic`, `x431`, `x431pro`. Read these when researching VCI protocol details for F10.

JADX is installed at `C:\Tools\jadx\bin\jadx.bat` — re-decompile any updated APK with `jadx -d <output> --show-bad-code <apk>`.

---

## Capability expansion roadmap (from the synthesis)

Full doc at `E:\Projects\launch-ai-dispatch\CAPABILITIES-OPUS-SYNTHESIS.md`. The Tier 1 features are already drafted (F1, F2, F3, F5). Tier 2 has F7 and F8 drafted. Tier 3 is F10. After the Tier 1+2 features ship, the next compounding work is:

- **Fix-Fleet feedback DB**: post-repair, tech marks fixed/not-fixed. Anonymized `{VIN_prefix, mileage_range, engine, DTC_set, confirmed_fix, outcome}` accumulates in a private cloud. Builds proprietary moat. Backend non-trivial — needs a tiny FastAPI service or Cloudflare Workers KV. Defer until after the F1-F8 integrations land.
- **Live-data anomaly detection**: background AI watches PIDs and flags subtle anomalies. Composes with EngineScraper buffer + Claude pattern analysis.
- **AR component locator**: tablet camera highlights parts in engine bay. ML model training required — Hold.

---

## How to use this handoff

1. **Cursor opens this repo** (`git clone https://github.com/thisisthecoolesthting/x431-ai-scanner.git`). Make sure you're at commit `fd03fe3` or later (`main`).
2. **First task: read `HANDOFF-TO-CURSOR.md` (this file) and `.cursor/rules/together-rules.md`.**
3. **Pull the private decompile repo** for findings reference: `gh repo clone thisisthecoolesthting/together-decompile` to `E:\Projects\together-decompile\` (or wherever).
4. **Mount the drafts folder** (read-only is fine): `E:\Projects\launch-ai-dispatch\drafts\` — pull files from here as you do each task.
5. **Start at `cursor-dispatch/outbox/001-fix-ci-confirm-green.prompt.md`** and work through in numeric order.
6. **Use all your agents.** Codex CLI for heavy refactor work, Cursor Background for autonomous queue draining, Cursor Composer for interactive UI iteration.
7. **Don't wait for Claude.** Claude is out of this loop. Make the calls yourself. If you genuinely need a human decision, Ricky is the human — ping him in chat.
8. **Definition of done**: tablet smoke test from `TESTING.md` passes all checkboxes, CI green on `main`, in-app updater serves the latest APK without crashes.

Good luck.
