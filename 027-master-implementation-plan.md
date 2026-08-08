# 027 — Master implementation plan (CU1 RUN LAST)

**Repo:** `_tcw-wave2/CU1`  
**When:** After parallel lanes land Kotlin/assets in CU1.  
**Build:** `gradlew :app:assembleDebug` with Temurin 17 (`017-jdk17-install.md`).

## Lanes consolidated (expected in tree)

| Lane | Scope | Acceptance |
|------|--------|------------|
| E1 | `marque-wedge-matrix.json`, `MarqueWedgeConfig` | Unit tests green |
| E2 | Copilot OBD tools (`read_obd_*`) | Gated by `nativeObdExperimental` |
| E3 | Jeep WMI / multi-marque VIN hints | Detector tests |
| E4 | Plan B tier toggles + stay-connected | `TierToggleConnectionTest` |
| E5 | Windstar 2000 discovery agent + UI | Profile + `scan_connection_readiness` |
| E6 | SKREEM module (022) | Immo + Programming screens |
| E7 | Live update Tier A/B | Settings sync + check for update |
| E8 | Harvester driver batch (026) | `harvest-batch/manifest.json` in zip |
| G2 | Gateway scaffold | `GatewayMapTest` |
| K1 | LAN transfer P0 | `transfer_log` route, `ExportDataScreen` signature |

## RUN LAST checklist

1. Grep: no `<<<<<<<` conflict markers under `app/src`.
2. Grep prior logs: `SettingsScreen`, `Harvester`, `Duplicate class`.
3. Merge duplicate filenames only if two paths define same JVM class (rename or delete stub).
4. Update `019-master-build-checklist.md`.
5. Clean `app/build`, `gradlew --stop`, `assembleDebug`.
6. Update `BUILD_FETCH.md` APK path + `014` G0 row.

## Known flaky build (mitigation)

- `mergeDebugResources` / missing `values-watch-v21.xml`: delete `app/build` before assemble.
- KSP/Room: `app/build.gradle.kts` clears `generated/ksp` before ksp tasks.

## Incomplete lane signals (still attempt compile)

- Operator smoke (127 Ford G1, 128 Windstar): in-app only until tablet.
- G0 scoped unit tests: run after green `assembleDebug`.
- CI rolling APK: separate from monorepo path.

