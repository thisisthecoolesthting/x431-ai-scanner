# CURSOR MASTER PLAN — Finish Together Scanners AI

**You are Cursor.** You have Composer, multiple Background Agents, and Codex CLI available. **Use every one of them in parallel.** Claude is out of this loop. Ricky (the human) is observer; don't ask permission for routine work.

## Goal

Ship a green build of Together Scanners AI to the tablet that passes the full TESTING.md checklist. Two layers:
- **Phase 1 overlay** — finish it (~6 feature integrations + OEM corrections + tablet smoke). MUST ship.
- **Phase 2 spike** — explore direct VCI Bluetooth bypass. 2-week budget. Don't block Phase 1 on it.

## Where everything is

| What | Where |
|---|---|
| Working repo | `C:\Users\reasn\Desktop\x431-foundation-push\` (this folder) |
| Latest main | commit `9d95c78` — last CI compile fix |
| Full context | `HANDOFF-TO-CURSOR.md` (read this first) |
| House rules | `.cursor/rules/together-rules.md` (never-touch list, version pins, Compose conventions) |
| Task queue | `cursor-dispatch/outbox/` — 10 tasks in numeric order |
| Feature drafts ready to integrate | `E:\Projects\launch-ai-dispatch\drafts\` (F1, F2, F3, F5, F7, F8, F10, OEM-FIX) |
| Decompile findings | private repo: `gh repo clone thisisthecoolesthting/together-decompile` |
| Decompiled source | `E:\Projects\together-decompile\decompiled\sources\com\cnlaunch\` (34,995 files) |
| JADX | `C:\Tools\jadx\bin\jadx.bat` |
| Capability synthesis | `E:\Projects\launch-ai-dispatch\CAPABILITIES-OPUS-SYNTHESIS.md` |
| Tablet data copier | `E:\Projects\copy-tablet-data.bat` |

## Parallel dispatch — fan out across 6 streams

You can run all six concurrently. Each stream runs independent of the others.

### STREAM A — Cursor Composer (you, interactive)
Verify CI green first. Then integrate the polish tickets that benefit from interactive Compose work.

- Task 001: confirm CI green (fix any red)
- Task 002: F8 Recall/TSB Auto-Flag
- Task 003: F3 Evidence Capture + Repair Story PDF
- Task 008: Apply OEM corrections
- Task 010: Tablet smoke pass (when human is available)

### STREAM B — Cursor Background Agent #1
Spawn this Background Agent with:
> "Read HANDOFF-TO-CURSOR.md and .cursor/rules/together-rules.md. Then claim cursor-dispatch/outbox/004 (Predictive Next-Test). Implement, push, move to done/. Then claim 005 (Cross-Module Correlator) and do the same. Push directly to main. Don't ask permission."

### STREAM C — Cursor Background Agent #2
Spawn this Background Agent with:
> "Read HANDOFF-TO-CURSOR.md and .cursor/rules/together-rules.md. Then claim cursor-dispatch/outbox/006 (Voice Mode). Implement, push, move to done/. Then claim 007 (Multi-Step Sequences) and do the same. Push directly to main. Don't ask permission."

### STREAM D — Codex CLI session 1 (heavy code refactor)
In a separate terminal:
```
cd C:\Users\reasn\Desktop\x431-foundation-push
codex
```
Paste:
> "Read HANDOFF-TO-CURSOR.md, .cursor/rules/together-rules.md, and cursor-dispatch/outbox/004-integrate-f1-predictive-next-test.prompt.md. Help Background Agent #1 by writing the agent/NextTestSuggester.kt file and the SuggestedTestCard Compose component. Push to a feat/f1-next-test branch and open a PR."

### STREAM E — Codex CLI session 2 (heavy code refactor)
Separate terminal:
```
cd C:\Users\reasn\Desktop\x431-foundation-push
codex
```
Paste:
> "Read HANDOFF-TO-CURSOR.md, .cursor/rules/together-rules.md, and cursor-dispatch/outbox/007-integrate-f7-multistep-sequences.prompt.md. Implement the 5 diagnostic sequences (relative compression, EVAP smoke, injector kill, VVT sweep, parasitic draw bisection). Each sequence = a DiagnosticSequence extending Capability with steps. Push to feat/f7-sequences branch."

### STREAM F — Codex CLI session 3 (Phase 2 VCI spike, separate branch)
Separate terminal, separate branch:
```
cd C:\Users\reasn\Desktop\x431-foundation-push
git checkout -b spike/direct-vci
codex
```
Paste:
> "Read cursor-dispatch/outbox/009-spike-direct-vci-bluetooth.prompt.md and drafts/F10-VCI-SPIKE/SPIKE-REPORT.md. Two-week budget. Goal: prove Mode 03 DTC read direct-via-VCI without X431. Two critical unknowns: header magic bytes (probably 0x55 0xAA) and raw-binary vs hex-ASCII SPP transport. Use Frida runtime hook on LocalSocketClient.send() to capture real wire frames. Document findings in findings/. Make me a runnable VciCommunicator.readDtcs() proof. Push to spike/direct-vci branch only — do NOT merge to main without explicit Ricky approval."

## Merge coordination

- Background Agents may step on each other if they push to main simultaneously. **Resolution: each Stream B/C agent push to a feature branch first, open PR, self-merge when CI green. Streams D/E already do this.** Only Stream A pushes directly to main (small fixes).
- If a feature branch fails CI, the owner stream fixes it before the next ticket. Don't pile up.
- When all overlay tickets (001-008) are green on main, the overlay is shipped. Task 010 (tablet smoke) gates final acceptance.

## Stop conditions

- **Phase 1 done** when: build is green on main, all 8 OEM JSONs have corrected paths, tasks 001-008 are in `cursor-dispatch/done/`, tablet smoke (task 010) passes every TESTING.md checkbox.
- **Phase 2 spike done** when: `spike/direct-vci` branch has a working `VciCommunicator.readDtcs()` against the tablet's actual VCI dongle OR has a documented gap report explaining what's still missing. Either outcome ships findings to the private together-decompile repo.

## Build / deploy loop reminder

CI runs `gradle :app:assembleDebug --no-daemon` on every push. On green, rolling latest release at `https://github.com/thisisthecoolesthting/x431-ai-scanner/releases/latest/download/app-debug.apk`. Tablet's in-app updater polls that URL. To fix red: `gh run view <id> --log-failed`.

