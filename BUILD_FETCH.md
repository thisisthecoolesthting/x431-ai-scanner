# BUILD_FETCH â€” get CU1 on the tablet (git + Gradle)

**App:** `_tcw-wave2/CU1` inside the DEv1 monorepo  
**Last update:** 2026-05-20

This build bundles three Plan B slices in one APK:

| Slice | Doc | In-app |
|-------|-----|--------|
| **SKREEM / SKIM (022)** | `_x431-work/decompile/findings/022-skreem-module.md` | Tier 3 Immo + Tier 4 Programming (Jeep/Dodge); Ford PATS N/A |
| **Ford G1 Tier-0 smoke (127)** | `_x431-work/decompile/findings/023-ford-g1-smoke-runbook.md` | `ObdScanScreen` G1 banner when Ford wedge VIN detected |
| **Windstar 2000 discovery (128)** | `_x431-work/decompile/findings/024-windstar-2000-connection-agent.md` | `ConnectionReadinessPanel` + `scan_connection_readiness` agent tool |

Progress tracker: `014-planb-build-progress.md` Â· Windows preconditions: `018-windows-build-sprint.md` Â· JDK pin: `017-jdk17-install.md`

---

## Embedded API key (debug)

The **debug** APK embeds your Anthropic key at compile time â€” no manual paste on the tablet for first ship.

| Step | Detail |
|------|--------|
| Source | Repo-root `.env` â†’ `ANTHROPIC_API_KEY=â€¦` (DEv1 monorepo root, not inside `CU1/`) |
| When read | Gradle `assembleDebug` only â€” value is **never** committed to git |
| Runtime | `BuildConfig.ANTHROPIC_API_KEY` â†’ `SettingsRepo.claudeApiKey` â†’ `ClaudeClient` / `AgentRunner` |
| Settings UI | Read-only label: **API key: embedded (build)** when present; never displays the key |

**Build (office PC):**

```powershell
cd C:\Users\reasn\Documents\Claude\Projects\DEv1\_tcw-wave2\CU1
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat --no-daemon :app:assembleDebug
```

Gradle walks up from `CU1/` until it finds `.env`. If the key is missing, the build still succeeds but logs a warning and the APK shows **API key: not set**.

**Rotate later (pick one):**

1. **Rebuild** â€” update `ANTHROPIC_API_KEY` in `.env`, run `assembleDebug` again, reinstall APK.
2. **Settings override (future)** â€” typed key in Settings will take precedence once priority is flipped; until then embedded build key wins.
3. **Release builds** â€” `ANTHROPIC_API_KEY` is empty in release; use Settings or a signed release pipeline with secrets injection.

Details: `_x431-work/decompile/findings/029-anthropic-key-build-injection.md`

---

## Git fetch / clone (operator) â€” same method as G0 / `_run-cu1-g0.bat`

CU1 lives **inside** `rickys-control-center` (DEv1). There is no separate git submodule or Gradle `git` dependency for app source â€” **pull the repo, then build locally**.

### Already cloned (office PC)

```powershell
cd C:\Users\reasn\Documents\Claude\Projects\DEv1
git pull origin main
cd _tcw-wave2\CU1
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat --no-daemon :app:assembleDebug
```

APK: `_tcw-wave2\CU1\app\build\outputs\apk\debug\app-debug.apk`

Full Windows path (office PC):

`C:\Users\reasn\Documents\Claude\Projects\DEv1\_tcw-wave2\CU1\app\build\outputs\apk\debug\app-debug.apk`

A copy may also exist at repo root: `_tcw-wave2\CU1\app-debug-latest.apk` after manual copy.

### Harvest & upload (driver discovery sidecar)

Every **Send to PC** / **Harvest & upload** run attaches `harvest-batch/manifest.json` inside the zip via [LanPushUploader](app/src/main/kotlin/com/caseforge/scanner/transfer/LanPushUploader.kt):

