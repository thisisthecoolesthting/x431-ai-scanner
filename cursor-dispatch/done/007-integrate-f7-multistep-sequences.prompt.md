# Task 007 — Integrate F7: Multi-Step Diagnostic Sequences

**Goal:** Integrate `drafts/F7-SEQUENCES/`. Five automated diagnostic sequences (relative compression, EVAP smoke test, injector kill, VVT solenoid sweep, parasitic draw bisection). EngineDriver navigates each via Capability format. Composable type extending Capability with `steps: List<SequenceStep>`.

## What ships

1. New data class `app/src/main/kotlin/com/caseforge/scanner/data/DiagnosticSequence.kt` — extends Capability, adds {name, description, steps: List<SequenceStep>, totalDurationMinutes}.
2. Five pre-configured sequences in CapabilityMap:
   - Relative Compression Test
   - EVAP Smoke Test
   - Injector Kill Test
   - VVT Solenoid Sweep
   - Parasitic Draw Bisection
3. New ScreenKind variant: `SequenceRunner` composable — renders step-by-step UI, throttles between steps, collects results.
4. EngineDriver routed sequence commands to SequenceRunner.
5. Results stored in ActionLog for playback.

## Files to read

- `drafts/F7-SEQUENCES/` (all sources + sequence definitions)
- `app/src/main/kotlin/com/caseforge/scanner/engine/EngineDriver.kt` (command interface)
- `app/src/main/kotlin/com/caseforge/scanner/data/CapabilityMap.kt` (where sequences defined)
- `app/src/main/kotlin/com/caseforge/scanner/engine/EngineState.kt` (state management)
- `app/src/main/kotlin/com/caseforge/scanner/overlay/compose/screens/ActuationScreen.kt` (analogous multi-step UI)

## Files to write/modify

- **Create:** `app/src/main/kotlin/com/caseforge/scanner/data/DiagnosticSequence.kt`
- **Create:** `app/src/main/kotlin/com/caseforge/scanner/overlay/compose/screens/SequenceRunnerScreen.kt`
- **Create:** `app/src/main/kotlin/com/caseforge/scanner/data/SequenceDefinitions.kt` (the 5 sequences)
- **Modify:** `engine/EngineDriver.kt` — add sequence routing command
- **Modify:** `data/CapabilityMap.kt` — inject 5 sequences into catalog
- **Modify:** `overlay/compose/ScreenKind.kt` — add `SequenceRunner` variant

## Acceptance

- Compiles clean.
- All five sequences load into CapabilityMap.
- SequenceRunnerScreen renders, steps advance on user action.
- Each step executes underlying EngineDriver command (read data, actuate, wait).
- Results saved to ActionLog for post-analysis.
- UI reflects step progress (X of Y) with timing.

## Done

`git mv cursor-dispatch/outbox/007-integrate-f7-multistep-sequences.prompt.md cursor-dispatch/done/`, commit `Bundle: F7 Multi-step diagnostic sequences`, push to main.
