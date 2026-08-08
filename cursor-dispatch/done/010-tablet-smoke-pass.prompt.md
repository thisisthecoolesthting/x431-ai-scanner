# Task 010 — Tablet Smoke Test Pass: Full TESTING.md Checklist

**Goal:** Run the full TESTING.md checklist on the actual X431 tablet. Install latest debug APK from `https://github.com/thisisthecoolesthting/x431-ai-scanner/releases/latest/download/app-debug.apk`. Walk every scenario; record pass/fail. Any failures → file fix tasks numbered 011+.

## What ships

1. Complete TESTING.md checklist executed on hardware.
2. Pass/fail report for every scenario (Setup, Dashboard, Scan, Report, Voice, Sequences, etc.).
3. Any failures logged as separate fix tasks (011, 012, …).
4. Definition of Done: every TESTING.md checkbox ticked or failure documented.

## Files to read

- `TESTING.md` at repo root (full checklist)
- `https://github.com/thisisthecoolesthting/x431-ai-scanner/releases/latest/download/app-debug.apk` (install on tablet)

## Setup

1. Obtain X431 PRO/V+ tablet (launch hardware).
2. Download APK from GitHub releases (latest debug build).
3. Install via `adb install -r app-debug.apk` or manual install.
4. Launch app; walk through setup wizard if first run.

## Execution

1. Go through TESTING.md line by line.
2. For each scenario: run on hardware, record pass/fail + screenshot if fail.
3. If any scenario fails:
   - Screenshot the error.
   - Note reproducibility (always/sometimes/once).
   - File a separate task (011, 012, …) with title `Fix: {scenario} — {failure description}`.

## Acceptance

- Every TESTING.md item has a pass or fail status recorded.
- All passes → move to "Done" section.
- Any failures → 1 fix task per failure, properly scoped.
- Screenshots attached for all failures.

## Done

Once all items checked (passes or failures logged), commit smoke-test report to `docs/SMOKE-TEST-REPORT.txt`, push to main, and proceed to fix tasks if any.

**Critical:** If all checks pass, the app is production-ready for tablet deployment. If failures found, prioritize by severity (crash > data loss > UI bug > cosmetic).
