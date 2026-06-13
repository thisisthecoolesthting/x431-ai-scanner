# Handoff to Cursor — unblock the standalone-scanner APK (CI rebrand gate)

**Date:** 2026-06-13
**Repo:** `C:\Together\x431-ai-scanner` (remote `github.com/thisisthecoolesthting/x431-ai-scanner`, branch `main`)
**From:** Claude (Cowork session). All code below is already committed and pushed.

---

## TL;DR
The standalone scanner got reviewed, compile-checked fixes (connection robustness + stability + UI). They are **pushed** (`36a7d51`, `8f251b8`). The APK is **NOT building** because CI fails at the **rebrand grep gate** *before* the build job. The failure is **not the scanner code** and is **not reproducible locally**. Get CI green → APK auto-publishes. Steps in §4.

---

## 1. Git state
```
8f251b8  connection robustness (command serialization + link-drop recovery)   <- HEAD, pushed
36a7d51  connection + stability + UX fixes; add PC ELM327 companion           <- pushed
b92cca0  Fix UsbVciProbe OEM_DIAG_PACKAGES reference for compile              <- LAST GREEN CI (build #132)
```
Local `main` == `origin/main`. Working tree has many untracked `_tmp_*`/`_vps_*` scratch files — **never `git add -A`; leave them.**

## 2. The blocker
`.github/workflows/build.yml` runs job `rebrand-audit` (script `scripts/run-rebrand-grep.ps1`) BEFORE job `build`. On both new commits:
```
rebrand-audit => failure   (FAILED STEP: "Rebrand grep gate")
build         => skipped   (=> no APK published)
```
The gate greps `app/src`, `.github/workflows`, `scripts`, `README.md`, `docs/STANDALONE-vs-FACTORY-OVERLAY.md` for `\b(Launch|X431|x431|cnlaunch|CaseForge|caseforge)\b`, minus a long allow-list, and `exit 1` if anything remains. It prints `REBRAND FAIL - forbidden words remain:` + the offending lines.

## 3. What I found / couldn't find
- **Prime suspect:** `scripts/run-rebrand-grep.ps1` itself changed **+35/-10** between the last green build (`b92cca0`) and now (it was a pre-existing uncommitted edit that rode along in `36a7d51`). The gate's own logic changed → can flip pass→fail with no code cause. Run `git diff b92cca0..HEAD -- scripts/run-rebrand-grep.ps1` first.
- **Candidate offender:** `scripts/cowork-autonomy-law.md:40` contains `x431-foundation-push`. Allow-list has `x431-ai-scanner` but NOT `x431-foundation`, so `\bx431\b` matches. Likely the trigger.
- **I could NOT reproduce locally** (ripgrep + git grep + full exclusion list all returned 0 offenders), and **could not read the CI log** (Actions logs endpoint = 403 without the repo PAT, which I didn't have). You have the PAT/`gh` — that's the fast path.

## 4. Do this, in order
1. Read the real failure:
   ```
   gh run list  --repo thisisthecoolesthting/x431-ai-scanner --limit 3
   gh run view <run-id #134> --repo thisisthecoolesthting/x431-ai-scanner --log-failed
   ```
   Find the lines after `REBRAND FAIL - forbidden words remain:`. Those are the exact offenders.
2. Fix exactly those. Preferred: rephrase the offending doc/script line to drop the literal banned word (e.g. neutralize `x431-foundation-push` in `cowork-autonomy-law.md`). Acceptable alt: add a *narrow* exclusion to `run-rebrand-grep.ps1` (e.g. `-notmatch 'x431-foundation'`) only if the word legitimately belongs (it's a path in a dev-rules doc). Do NOT broaden the pattern enough to defeat the gate.
3. If the gate-script diff itself is the regression, fix the script.
4. Commit ONLY the file(s) you fixed; push; poll until `rebrand-audit => success` AND `build => success`:
   ```
   gh run list --repo thisisthecoolesthting/x431-ai-scanner --limit 1
   ```
5. Confirm APK published: `https://github.com/thisisthecoolesthting/x431-ai-scanner/releases/latest/download/app-debug.apk`

## 5. Don't break these (the actual fixes — keep them)
Connection: `agent/ObdElmEngine.kt` (ATAT2), `vci/transport/UsbSerialTransport.kt` + `agent/ObdBluetoothTool.kt` (4096 guard), `vci/VciCommunicator.kt` (commandMutex — livePid delay stays OUTSIDE the lock), `vci/DirectVciSession.kt` (isLinkLive/reconnect), `ui/main/StandaloneVciController.kt` (observeConnection/reconnect/connect(watchScope)), `MainActivity.kt` (connect(lifecycleScope) + toast try/catch), `vci/VciFramePump.kt` (reader close), `transfer/LanPushUploader.kt` (delete log). UI: `ui/obd/ObdScanScreen.kt` (clear-codes confirm), `ui/main/MainScreen.kt` (blank-input guard), `ui/main/AiCopilotHomeScreen.kt` ("Available actions"). All compile-sanity reviewed (no local Android SDK to actually compile — CI is the gate).

`pc-companion/` (the Windows .exe) is independent of the Android build and already delivered.

## 6. Quick ref
- Failing: #133 (`36a7d51`), #134 (`8f251b8`). Last green: #132 (`b92cca0`).
- Gate: `scripts/run-rebrand-grep.ps1`; workflow `.github/workflows/build.yml` (job `rebrand-audit` gates `build`).
- Build is CI-only (no `gradlew`/SDK locally). Verify via Actions, not a local build.
