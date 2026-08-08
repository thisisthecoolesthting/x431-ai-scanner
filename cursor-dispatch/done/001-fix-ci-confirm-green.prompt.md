# Task 001 — Confirm CI green on main

**Goal:** Verify build #52 (or latest) is green. If red, fix the compile error(s) and push until green.

## Steps

1. `gh run list --limit 1 --json conclusion,number,headSha,displayTitle | ConvertFrom-Json` (PowerShell) or equivalent.
2. If `conclusion == "success"`: move this prompt to `done/`, proceed to task 002.
3. If `conclusion == "failure"`:
   - `gh run view <id> --log-failed | Select-String -Pattern 'error:|Unresolved' | Select-Object -First 30`
   - Fix the compile errors. Common patterns we've already seen:
     - `Unresolved reference 'not' for operator '!'` on nullable Boolean → use `!= true`
     - `Unresolved reference 'awaitFirstDown'` → wrap in `awaitEachGesture { ... }` and import from `androidx.compose.foundation.gestures.*`
     - `Unresolved reference 'accompanist'` → migrate to `androidx.compose.foundation.pager.HorizontalPager` + `rememberPagerState(initialPage = 0) { pageCount }`
     - `Unresolved reference 'dynamicColorScheme'` → split into `dynamicLightColorScheme(ctx)` / `dynamicDarkColorScheme(ctx)`
     - `Unresolved reference 'isActive'` inside coroutine → use `currentCoroutineContext().isActive` (or `coroutineContext.isActive` with `import kotlin.coroutines.coroutineContext`)
     - Missing `dp` → `import androidx.compose.ui.unit.dp`
   - Push fix.
4. Repeat until green.

## Acceptance

- Latest `main` build conclusion is `success`.
- APK artifact published at `https://github.com/thisisthecoolesthting/x431-ai-scanner/releases/latest/download/app-debug.apk`.

## Done

`git mv cursor-dispatch/outbox/001-fix-ci-confirm-green.prompt.md cursor-dispatch/done/`, commit, push, proceed to task 002.
