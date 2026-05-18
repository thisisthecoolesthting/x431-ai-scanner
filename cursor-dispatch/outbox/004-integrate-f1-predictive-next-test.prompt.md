# Task 004 — Integrate F1: Predictive Next-Test via Agent Tool-Use

**Goal:** Integrate `drafts/F1-NEXT-TEST/`. After every scan, agent suggests highest-probability next diagnostic step using Claude tool-use through existing AgentRunner. Adds new agent tool + "Suggested Next Test" UI card.

## What ships

1. New file `app/src/main/kotlin/com/caseforge/scanner/agent/NextTestSuggester.kt` — takes EngineState (DTCs, freeze-frame, module state) and invokes Claude via AgentRunner to suggest next test. Returns structured suggestion {testName, probability, rationale}.
2. New ScreenKind variant: `SuggestedTestCard` composable inline on Report screen (or modal).
3. Wire NextTestSuggester call into scan completion flow (after EngineState updated, trigger suggestion).
4. Surface suggestion card with accept/decline buttons; accept routes to LiveDataScreen pre-configured for that test.

## Critical constraints

- **DO NOT MODIFY:** `agent/AgentRunner.kt`, `agent/AgentTools.kt`, `agent/AgentActionLog.kt` — these are read-only.
- **DO NOT MODIFY:** `ai/ClaudeClient.kt`, `ai/Prompts.kt` — frozen.
- NextTestSuggester **composes only** — calls existing AgentRunner (already tool-use capable) to invoke Claude.

## Files to read

- `drafts/F1-NEXT-TEST/` (all sources)
- `app/src/main/kotlin/com/caseforge/scanner/agent/AgentRunner.kt` (READ-ONLY; understand the tool-use interface)
- `app/src/main/kotlin/com/caseforge/scanner/engine/EngineState.kt` (input data: dtcs, freeze-frame)
- `app/src/main/kotlin/com/caseforge/scanner/overlay/compose/screens/ReportScreen.kt` (where suggestion card renders)

## Files to write/modify

- **Create:** `app/src/main/kotlin/com/caseforge/scanner/agent/NextTestSuggester.kt` (calls AgentRunner, returns Suggestion data class)
- **Create:** `app/src/main/kotlin/com/caseforge/scanner/overlay/compose/screens/SuggestedTestCard.kt` (composable card)
- **Modify:** `overlay/compose/screens/ReportScreen.kt` — import and conditionally render SuggestedTestCard if suggestion present
- **Modify:** `engine/EngineState.kt` — add `suggestedNextTest: Suggestion?` field
- **Modify:** scan completion callback — call NextTestSuggester after EngineState settled

## Acceptance

- Compiles clean.
- NextTestSuggester invokes AgentRunner without error.
- Claude responds with structured suggestion (tool-use call succeeds).
- SuggestedTestCard renders on Report screen post-scan.
- Accept/decline buttons functional; accept navigates to LiveDataScreen.

## Done

`git mv cursor-dispatch/outbox/004-integrate-f1-predictive-next-test.prompt.md cursor-dispatch/done/`, commit `Bundle: F1 Predictive next-test suggestions`, push to main.
