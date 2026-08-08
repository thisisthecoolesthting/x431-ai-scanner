# Task 005 — Integrate F2: Cross-Module DTC Root-Cause Correlator

**Goal:** Integrate `drafts/F2-CORRELATOR/`. New file `engine/DtcCorrelator.kt` consumes EngineState.dtcs across modules and surfaces "Likely Root Cause" card on Report screen.

## What ships

1. New file `app/src/main/kotlin/com/caseforge/scanner/engine/DtcCorrelator.kt` — stateless correlator that analyzes DTC set, cross-module patterns, identifies single-point-of-failure hypotheses. Returns RootCauseHypothesis {cause, affectedModules, confidence, evidencePoints}.
2. Composable `app/src/main/kotlin/com/caseforge/scanner/overlay/compose/screens/RootCauseCard.kt` — renders hypothesis with evidence links.
3. Wire correlator into Report screen; call post-scan when all EngineState DTCs settled.
4. No external API calls — pure logic over DTC patterns, OEM playbook cross-ref.

## Files to read

- `drafts/F2-CORRELATOR/` (all sources)
- `app/src/main/kotlin/com/caseforge/scanner/engine/EngineState.kt` (DTC source)
- `app/src/main/kotlin/com/caseforge/scanner/data/CapabilityMap.kt` (OEM playbook context)
- `app/src/main/kotlin/com/caseforge/scanner/overlay/compose/screens/ReportScreen.kt` (where card renders)

## Files to write/modify

- **Create:** `app/src/main/kotlin/com/caseforge/scanner/engine/DtcCorrelator.kt`
- **Create:** `app/src/main/kotlin/com/caseforge/scanner/overlay/compose/screens/RootCauseCard.kt`
- **Modify:** `overlay/compose/screens/ReportScreen.kt` — call DtcCorrelator post-scan, render RootCauseCard if hypothesis present
- **Modify:** `engine/EngineState.kt` — add `rootCauseHypothesis: RootCauseHypothesis?` field

## Acceptance

- Compiles clean.
- DtcCorrelator processes multi-module DTC set without error.
- RootCauseCard renders on Report screen when hypothesis present.
- Evidence points clickable (link to relevant Capabilities or test suggestions).
- No performance regression (correlator runs in <100ms for typical DTC set).

## Done

`git mv cursor-dispatch/outbox/005-integrate-f2-correlator.prompt.md cursor-dispatch/done/`, commit `Bundle: F2 Cross-module DTC correlator`, push to main.