Common pitfalls already encountered (don't re-hit):
- Accompanist pager is deprecated → use `androidx.compose.foundation.pager.HorizontalPager` + `rememberPagerState(initialPage = 0) { pageCount }`
- `awaitFirstDown` / `awaitLongPressOrCancellation` need `awaitEachGesture { }` wrapper from `androidx.compose.foundation.gestures.*`
- `dynamicColorScheme` → use separate `dynamicLightColorScheme()` / `dynamicDarkColorScheme()`
- Top-level `kotlinx.coroutines.isActive` is an extension on `CoroutineScope` AND `CoroutineContext` — import it explicitly; inside suspend funs use `coroutineContext.isActive`; only use `return@launch` inside the launch lambda, plain `return` inside suspend functions
- `UsageStatsManager` is in `android.app.usage` not `android.app`
- Nullable Boolean → `if (someBool != true)` not `if (!someBool)`
- AGP 8.11.2 needs Gradle 8.13 (both wrapper AND CI workflow `gradle-version`)
- For Kotlin file edits: use `Add-LinesToFile`, `Update-LinesInFile`, `Update-MatchInFile`, `Remove-LinesFromFile` with content passed via `var1`-`var4` of `invoke_expression` to avoid PowerShell parser expansion of `$` and backticks

## KICKOFF — paste this into Cursor Composer to start

```
Read HANDOFF-TO-CURSOR.md, .cursor/rules/together-rules.md, and CURSOR-MASTER-PLAN.md.

Then execute the 6-stream parallel dispatch plan. You take Stream A. Spawn Background Agent #1 and #2 with the prompts from CURSOR-MASTER-PLAN.md sections "STREAM B" and "STREAM C". For Streams D, E, F — open new terminals and start three Codex CLI sessions with the prompts from those sections.

Push to main directly for Stream A small fixes. Stream B/C agents and Streams D/E open feature-branch PRs and self-merge on CI green. Stream F stays on spike/direct-vci branch and does NOT merge to main without Ricky approval.

Phase 1 done = all of cursor-dispatch/outbox/001-008 moved to done/ and build green on main and tablet smoke (task 010) passes.

Don't ask permission for routine work. Stop only when Phase 1 is done or a blocker requires human input.
```

## Final note

Everything you need is already on disk or in the repo. The 8 feature drafts in `E:\Projects\launch-ai-dispatch\drafts\` are working Kotlin — your job is integration, conflict-resolve, push, CI-green. Don't redo the drafting work.

If you discover a missing piece, ADD a new task `cursor-dispatch/outbox/NNN-<slug>.prompt.md` (next monotonic number) and proceed.

Go.
