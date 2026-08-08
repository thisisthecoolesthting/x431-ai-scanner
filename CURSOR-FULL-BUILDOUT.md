# CURSOR FULL BUILD-OUT PLAN — Together Scanners AI

**You are Cursor. Codex CLI is your peer. Claude is out of the loop.** This plan supersedes `CURSOR-MASTER-PLAN.md` and is the complete spec from here to shipped app. Read everything, then execute without coming back for permission. Ricky is observer.

## North-star outcome

A Together Scanners AI APK installed on the X431 tablet that:

1. **Phase 1 overlay**: drives X431 underneath with our UI; all Tier 1 features live (predictive next-test, cross-module correlator, evidence-capture + repair-story PDF, voice mode, multi-step sequences, recall/TSB flag).
2. **Phase 2 standalone**: a flag-gated "Direct VCI" mode that opens the dongle Bluetooth socket directly and reads DTCs without X431 running. Proven for Mode 03 minimum.
3. **Hardware-max pass**: after both phases ship green, a DeepSeek consultation surfaces what else the tablet hardware can do, then implements the top picks.

When all three are done, the in-app updater bumps the tablet to the final shipped APK and `cursor-dispatch/outbox/` is empty.

---

## Inventory — where everything lives

| Item | Location |
|---|---|
| Working repo (this clone) | wherever you have it; canonical name is `x431-ai-scanner` |
| Latest main | check `git log -1` |
| Repo URL | `https://github.com/thisisthecoolesthting/x431-ai-scanner` (public) |
| Private decompile repo | `gh repo clone thisisthecoolesthting/together-decompile` |
| Decompiled source (on Ricky's OfficePC) | `E:\Projects\together-decompile\decompiled\sources\com\cnlaunch\` (34,995 files) |
| Feature drafts (bundled in this repo) | `cursor-dispatch/drafts/` (F1, F2, F3, F5, F7, F8, F10, OEM-FIX, POLISH, REBRAND, MERGED, A1-CI-FIX) |
| Task queue | `cursor-dispatch/outbox/` numeric order |
| Done tasks | `cursor-dispatch/done/` |
| House rules | `.cursor/rules/together-rules.md` |
| Original handoff | `HANDOFF-TO-CURSOR.md` |
| Capability synthesis (DeepSeek + Gemini + Opus) | `cursor-dispatch/drafts/MERGED/` plus `E:\Projects\launch-ai-dispatch\CAPABILITIES-*.md` if Ricky's OfficePC is reachable |
| OpenRouter API key (for DeepSeek consultation) | `E:\Projects\CaseForge\.env` line starting `OPENROUTER` |
| JADX (for re-decompile if needed) | `C:\Tools\jadx\bin\jadx.bat` on OfficePC |

Build is **always CI-only**. Local build is for editing. CI: `gradle :app:assembleDebug --no-daemon` via `.github/workflows/build.yml`. Rolling release at `https://github.com/thisisthecoolesthting/x431-ai-scanner/releases/latest/download/app-debug.apk`. Tablet's in-app updater polls it.

---

## PHASE 1 — Overlay finish (parallel, ~1 day wall clock)

Already shipped through prior Cursor commits: F1 Predictive Next-Test (#1), F8 Recall/TSB Auto-Flag, F5 Voice Mode (#3), F7 Multi-Step Sequences (#4), plus their CI fixes. Current good build = **#68** at commit `f02be3e`.

### Remaining Phase 1 tasks

| Task | What | Stream |
|---|---|---|
| 003 | F3 Evidence Capture + Repair Story PDF | A — Cursor Composer (camera + Compose) |
| 005 | F2 Cross-Module DTC Correlator | B — Codex CLI |
| 008 | Apply 47 OEM capability path corrections (from `cursor-dispatch/drafts/OEM-FIX/`) | A — Cursor Composer (mechanical) |
| 010 | Tablet smoke pass against `TESTING.md` | A — Cursor Composer + Ricky on tablet |

Each task's full spec is in `cursor-dispatch/outbox/`. Read before starting.

### Stream A — Cursor Composer (interactive UI work + final smoke)

Sequence: 003 → 008 → 010. Push 003 + 008 directly to main (small fixes per Stream A rule). For 010, ask Ricky to install the latest APK on the tablet and walk TESTING.md with you in Composer — record pass/fail per line; file fix tasks numbered 100+ for any failures.

### Stream B — Codex CLI

Open one Codex CLI session against this repo:

```
cd <repo>
codex
```

Paste:

> "Read cursor-dispatch/outbox/005-integrate-f2-correlator.prompt.md, the drafts at cursor-dispatch/drafts/F2-CORRELATOR/, and findings/011-diagnostic-engine.md in the private together-decompile repo (gh repo clone thisisthecoolesthting/together-decompile if you don't have it). Implement engine/DtcCorrelator.kt — reads EngineState.dtcs across modules, groups by likely root cause using a static rules table seeded from the decompiled DiagnoseLogicBusiness branches plus public OEM TSB patterns. Surface as a RootCauseCard on ReportScreen above the DTC list. Push to feat/f2-correlator and self-merge on CI green."

Stream B can run in parallel with Stream A's task 003 — different files.

### Phase 1 acceptance gate

- Build green on main.
- All TESTING.md checkboxes ticked.
- Tasks 003, 005, 008, 010 in `cursor-dispatch/done/`.
- 242+ entry capability JSON shipped at `app/src/main/assets/capabilities/capabilities.json`.

When all four pass → start Phase 2.

---

## PHASE 2 — Direct VCI Standalone (`spike/direct-vci` branch, 2-week budget)

Goal: prove and then production-harden a path where Together speaks the Launch VCI Bluetooth protocol directly without X431 running. Decompile findings already on disk give us 70% — Frida on the live tablet closes the remaining 30%.

### Scaffold on disk (ready to consume)

`cursor-dispatch/drafts/F10-VCI-SPIKE/`:
- `VciFrame.kt` — wire frame model (2-byte header + 2-byte BE opcode + 2-byte BE length + payload + XOR checksum). Encode/decode verified against decompiled `CommunicationCOM.getCrcByDataLength`.
- `VciSocketClient.kt` — Bluetooth SPP socket with hex-ASCII and raw-binary modes (one is wrong, Frida will confirm which).
- `VciOpcodes.kt` — opcodes from `DiagnoseConstants.java`, each tagged CONFIRMED / INFERRED / UNKNOWN.
- `VciCommunicator.kt` — readDtcs / clearCodes / fullScan / livePid / actuate stubs.
- `SPIKE-REPORT.md` — full confidence table, Frida hook one-liner, priority of unknowns.

### Phase 2 sub-stream — Codex CLI session 3 (dedicated)

Open a third Codex CLI session on a separate branch:

```
cd <repo>
git checkout -b spike/direct-vci
codex
```

Paste:

> "Phase 2 spike: prove direct-VCI DTC read without X431. Two-week budget. Read cursor-dispatch/outbox/009-spike-direct-vci-bluetooth.prompt.md and cursor-dispatch/drafts/F10-VCI-SPIKE/SPIKE-REPORT.md, plus findings/010-vci-bluetooth-protocol.md in the private together-decompile repo. Two unknowns to resolve via Frida hook on the live tablet: (1) header magic bytes — try 0x55 0xAA first, then sweep if wrong; (2) raw-binary vs hex-ASCII SPP transport — both modes are coded, just flip the boolean. Day 1: ride alongside the tablet via Frida-server, capture 50+ wire frames during a normal X431 scan, log them. Day 2: confirm header + transport, rebuild VciFrame if needed. Day 3-5: VciCommunicator.readDtcs() round-trip against the VCI dongle with X431 NOT running. Day 6-10: Mode 01 live-PID + Mode 09 VIN. Day 11-14: write integration into Together as a settings toggle 'Direct VCI (experimental)'. Push everything to spike/direct-vci. NEVER merge to main without Ricky's explicit approval — Phase 2 stays gated behind the toggle in main only when approved."

### Phase 2 acceptance gate

- `VciCommunicator.readDtcs()` returns real DTCs from a connected vehicle on the tablet with X431 NOT running.
- Settings toggle "Direct VCI (experimental)" added behind a feature flag.
- Either Mode 01 + Mode 09 also work, OR a clear gap report explains why not.
- Findings written to private `together-decompile/findings/020-vci-spike-result.md`.

When the spike branch hits its acceptance criteria, post-message Ricky for the merge call.

---

## PHASE 3 — Hardware-max consultation (after Phases 1 & 2 ship)

Once Phase 1 acceptance gate passes AND Phase 2 spike has shipped a verdict (works or documented gaps), trigger a DeepSeek call to surface the next wave of features that exploit the tablet hardware fully.

### Trigger script

Cursor (or any team member) runs this on a Windows machine with the OpenRouter key in `.env`:

```powershell
$line = (Get-Content 'E:\Projects\CaseForge\.env') | Where-Object { $_ -match '^OPENROUTER' } | Select-Object -First 1
$key = ($line -split '=', 2)[1].Trim().Trim('"').Trim("'")
$promptText = Get-Content '.\PHASE3-PROMPT.md' -Raw  # write this file first, see below
$body = @{
    model = 'deepseek/deepseek-chat'
    messages = @(@{ role = 'user'; content = $promptText })
    max_tokens = 4096
    temperature = 0.7
} | ConvertTo-Json -Depth 5
$headers = @{ 'Authorization' = "Bearer $key"; 'Content-Type' = 'application/json' }
$r = Invoke-RestMethod -Uri 'https://openrouter.ai/api/v1/chat/completions' -Method Post -Headers $headers -Body $body -TimeoutSec 150
[IO.File]::WriteAllText('PHASE3-RESPONSE.md', $r.choices[0].message.content, [Text.UTF8Encoding]::new($false))
```

### What goes in `PHASE3-PROMPT.md`

Write this file at repo root, then run the script above:

```
You are reviewing Together Scanners AI after Phase 1 + Phase 2 have shipped. Phase 1 = full AI overlay over Launch X431 with predictive next-test, cross-module DTC correlation, evidence-capture + repair-story PDF, hands-free voice mode, multi-step diagnostic sequences (compression / EVAP / injector kill / VVT / parasitic draw), NHTSA recall + TSB flagging, 240+ OEM capability entries across Ford/GM/Stellantis/Toyota/Honda/VAG/BMW/Mercedes. Phase 2 = Direct VCI Bluetooth standalone mode (bypasses X431 entirely) gated behind a settings toggle; Mode 01/03/09 working.

Hardware available on the X431 PRO / PROS / V+ tablet: Android 10-12, Snapdragon 6xx-class SoC, 4-8 GB RAM, 64-128 GB storage, 10-inch IPS display, rear camera, microphone, Bluetooth 5, Wi-Fi, optional cellular, GPS, on some models a 2-channel oscilloscope ADC.

Question for you: what's a ranked list of NEW capabilities that maximize this hardware that we have NOT already built? Focus on:
- On-device local LLM fallback for offline diagnosis (Phi-3-mini / Gemma 2B / Llama 3.2 1B — which fits best, what tok/sec on this SoC class)
- Camera as a diagnostic input: VIN OCR (no manual entry), QR/barcode scan for parts, photograph engine bay → AI identifies leaking component
- AR overlay on engine bay (ARCore + tablet camera): highlight the part the AI is talking about
- Microphone as a sensor: listen to engine for misfire detection, exhaust leak detection, knock sensor surrogate
- GPS + cellular: fleet mode (multi-vehicle workflow), location-tagged scan history, recall push-notifications when a customer's VIN gets a new TSB
- Oscilloscope ADC (on PRO5+ models): replace the dedicated scope app entirely; tied into our scan UI
- Multi-modal local AI: vision + voice + text in one flow ("show me where you hear the noise")
- Background daemons: pre-fetch vehicle DB updates, idle-time DTC pattern learning, overnight model fine-tuning

For each idea: name, what-it-does in one paragraph, why-it-matters in the workshop, what hardware it composes with, build complexity (S/M/L), and what existing Together piece it builds on top of. Aim 12-18 ideas ranked best to most speculative. No fluff.
```

### Phase 3 acceptance gate

DeepSeek's response lands as `PHASE3-RESPONSE.md`. Cursor reads it, picks the top 3 ideas, drafts them as new tasks (numbered 100+) into `cursor-dispatch/outbox/`, and starts integrating. Each top-3 idea gets its own feature branch. When all three ship green on main, Phase 3 is done.

---

## Common pitfalls already encountered (do not re-hit)

- Accompanist pager is deprecated. Use `androidx.compose.foundation.pager.HorizontalPager` + `rememberPagerState(initialPage = 0) { pageCount }`.
- `awaitFirstDown` / `awaitLongPressOrCancellation` must live inside `awaitEachGesture { }`. Imports from `androidx.compose.foundation.gestures.*`.
- Material3 `dynamicColorScheme` doesn't exist; use `dynamicLightColorScheme(context)` / `dynamicDarkColorScheme(context)`.
- `kotlinx.coroutines.isActive` is a CoroutineContext extension — import it explicitly. Inside a suspend function use plain `return`, NOT `return@launch` (the label is only valid inside the launch lambda).
- `UsageStatsManager` lives at `android.app.usage.UsageStatsManager` NOT `android.app.UsageStatsManager`.
- Nullable `Boolean?` doesn't have `!` operator — use `if (someBool != true)`.
- AGP 8.11.2 requires Gradle 8.13. Bump BOTH `gradle/wrapper/gradle-wrapper.properties` AND `.github/workflows/build.yml`'s `gradle-version`.
- The existing `object CapabilityMap` (foundation) and our JSON `CapabilityCatalog` (B1) are TWO different types. Don't name-collide.
- Never modify: `agent/AgentRunner.kt`, `agent/AgentTools.kt`, `agent/AgentActionLog.kt`, `ai/ClaudeClient.kt`, `ai/Prompts.kt`, in-app updater, setup wizard.
- For PowerShell edits via PowerShell.MCP (if you use it), pass file content via `var1`-`var4` of `invoke_expression` — that bypasses parser expansion of `$` and backticks.

---

## KICKOFF — paste into Cursor Composer to launch the whole thing

```
Read CURSOR-FULL-BUILDOUT.md, HANDOFF-TO-CURSOR.md, and .cursor/rules/together-rules.md.

Execute the three phases in order:

PHASE 1 — overlay finish. You take Stream A (tasks 003, 008, 010). Spawn one Codex CLI session for Stream B (task 005). Push small fixes to main directly; Stream B opens a feat/f2-correlator branch and self-merges. Phase 1 done when all four tasks in cursor-dispatch/done/ and build green on main.

PHASE 2 — Direct VCI standalone spike on branch spike/direct-vci. Spawn a second Codex CLI session per the "Phase 2 sub-stream" prompt. 2-week budget. NEVER merge to main without Ricky's explicit approval — keep gated behind a settings toggle when eventually approved.

PHASE 3 — hardware-max consultation. Once Phase 1 acceptance gate passes AND Phase 2 spike has shipped a verdict, write PHASE3-PROMPT.md from the spec in CURSOR-FULL-BUILDOUT.md, run the OpenRouter PowerShell script, read PHASE3-RESPONSE.md, draft the top-3 ideas as cursor-dispatch/outbox/100-, 101-, 102- numbered tasks, integrate each.

Do not ask permission for routine work. Use Codex CLI in parallel wherever the plan says. Push directly to main only for small fixes. Open feature branches and self-merge on CI green for anything substantive. Stop only when all three phases are complete or a true blocker requires human input.

Go.
```

---

## When all three phases are done

The shipped Together Scanners AI APK on the tablet has:

- Full overlay UI driving X431
- Direct VCI standalone mode behind a toggle
- Hardware-max features (top 3 from DeepSeek consultation)
- All TESTING.md scenarios green
- Empty `cursor-dispatch/outbox/`

That's the product. Hand back to Ricky for go-to-market.
