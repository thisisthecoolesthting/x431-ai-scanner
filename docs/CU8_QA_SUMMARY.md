# CU8 — Integration QA summary

**Worktree:** `_tcw-wave2/CU8`  
**Branch:** `feat/final-integration-qa`  
**Base commit reviewed:** `95c8ad6` (post DX1–DX8 merge stack)  
**Date:** 2026-05-18  

## Scope

Read-only static QA on the merged DX integration base, plus one low-risk compile fix. No OpenAI/OpenRouter. No broad UI edits.

## Commands run

| Check | Result |
|--------|--------|
| `powershell -ExecutionPolicy Bypass -File scripts/run-rebrand-grep.ps1` | **PASS** — "Rebrand audit clean." |
| `git diff --check` (working tree) | **PASS** — no whitespace errors |
| `git diff --check HEAD~12..HEAD` | **Note** — trailing whitespace on `docs/K3_LANE_SUMMARY.md:3` (K-lane doc, pre-DX); no conflict markers in `app/src` |
| Gradle (`gradlew.bat` / `assembleDebug`) | **Unavailable** — repo has `build.gradle.kts` / `app/build.gradle.kts` in git index but **no** `gradlew` / `gradlew.bat` checked in; sparse worktree cannot run APK compile here |

## DX merge stack on this branch

Eight merge commits integrated on top of K-lane rebrand/transfer work:

- DX2 guided diagnostic tests  
- DX3 OEM data mining index  
- DX4 offline DTC bundles  
- DX5 camera VIN scan  
- DX6 shop export formats  
- DX7 PC assistant core  
- DX8 fast workflow state  
- DX1 AI Copilot home core  

## Static scan (imports, symbols, callbacks, resources)

### Conflict / merge hygiene

- No `<<<<<<<` / `=======` / `>>>>>>>` markers under `app/src` in the CU8 worktree.
- `MainActivity` `when (route)` and `handleCopilotAction` cover all `CopilotAction` sealed variants including `SubmitSymptom`.
- `GuidedTestPlanner.transportFromLink` `when` is exhaustive for `DiagnosticConnector.LinkKind` (four kinds: ELM327_USB/BT, OEM_USB/BT).

### Cross-reference (types used by DX-touched files)

| Symbol / API | Declared in repo (git tree) | Used from |
|--------------|----------------------------|-----------|
| `ConnectionDrawerSheet`, `ActionTile` | `ui/main/ConnectionDrawer.kt`, `ActionTile.kt` | `MainScreen.kt` |
| `RecallsScreen`, `StandaloneVciController` | `ui/main/RecallsScreen.kt`, `StandaloneVciController.kt` | `MainActivity.kt` |
| `CopilotAction` | `ui/main/CopilotAction.kt` | `AiCopilotHomeScreen.kt`, `MainActivity.kt` |
| `FastWorkflowState` | `data/FastWorkflowState.kt` | `SettingsRepo.kt` |
| `OfflineBundle`, `OfflineDtcLookup` | `offline/*.kt` + assets | tests + future UI lanes |
| `ShopExport`, `ShopExportFormatter` | `report/*.kt` | tests |
| `PcAssistantClient`, `PcAssistantModels` | `pc/*.kt` | settings/export lanes |
| `VinNormalizer`, `VinOcrCandidate` | `vin/*.kt` | `VinCameraScanActivity.kt` |

No duplicate class definitions found for DX-owned types in the merged slice.

### Android resources

- `AndroidManifest.xml` registers `VinCameraScanActivity` and `FileProvider`; `file_paths.xml` adds `cache-path` for VIN scan JPEGs.
- `strings.xml` defines `vin_scan_activity_label`, `vin_scan_no_vin_found`, `vin_scan_ocr_failed` referenced by manifest / VIN activity.
- `@xml/usb_device_filter` referenced by manifest exists in full tree (not in sparse checkout).

### Assets

- `app/src/main/assets/offline/dtc_generic.json` and `guided_tests.json` present; structure matches `OfflineBundle` kotlinx-serialization models (`version` + `entries` / `tests`).

## Compile fix applied (CU8)

**File:** `app/src/main/kotlin/com/caseforge/scanner/vin/VinCameraScanActivity.kt`  

**Issue:** Uses `R.string.vin_scan_*` from package `com.caseforge.scanner.vin` without importing `com.caseforge.scanner.R` (would fail Kotlin compile).  

**Fix:** Added `import com.caseforge.scanner.R`.

## Risks / follow-ups (not fixed in CU8)

1. **No Gradle proof** — run `./gradlew :app:assembleDebug` and `:app:testDebugUnitTest` on a full checkout (with wrapper) before release.
2. **Sparse worktree** — CU8 checkout only materializes merged paths; QA relied on `git ls-tree` for sibling symbols.
3. **Runtime wiring** — `FastWorkflowState` persisted in `SettingsRepo` but UI consumption is deferred to other CU lanes per DX8 comments.
4. **PC assistant** — `PcAssistantClient` assumes LAN receiver endpoints; no compile dependency on OpenRouter.

## Verdict

**Integration static QA: green** with one trivial compile fix committed. Full compile/test gate blocked only by missing Gradle wrapper in this worktree.