| Field | Meaning |
|-------|---------|
| `schemaVersion` | Manifest format (currently `1`) |
| `timestampMs` | Epoch ms when harvest ran |
| `versionCode` / `versionName` | App build at harvest time |
| `vehicleProfileId` | e.g. `ford-windstar-2000` |
| `discoveryReport` | USB/BT devices, `permissions`, `missingItems[]`, `recommendedAction` |

**Operator triggers:**

1. **Settings â†’ Harvest & upload to PC** â€” scans drivers then uploads (vehicle DB + sidecar when present).
2. **Scan vehicle â†’ Connection readiness â†’ Find drivers & adapters â†’ Harvest & upload to PC** â€” uses the scan you just ran.
3. **Send data to PC** (Settings or Copilot) â€” same uploader; driver sidecar always included.

PC receiver unchanged: `scripts/lan-export-receiver.ps1` on port 8765. Unzip and read `harvest-batch/manifest.json` for adapter state.

Details: `_x431-work/decompile/findings/026-tablet-data-and-missing-features.md`


### Fresh machine (first time)

```powershell
git clone https://github.com/thisisthecoolesthting/rickys-control-center.git C:\Users\reasn\Documents\Claude\Projects\DEv1
cd C:\Users\reasn\Documents\Claude\Projects\DEv1\_tcw-wave2\CU1
# JDK 17: see 017-jdk17-install.md (Temurin 17 + gradle.properties pin)
.\gradlew.bat --no-daemon :app:assembleDebug
```

### One-click bootstrap (no git â€” downloads JDK/SDK/Gradle into `.build-cache`)

```powershell
cd C:\Users\reasn\Documents\Claude\Projects\DEv1\_tcw-wave2\CU1
.\build.ps1
```

Use **git pull + gradlew** when you need the latest committed Kotlin/assets from `main`. Use **`build.ps1`** only for a cold Windows box without Android Studio.

---

## Install on tablet

USB debugging on; tablet connected:

```powershell
adb install -r C:\Users\reasn\Documents\Claude\Projects\DEv1\_tcw-wave2\CU1\app\build\outputs\apk\debug\app-debug.apk
```

Or copy `app-debug.apk` to the tablet and install manually.

---

## Verify unit tests (scoped Plan B / OBD / discovery)

```powershell
cd C:\Users\reasn\Documents\Claude\Projects\DEv1\_tcw-wave2\CU1
.\gradlew.bat --no-daemon :app:testDebugUnitTest `
  --tests "com.caseforge.scanner.planb.*" `
  --tests "com.caseforge.scanner.obd.*" `
  --tests "com.caseforge.scanner.oem.*" `
  --tests "com.caseforge.scanner.agent.discovery.*"
```

Same filter as `_x431-work/decompile/findings/_run-cu1-g0.bat`.

---

## Operator smoke after install

1. **Settings â†’ Plan B:** Tier 0 on; tiers 1â€“4 off for first connect.
2. **Windstar:** Scan vehicle â†’ Connection readiness â†’ Find drivers & adapters (profile auto-selects from VIN when Ford MY 1999â€“2003).
3. **Ford F-150 G1:** Connect on F-150 MY 2015â€“2020 â†’ confirm G1 smoke banner on Scan vehicle; follow `023-ford-g1-smoke-runbook.md`.
4. **SKREEM:** Jeep/Dodge â†’ Tier 3 Immo + Tier 4 Programming show SKREEM info/checklist; Ford shows PATS N/A (no regression).

---

## Live update on tablet (while app running)

| Tier | Action | When |
|------|--------|------|
| **A â€” JSON only** | Settings â†’ **Sync vehicle & Plan B data** | Windstar profile, wedge matrix (Ford G1), SKREEM checklists changed on `main` â€” no reinstall |
| **B â€” APK + manifest** | Settings â†’ **Check for update** | Kotlin/UI/native changes **or** CI APK newer than tablet `versionCode`; user taps Install on system prompt |

