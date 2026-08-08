# 018 Windows CU1 build sprint (G0 onward)

Companion to 014-planb-build-progress.md vertical gates for Windows precondition + compile sanity before merging Plan B tier workstreams.

Last update: 2026-05-20.

| Gate | Goal | Status | Evidence |
| --- | --- | --- | --- |
| G0 | Host JDK17 + gradlew :app:compileDebugKotlin AND :app:testDebugUnitTest | BLOCKED merge resources | Repo-root log cu1_gradle_g0.txt shows mergeDebugResources FAILED after JDK17 pin in gradle.properties |
| G1 | Plan B body scaffolding | unchanged | see 014 |

## Unified build (022 SKREEM + 127 Ford G1 + 128 Windstar)

All three slices ship in **one APK** from `_tcw-wave2/CU1`. Operator fetch steps: **`BUILD_FETCH.md`** (canonical).

Quick path:

```powershell
cd C:\Users\reasn\Documents\Claude\Projects\DEv1
git pull origin main
cd _tcw-wave2\CU1
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot"
.\gradlew.bat --no-daemon :app:assembleDebug
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

Same git-pull-then-gradlew pattern as `_x431-work/decompile/findings/_run-cu1-g0.bat` (G0 unit-test gate uses `:app:testDebugUnitTest` instead of `assembleDebug`).

## Git fetch / clone (operator)

| Step | Command |
|------|---------|
| Update source | `git pull origin main` from DEv1 repo root |
| Build debug APK | `cd _tcw-wave2\CU1` → `.\gradlew.bat --no-daemon :app:assembleDebug` |
| Install tablet | `adb install -r app\build\outputs\apk\debug\app-debug.apk` |
| Cold PC (no Studio) | `.\build.ps1` downloads JDK/SDK/Gradle into `.build-cache` — still run from a git clone first |

No Gradle git dependencies or sparse-checkout for CU1 assets — vehicle profiles and wedge matrix are **bundled in the APK** under `app/src/main/assets/`.

## Next actions

1. Local shell: gradlew.bat clean ; gradlew.bat :app:mergeDebugResources --stacktrace .
2. If merged.dir locales still missing intermittently check sync tools (OneDrive) + antivirus + optional Windows long-path policy.
3. Re-open G0 row when merge + compile + unit tests succeed and paste new log path.

See 017-jdk17-install.md for Temurin 17 pinning details. See BUILD_FETCH.md for slice smoke checklist after install.
