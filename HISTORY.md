# CaseForge Scanner AI — Working History

What's been built and the path it took. Newer entries on top.

## Phase 0 — GitHub repo + CI pipeline (May 18, 2026)

**Goal:** stop building locally; every change ships via CI.

- Set up `.github/workflows/build.yml` to build the debug APK on every push to `main`, attach it as a workflow artifact, and publish to a rolling `latest` release with a stable URL: `https://github.com/thisisthecoolesthting/x431-ai-scanner/releases/tag/latest`.
- Initial push from a sandbox-only workflow using a GitHub PAT — sidestepped Ricky's "can't double-click in Cowork" constraint completely. Project now operates "edit-from-sandbox / push-via-API / build-in-CI / install-from-release".
- Build-time API key injection: `local.properties` carries `caseforge.claudeApiKey=...`, `build.gradle.kts` reads it and bakes it into `BuildConfig.CLAUDE_API_KEY_DEFAULT`, `SettingsRepo.claudeApiKey` falls back to it. Key never touches git.
- CI pinned to Gradle 8.13 to match the wrapper Android Studio is running.

## Phase 1 — feel-it-first features (May 17–18, 2026)

Done with two parallel general-purpose subagents (disjoint file ownership) in ~4 min instead of ~30 min serial.

**1a — One-tap Full Scan**
- New `agentGoalFullScan(vin)` prompt that has the agent walk every diagnostic module read-only and return all DTCs via `finish_session`.
- Threaded through the existing `AgentRunner.run(vin, symptom)` entry point using a `FULL_SCAN_SENTINEL` symptom so the runner didn't need to change.
- `SessionEntity.scope` column ("diagnostic" vs "fullscan"), `latestByScope()` query, Room version bumped 1 → 2 with destructive migration.
- New `FullScanResultsScreen` renders DTCs grouped by module with red/amber/gray severity badges. Auto-navigated after `finish_session`.

**1b — `repair_info_lookup` tool**
- Agent can now call `repair_info_lookup(dtc_code, vehicle, module?)` when it sees an unfamiliar code.
- Implementation in `ai/RepairInfoLookup.kt` makes a separate single-turn Claude call with a tight system prompt: Common Causes / Recommended Tests / TSBs / Wiring Hint, ≤300 words, temperature 0.1, maxTokens 800.
- Kept off the main loop's context window so DTC lookups don't bloat token usage as the session grows.
- `AGENT_SYSTEM` prompt updated to instruct the agent to call this for any unknown DTC.

## Initial scaffold + agent loop (May 17, 2026)

- Built the whole Kotlin/Compose Android wrap app from scratch — 33+ files: gradle, manifest, Compose UI (Home, Settings, Triage, History, Approvals, FullScanResults), data layer (Room, EncryptedSharedPreferences), agent layer (AccessibilityService + tool-use loop + action log + pending-approval queue), overlay layer (floating bubble + MediaProjection), ingest layer (PDF share target + folder watcher).
- Hand-rolled Anthropic Messages API client with tool-use + vision. Polymorphic ContentBlock serializer with `encodeDefaults = true` (a real bug we hit — without that, the `type` discriminator drops off the wire and tool use silently fails).
- Floating bubble: tap = start agent session against current X431 screen; tap again while running = stop. Initially I'd wired the tap to a broadcast with no receiver — bug, fixed.
- Auto-start on VIN: `ScannerAccessibilityService` scans the visible accessibility-tree text with a 17-char VIN regex and fires `onVinDetected`.
- Per-action confirmation flow exists but is off by default (Ricky enabled "fully autonomous actuation"). Kill switch in Settings stops any running agent immediately.
- Sideloading via ADB tested end-to-end. App runs on Ricky's X431 tablet; bubble works; accessibility service activates only on X431 package names.

## Decisions worth remembering

- **No standalone VCI driver.** We talk to the X431 app, not to the dongle. Bypassing the X431 app to talk UDS/ISO-TP directly would lose Launch's manufacturer-specific bidirectional coverage — not worth it for this use case.
- **Claude API over on-device LLM.** Tested at the start; on-device models on the X431 tablet's hardware aren't strong enough for diagnostic reasoning. Cloud Claude is the right call here.
- **Agentic, not chat-style.** The AI doesn't just give the tech advice — it operates the scanner directly. That's the differentiator vs every other "AI for OBD" product.
- **Build in CI, not locally.** The sandbox can't finish a cold Android build under its 45s call cap. Android Studio works but is friction for a non-developer. GitHub Actions is the answer.
