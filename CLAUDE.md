# How Claude Works on CaseForge Scanner AI

Rules for any Claude session that picks up this project. Written from real friction we hit, not theory.

## Identity

This is a personal tool built by Ricky (reasner196@gmail.com). It's an AI agent that sits on top of the **Launch X431 PRO/PROS/V+** automotive scanner app on an Android tablet, reads the X431 app's UI via the Accessibility framework, and drives it on the technician's behalf using Anthropic's Claude with tool use.

## The deployment loop — this is the law

The project uses GitHub Actions to build APKs. Do NOT try to build on Ricky's Windows machine, do NOT try to drive Android Studio, do NOT try to use the local sandbox to compile. The flow is:

1. You (Claude) edit code in the workspace.
2. You push to GitHub from your sandbox using the PAT (it's in the chat history; if it's missing or revoked, ask for a new one — link: https://github.com/settings/tokens/new?scopes=repo).
3. GitHub Actions builds the APK in ~3 min.
4. The APK lands at `https://github.com/thisisthecoolesthting/x431-ai-scanner/releases/tag/latest` — a stable URL.
5. Ricky installs by downloading from that URL on the tablet (or eventually via the in-app updater).

**Never** ask Ricky to copy/paste, double-click, run a .bat, or open Android Studio unless every other path is blocked. He has said this explicitly. Cowork in his setup does not execute files when clicked; assume the same of every UI prompt you might invent.

## Push command (use this verbatim)

```bash
WORK=/tmp/x431-push
rm -rf $WORK && mkdir -p $WORK
cd /sessions/inspiring-amazing-gates/mnt/CaseForge/x431-ai-scanner
rsync -a --exclude='.git' --exclude='.gradle' --exclude='build' --exclude='app/build' \
  --exclude='.build-cache' --exclude='local.properties' --exclude='*.iml' --exclude='.idea' \
  --exclude='setup-github.log' --exclude='.kotlin' ./ $WORK/
cd $WORK
git init -b main -q
git config user.email "reasner196@gmail.com"
git config user.name "Ricky"
git add . && git commit -q -m "<one-line message>"
git remote add origin "https://thisisthecoolesthting:<PAT>@github.com/thisisthecoolesthting/x431-ai-scanner.git"
git push -u origin main --force
```

Then poll the build:

```bash
curl -s -H "Authorization: Bearer <PAT>" \
  "https://api.github.com/repos/thisisthecoolesthting/x431-ai-scanner/actions/runs?per_page=1" \
  | python3 -c "import sys,json; r=json.load(sys.stdin)['workflow_runs'][0]; print(r['status'], r['conclusion'], r['html_url'])"
```

If a step fails, pull logs:
```bash
curl -sL -H "Authorization: Bearer <PAT>" \
  "https://api.github.com/repos/thisisthecoolesthting/x431-ai-scanner/actions/runs/<RUN_ID>/logs" -o /tmp/logs.zip
unzip -o /tmp/logs.zip -d /tmp/logs/
```

## What never goes to GitHub

- `local.properties` — has Ricky's Anthropic API key (`caseforge.claudeApiKey=sk-ant-...`). The build reads it at compile time and bakes it into BuildConfig.CLAUDE_API_KEY_DEFAULT. The key never appears in committed files.
- `.build-cache/`, `build/`, `.gradle/`, `.idea/`, `setup-github.log`, `.kotlin/` — local-only.

The `.gitignore` already covers these. The rsync command in the push above is a belt-and-suspenders second layer.

## Stack pinning (don't drift)

- AGP 8.5.2, Kotlin 2.0.20, Compose BOM 2024.09.02, Material3, Room 2.6.1, KSP 2.0.20-1.0.25.
- Gradle: keep CI in lockstep with whatever is in `gradle/wrapper/gradle-wrapper.properties`. Android Studio bumps this from time to time; bump CI to match in `.github/workflows/build.yml`.
- minSdk 24, targetSdk 34, compileSdk 34. minSdk 24 is required to cover old X431 tablets.

## Architecture (read this before editing the agent)

The agent loop is the heart. Don't change it casually.

- `agent/ScannerAccessibilityService.kt` — reads the X431 UI tree, dispatches taps/typing/gestures. Activates only when one of the X431 package names is foreground. Detects VINs by regex and fires `onVinDetected` callbacks.
- `agent/AgentTools.kt` — JSON-schema'd tools exposed to Claude. Current set: `read_screen`, `tap`, `type`, `scroll`, `back`, `wait_for`, `capture_screenshot`, `repair_info_lookup`, `propose_actuation`, `finish_session`. Adding a tool means: add schema here, add execution branch in AgentRunner, and (optionally) mention it in the AGENT_SYSTEM prompt.
- `agent/AgentRunner.kt` — the loop. Sends conversation to Claude, executes returned tool_use blocks, packs results back, repeats until `finish_session` or `maxSteps=40`. Cancellable via parentJob.
- `ai/Prompts.kt` — all prompt copy. Iterate here without code changes elsewhere. `FULL_SCAN_SENTINEL` is a magic symptom string that rewrites the goal — don't break it.
- `ai/ClaudeClient.kt` — hand-rolled Anthropic Messages API client with tool-use + vision. Polymorphic ContentBlock serializer uses `encodeDefaults = true` (don't change that — drops the `type` discriminator).
- `ai/RepairInfoLookup.kt` — separate Claude call for DTC repair info. Kept outside the main loop's context window.

## Working style — what Ricky actually wants

- **Concise.** No bullets unless needed. No fluff, no "Great question!" preambles. Get to the work.
- **Do, don't ask.** When the path is obvious, just do it. Only ask when a decision genuinely changes the plan.
- **Skip explaining what you just did at length.** A one-line summary is enough.
- **Don't lecture about safety.** This is his personal tool, his car, his scanner. He has autonomous-actuation enabled on purpose. Don't second-guess; just engineer it well (kill switch, action log, optional confirmation mode — all already present).
- **No emojis unless he uses one first.**
- **Use subagents for parallel work.** When two features have disjoint file ownership, fire two general-purpose agents in parallel via the Agent tool with explicit "owns these files / must NOT touch these files" instructions. It cut a ~30 min serial job to ~4 min.
- **Verify before celebrating.** After a push, poll the GitHub Actions API to confirm the build is green. Don't say "you're all set" until the conclusion is `success`.

## Phase roadmap (where we are)

**Done:**
- Phase 0: GitHub repo + CI build pipeline (this file).
- Phase 1a: One-tap Full Scan of every module.
- Phase 1b: `repair_info_lookup` tool (separate Claude call).
- Core agent loop, accessibility driver, overlay bubble, screen capture, report ingest, Room session history, encrypted settings with build-time API key default.

**Next (in this order, when Ricky asks):**
- In-app "Check for Updates" button that pulls from the `latest` release URL — finishes the auto-update loop.
- Phase 2a: Customer-facing PDF report from a session.
- Phase 2b: VIN history surfaced at session start.
- Phase 2c: UI translation overlay over the X431 app.
- Phase 3: Live-data dashboard, voice control, pre/post-scan automation.

## Environmental quirks (don't re-discover)

- Cowork file links in chat (`computer://...`) preview files, they don't execute them. Don't expect Ricky to be able to double-click a .bat from chat.
- Ricky's File Explorer is granted at "full" tier but masking still hides most of its content in screenshots — driving it via computer-use is unreliable. Prefer sandbox + API.
- Android Studio is tier "click": you can left-click but cannot type into the editor. If you're driving Studio, only menu clicks work, and even those are blocky because of the screenshot mask.
- The local CaseForge folder (`E:\Projects\CaseForge`) is itself a separate, unrelated git repo (his Printify project). `x431-ai-scanner` is a nested subfolder. A nested `.git` was needed to give it its own remote. From your sandbox: don't run `git` inside the project folder — operate from `/tmp/x431-push` so the parent CaseForge .git doesn't interfere.
- Sandbox bash has a 45s hard cap and no persistent background processes. Don't try to build the APK locally in the sandbox — that's why we delegate to GitHub Actions.

## When something fails

1. Don't theorize. Pull the failing log, read the actual error, fix the specific thing.
2. If CI fails, the `/actions/runs/<id>/logs` endpoint returns a zip with per-step text logs. The last 80 lines of the failing step almost always tell the story.
3. If the agent on the tablet misbehaves, look at `agent_actions.log` (in app private storage) — every step is recorded.
