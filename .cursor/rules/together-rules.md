# Together Scanners AI — House Rules (Cursor + Codex)

You are Cursor (with Codex available) building Together Scanners AI for Ricky. Claude is not in this loop.

## Build target

Android app, Kotlin + Jetpack Compose, runs on Launch X431 PRO/V+ tablet. Overlay-over-X431 architecture (Phase 1) with a Phase 2 spike on direct VCI Bluetooth (`drafts/F10-VCI-SPIKE/`).

## Working clone

`C:\Users\reasn\Desktop\x431-foundation-push\` — push from here. Never push from `E:\Projects\CaseForge\x431-ai-scanner\` (different parent repo).

## Version pins — DO NOT bump without explicit ticket

- AGP 8.11.2, Kotlin 2.0.20, KSP 2.0.20-1.0.25, Compose plugin 2.0.20
- Gradle 8.13 (both wrapper at `gradle/wrapper/gradle-wrapper.properties` AND CI workflow `gradle-version`)
- Material3 latest stable, Room 2.6.1
- Min SDK 24, Target SDK 34, Compile SDK 34

## Files NEVER to touch (regression shield)

- `agent/AgentRunner.kt` — the Claude tool-use loop, brittle
- `agent/AgentTools.kt` — agent tool schemas
- `agent/AgentActionLog.kt` — append-only audit hook
- `agent/ScannerAccessibilityService.kt` — unless your ticket explicitly says so
- `ai/ClaudeClient.kt` — polymorphic serializer is fragile
- `ai/Prompts.kt` — Ricky iterates here
- `ai/RepairInfoLookup.kt`
- `overlay/OverlayService.kt` — the bubble; already polished
- `ui/wizard/*` — setup wizard, sensitive
- `ui/dashboard/*` — reactive VIN binding, sensitive
- In-app updater code (likely `data/Updater.kt` or `update/*`)
- `local.properties`, `.gradle/`, `build/`, `app/build/`, `.idea/`, `.kotlin/`
- Top-level Gradle plugin versions in `build.gradle.kts` (root)

## Build / deploy loop

1. Local edits → `git add . && git commit -m "..." && git push origin main`
2. CI (`.github/workflows/build.yml`) runs `gradle :app:assembleDebug --no-daemon`
3. On green: rolling `latest` Release published at `https://github.com/thisisthecoolesthting/x431-ai-scanner/releases/latest/download/app-debug.apk`
4. Tablet in-app updater polls that URL
5. On red: `gh run view <id> --log-failed` → fix → re-push

Do NOT build locally. Builds happen in GitHub Actions. Local environment is for editing and syntax checks only.

## Commit message style

Imperative mood, concise. No emojis. Examples:
- `Bundle: F8 Recall/TSB auto-flag (NHTSA cross-reference)`
- `Fix CI: missing import androidx.compose.ui.unit.dp in ActuationScreen`
- `F10 SPIKE: VciFrame encode/decode + checksum`
- `Apply OEM path corrections (47 entries fixed per finding 012)`

## Compose conventions

- All text via `MaterialTheme.typography.*`. No raw `.sp` values.
- All colors via `MaterialTheme.colorScheme.*`. No `Color(0xFF…)` literals in screen files.
- Spacing tokens from `overlay/compose/Spacing.kt`: `Space4`, `Space8`, `Space12`, `Space16`, `Space24`, `Space32`.
- Card style via `overlay/compose/Style.kt`: `TogetherCardShape`, `togetherCardColors()`, `togetherCardElevation()`.
- Modern pager: `androidx.compose.foundation.pager.HorizontalPager` + `rememberPagerState(initialPage = 0) { pageCount }`. Never Accompanist (deprecated).
- Pointer-input gestures: wrap in `awaitEachGesture { awaitFirstDown(); awaitLongPressOrCancellation() }`. Import from `androidx.compose.foundation.gestures.*`.

## Kotlin conventions

- For coroutine cancellation checks inside `launch { }`: use `currentCoroutineContext().isActive` (top-level `kotlinx.coroutines.isActive` was removed in 1.7+).
- UsageStatsManager import: `android.app.usage.UsageStatsManager` (NOT `android.app.UsageStatsManager`).
- Material3 dynamic color: `dynamicLightColorScheme(context)` / `dynamicDarkColorScheme(context)` (separate functions, no unified `dynamicColorScheme`).
- For nullable Boolean comparisons: `if (someBool != true)` instead of `if (!someBool)` to avoid operator-not unresolved.

## Branch strategy

For one-line fixes and CI repairs: push directly to `main`. For multi-file features: branch `feat/<name>` or `spike/<name>`, open PR, you can self-merge once CI green. For Phase 2 spike (F10): use `spike/direct-vci`.

## Decompile findings (private repo)

`github.com/thisisthecoolesthting/together-decompile` — clone with `gh repo clone`. Findings 010 (VCI protocol), 011 (diagnostic engine), 012 (menu tree), 013 (brand assets). Decompiled source on disk at `E:\Projects\together-decompile\decompiled\sources\com\cnlaunch\` (34,995 files). Unobfuscated packages: `bluetooth`, `socket`, `diagnosemodule`, `diagnostic`, `x431`, `x431pro`.

## Use all your agents

Ricky wants maximum fan-out. Use Cursor Composer for interactive work, Cursor Background Agent for autonomous queue draining, Codex CLI for heavy code-writing. Up to ~10 concurrent streams. Don't ask permission for routine work.
