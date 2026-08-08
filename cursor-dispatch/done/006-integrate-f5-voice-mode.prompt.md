# Task 006 — Integrate F5: Hands-Free Voice Mode

**Goal:** Integrate `drafts/F5-VOICE/`. Hands-free voice control ("Hey Together, read all codes"). Uses Android SpeechRecognizer. Composes with existing TTS + EngineDriver command surface.

## What ships

1. New file `app/src/main/kotlin/com/caseforge/scanner/agent/VoiceController.kt` — wraps SpeechRecognizer, NLP intent parser, routes recognized commands to EngineDriver (read codes, live data, test sequence).
2. Settings toggle in `ui/SettingsScreen.kt` — "Enable Voice Mode".
3. Voice FAB on Dashboard or MainOverlay — long-press to listen, release to execute.
4. TTS feedback on command recognition ("OK, reading all codes…").
5. Add `android.permission.RECORD_AUDIO` and `android.permission.RECOGNIZE_SPEECH` to manifest.

## Files to read

- `drafts/F5-VOICE/` (all sources)
- `app/src/main/kotlin/com/caseforge/scanner/engine/EngineDriver.kt` (command surface to compose with)
- `app/src/main/kotlin/com/caseforge/scanner/ui/SettingsScreen.kt` (settings toggle location)
- `app/src/main/kotlin/com/caseforge/scanner/overlay/OverlayRoot.kt` (main UI tree; where voice FAB lives)
- `app/src/main/AndroidManifest.xml` (permissions)

## Files to write/modify

- **Create:** `app/src/main/kotlin/com/caseforge/scanner/agent/VoiceController.kt`
- **Create:** `app/src/main/kotlin/com/caseforge/scanner/overlay/compose/VoiceFab.kt` (FAB composable)
- **Modify:** `ui/SettingsScreen.kt` — add voice mode toggle, tied to App.voiceEnabled
- **Modify:** `overlay/OverlayRoot.kt` or main Dashboard — add VoiceFab conditionally if voice enabled
- **Modify:** `AndroidManifest.xml` — add RECORD_AUDIO + RECOGNIZE_SPEECH permissions

## Acceptance

- Compiles clean.
- RECORD_AUDIO and RECOGNIZE_SPEECH declared and requested at runtime.
- Voice FAB responds to long-press; SpeechRecognizer activates.
- Commands recognized (e.g., "read codes", "live data") and routed to EngineDriver.
- TTS feedback confirms execution.
- Settings toggle controls enable/disable.

## Done

`git mv cursor-dispatch/outbox/006-integrate-f5-voice-mode.prompt.md cursor-dispatch/done/`, commit `Bundle: F5 Hands-free voice mode`, push to main.
