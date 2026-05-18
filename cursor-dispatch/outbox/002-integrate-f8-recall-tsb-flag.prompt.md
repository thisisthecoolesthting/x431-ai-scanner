# Task 002 — Integrate F8: NHTSA Recall/TSB Auto-Flag

**Goal:** Integrate `drafts/F8-RECALL-FLAG/` to cross-reference VIN + DTCs against NHTSA recalls and TSBs, surface findings in a new "RecallScreen" composable.

## What ships

1. New Kotlin file `app/src/main/kotlin/com/caseforge/scanner/agent/RecallFlagger.kt` — queries NHTSA API with VIN, cross-matches DTCs, returns structured Recall/TSB list.
2. New composable `app/src/main/kotlin/com/caseforge/scanner/overlay/compose/screens/RecallScreen.kt` — renders recall cards with VIN decode info, severity badges, links to NHTSA.
3. New ScreenKind enum variant: `RecallScreen` in `overlay/compose/ScreenKind.kt`.
4. Wire RecallFlagger into EngineState on scan completion (compose with existing `agent/RepairInfoLookup.kt`).
5. Add recall-card UI to Report screen route when DTCs present.

## Files to read

- `drafts/F8-RECALL-FLAG/` (all Kotlin sources)
- `app/src/main/kotlin/com/caseforge/scanner/engine/EngineState.kt` (state model)
- `app/src/main/kotlin/com/caseforge/scanner/agent/RepairInfoLookup.kt` (NHTSA VIN decode already wired)
- `app/src/main/kotlin/com/caseforge/scanner/overlay/compose/ScreenKind.kt` (enum routing)
- `app/src/main/kotlin/com/caseforge/scanner/overlay/compose/screens/ReportScreen.kt` (where recall results live)

## Files to write/modify

- **Create:** `app/src/main/kotlin/com/caseforge/scanner/agent/RecallFlagger.kt` (copy from drafts, rename package)
- **Create:** `app/src/main/kotlin/com/caseforge/scanner/overlay/compose/screens/RecallScreen.kt` (new composable)
- **Modify:** `overlay/compose/ScreenKind.kt` — add `RecallScreen` variant
- **Modify:** `engine/EngineState.kt` — add `recalls: List<Recall>` field, initialize empty
- **Modify:** `overlay/compose/screens/ReportScreen.kt` — conditionally render recall card when recalls non-empty

## Acceptance

- Compiles clean.
- RecallScreen renders in isolation (compose preview).
- RecallFlagger query succeeds for a known VIN (e.g., "1HGCV1F32LA123456").
- Recall card appears on Report screen post-scan if any recalls found.
- No regression in existing NHTSA module.

## Done

`git mv cursor-dispatch/outbox/002-integrate-f8-recall-tsb-flag.prompt.md cursor-dispatch/done/`, commit `Bundle: F8 Recall/TSB auto-flag (NHTSA cross-reference)`, push to main.