Channel config: `app/src/main/assets/update-channel.json`  
Remote manifest (Tier A file list + Tier B hints â€” consumed by `LiveUpdateCoordinator`):  
`https://raw.githubusercontent.com/thisisthecoolesthting/rickys-control-center/main/_tcw-wave2/CU1/app/src/main/assets/update-manifest.json`

Details: `_x431-work/decompile/findings/025-live-update-apk-and-bundles.md`

### Tier B â€” refresh `update-manifest.json` (no APK build)

The coordinator compares `BuildConfig.VERSION_CODE` to remote `apk.versionCode` and optionally verifies each Tier A asset with `files[].sha256`. Regenerate the manifest from disk without Gradle:

```powershell
cd C:\Users\reasn\Documents\Claude\Projects\DEv1
powershell -ExecutionPolicy Bypass -File .\scripts\publish-cu1-update-manifest.ps1
```

Enumerates **`app/src/main/assets/planb/**`**, **`app/src/main/assets/agent/**`** (session/accessibility playbook), plus root **`marque-wedge-matrix.json`**, **`oem-decompile-bundle.json`**, **`jeep-wedge-matrix.json`** when present.

- **`apk.versionCode`:** uses **`GITHUB_RUN_NUMBER`** when the env var is set; otherwise parses the local Gradle fallback under `defaultConfig` (typically `1` for office builds). Override with **`-VersionCode N`**.
- **`revision` / `apk.buildSha`:** `git rev-parse --short HEAD` from repo root unless **`-Revision`** is passed.
- Optional **`-EmitUrls`** adds explicit raw GitHub URLs per file (default omits `url`; the app fills raw paths automatically).

Ship the updated **`_tcw-wave2/CU1/app/src/main/assets/update-manifest.json`** on `main` whenever Tier A JSON changes so tablets fetch fresh hashes and Tier B sees the intended `versionCode`.

After you `git pull` and change JSON under `app/src/main/assets/`, push to `main` so the tablet sync URL serves new files. Kotlin changes still need `assembleDebug` + install (this doc) or CI APK (Tier B).

---

## What this doc does *not* use

- No Gradle `git` URL dependencies for vehicle profiles (bundled under `app/src/main/assets/`).
- No sparse-checkout â€” full DEv1 clone is the supported path.
- CI rolling APK (`x431-ai-scanner` releases) is a separate lane; this doc is the **monorepo pull + local assembleDebug** path used for Plan B G0/G1 work on office-pc.

---

## Final debug APK (operator ship ï¿½ 2026-05-20)

| Item | Value |
|------|--------|
| **APK (full path)** | `C:\Users\reasn\Documents\Claude\Projects\DEv1\_tcw-wave2\CU1\app\build\outputs\apk\debug\app-debug.apk` |
| **GitHub runbook** | https://github.com/thisisthecoolesthting/rickys-control-center/blob/main/_tcw-wave2/CU1/BUILD_FETCH.md |
| **Build log** | C:\Users\reasn\Documents\Claude\Projects\DEv1\build-logs\cu1-final-apk.log |

```powershell
adb install -r "C:\Users\reasn\Documents\Claude\Projects\DEv1\_tcw-wave2\CU1\app\build\outputs\apk\debug\app-debug.apk"
```


---

## Web download URL (HTTPS)

| Field | Value |
|-------|-------|
| URL | https://rickyscontrolcenter.com/downloads/cu1/app-debug.apk |
| Source | Latest VPS upload (73975471 bytes; matches office `app-debug-latest.apk`) served from VPS `/var/www/downloads/cu1/` |
| Size | ~70.6 MB (73975471 bytes) |
| Refresh | Rebuild `assembleDebug`, `scp` to VPS path above, `systemctl reload caddy` (only if Caddy block changed) |

**Tablet (browser):** open the URL and install when prompted (enable unknown sources if needed).

**ADB (USB):**

```powershell
curl.exe -L -o app-debug.apk https://rickyscontrolcenter.com/downloads/cu1/app-debug.apk
adb install -r app-debug.apk
```

