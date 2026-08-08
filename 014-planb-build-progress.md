# 014 Plan B — build progress (CU1)

Vertical gates for CaseForge standalone Plan B scaffolding.

**Single build entry (all slices):** `BUILD_FETCH.md` — git pull DEv1 → `gradlew assembleDebug` → tablet install.

| Slice | Doc | Status |
|-------|-----|--------|
| SKREEM / SKIM (022) | `_x431-work/decompile/findings/022-skreem-module.md` | **landed** — Tier 3 Immo + Tier 4 Programming; Stellantis only; Ford PATS N/A |
| Ford G1 Tier-0 smoke (127) | `_x431-work/decompile/findings/023-ford-g1-smoke-runbook.md` | **in-app landed** — G1 banner on `ObdScanScreen`; operator smoke **planned** |
| Windstar discovery (128) | `_x431-work/decompile/findings/024-windstar-2000-connection-agent.md` | **landed** — agent + UI + profile; operator verify **planned** |

| Gate | Lane | Owner | Status | Notes |
|:----:|------|-------|:------:|-------|
| G0 | Scoped JVM tests (`compileDebugKotlin` + `testDebugUnitTest`: packages `obd`, `planb`, `vin`, `oem`) | CU1 | **Fail** | `JAVA_HOME`: `C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot` (same as `org.gradle.java.home` in `gradle.properties`). KSP/Room: `app/build.gradle.kts` deletes `app/build/generated/ksp` before each `ksp*Kotlin` task. Command run did not finish green here: intermittent Android `mergeDebugResources` / `processDebugResources`; retry after `gradlew :app:clean` or deleting `app/build`. |
| G1 | Tier-1 body read contract (`BodyModuleReader`, stubs) | Plan B · body | In progress | `StubBodyModuleReader` baseline |
| G2 | Gateway lane (`planb.gateway` — NRC typing, PCM map IDs, session stub wired from `BodyReadSession` when `planbBodyRead`) | CU1 lane | **Scaffold landed** | `GatewayMap` marque routing (`ford` / `dodge` / `jeep`), `GatewaySession.marque` logging field, `BodyReadSession.marqueId`; `GatewayMapTest` covers Ford/Dodge IDs; empty DTCs until golden logs; see `StellantisGatewayNotes` for SGW neutrality |
| G3 | (reserved) | — | — | — |
| G4 | Reversible coding | Plan B · coding | Pending | gated by flags |

## E1 — Marque wedge matrix (CU1 lane) — Jeep + Ford + Dodge Tier 0

- [x] `marque-wedge-matrix.json` + `planb/MarqueWedgeConfig.kt` (multi-marque load, `findCardForVin`, `matrixSummaryLines`, `supportedMarques`, `decodeVinModelYear`; deprecated `JeepWedge*` + delegating `JeepWedgeConfig`) + `MarqueWedgeConfigTest.kt` (supersedes jeep-only asset / `jeep-wedge-matrix.json`)

## E2 — Copilot native OBD tools (CU1 lane)

- [x] `AgentTools.toolList(nativeObdExperimental)` + `read_obd_vin`, `read_obd_dtcs` (stored + pending), `read_obd_live_snapshot` — registered only when `settings.nativeObdExperimental`; `AgentRunner` executes via `ObdEngine(ObdSession(StubObdTransport()))` (read-only; stub/suspend OK). Wired from `MainActivity` + `OverlayService`.

## E3 — Jeep WMI hint (CU1 lane)

- [x] `vin/JeepVinDetector.kt` — `isLikelyJeepVin`, `marqueHint` (`Jeep wedge candidate`), WMI table in-file (`1J4`, `1J8`, `1C3`, `1C4`, `1C6`, `3C4`)
- [x] `JeepVinDetectorTest.kt`
- [x] `VinNormalizer.marqueHint` — order Jeep → Ford → Dodge (`JeepVinDetector` / `FordVinDetector` / `DodgeVinDetector`). Multi-marque wedge matrix lives under **E1** (`MarqueWedgeConfig` + `FordVinDetectorTest` / `DodgeVinDetectorTest` / `MarqueWedgeConfigTest`).
## G2 checklist (closed for stub phase)

- [x] `UdsNegativeResponse` — representative NRC set + `fromNrc`
- [x] `StellantisGatewayNotes` — SGW / diagnostics doc (neutral UI doctrine)
- [x] `GatewaySession.connect` / `readDtcs()` stub returning empty success post-connect
- [x] `GatewayMap.EcuEntry` + `jeepWedgeDefaults()` PCM `0x7E0` / `0x7E8`
- [x] `fordWedgeDefaults()` / `dodgeWedgeDefaults()` + `GatewayMap.forMarque` (`MARQUE_FORD` / `MARQUE_DODGE` / `MARQUE_JEEP`)
- [x] `GatewaySession.marque` optional field (logging)
- [x] `BodyReadSession.marqueId` + default `GatewaySession` from `forMarque`; routes DTC path through gateway when `settings.planbBodyRead`-equivalent ctor flag

## E4 — Plan B tier toggles + stay-connected (CU1)

- [x] Unified tier prefs / aliases in `SettingsRepo`, first-connect tier safety + snapshot/unlock masks, `OemEngineFacade.refreshSuspendPreserveConnection()`, `TierToggleConnectionTest` (transport disconnect count).

## G1 Ford smoke — Tier 0 (planned)

| Item | Path |
|------|------|
| Runbook | `_x431-work/decompile/findings/023-ford-g1-smoke-runbook.md` |
| Golden template | `_x431-work/decompile/golden_logs/examples/ford-f150-tier0-smoke.template.jsonl` |
| Dispatch | `cursor-dispatch/outbox/127-ford-g1-tier0-smoke.prompt.md` |
| Card id | `ford-f150-2015-2020` |
| In-app cue | `ObdScanScreen` banner + `R.string.obd_g1_smoke_ford_banner` when Ford wedge VIN detected |
| Status | **in-app landed** — operator smoke + optional CAN log per dispatch 127; tiers 1–4 off |

## E6 — SKREEM / SKIM module (022)

| Item | Path |
|------|------|
| Findings | `_x431-work/decompile/findings/022-skreem-module.md` |
| Kotlin | `planb/immo/SkreemModule.kt` |
| Tier 3 assets | `immo-info-jeep.json`, `immo-info-dodge.json`, `immo-info-ford.json` (PATS N/A) |
| Tier 4 runbook | `planb/programming-checklist-skreem.json` |
| UI | `ImmoInfoScreen`, `ProgrammingScreen` |
| Tests | `SkreemModuleTest`, `ImmoInfoServiceTest`, `ProgrammingChecklistLoaderTest` |
| Status | **landed** — no automated key learn; partner gate unchanged |

## E5 — Windstar 2000 connection readiness agent (CU1 lane)

| Item | Path |
|------|------|
| Findings | `_x431-work/decompile/findings/024-windstar-2000-connection-agent.md` |
| Vehicle profile | `app/src/main/assets/planb/vehicle-profiles/ford-windstar-2000.json` |
| Wedge card | `marque-wedge-matrix.json` → `ford-windstar-2000` |
| Discovery agent | `agent/discovery/TabletHardwareDiscoveryAgent.kt` |
| Copilot tool | `scan_connection_readiness` (`AgentTools` + `AgentRunner`) |
| UI | `ConnectionReadinessPanel` on OBD scan; Settings link |
| Dispatch | `cursor-dispatch/outbox/128-windstar-driver-discovery-agent.prompt.md` |
| Tests | `VehicleProfileLoaderTest`, `UsbSerialChipIdsTest`, `TabletHardwareDiscoveryAgentTest` |
| Status | **landed** — operator verify on 2000 Windstar + OTG/BT adapter |

## E7 — Live update Tier A/B (CU1 lane)

| Item | Path |
|------|------|
| Findings | `_x431-work/decompile/findings/025-live-update-apk-and-bundles.md` |
| Coordinator | `com.caseforge.scanner.update.LiveUpdateCoordinator` |
| Settings | Sync vehicle & Plan B data + Check for update |
| Copilot | `check_for_updates`, `sync_vehicle_profiles` |
| Dispatch | `cursor-dispatch/outbox/129-live-update-from-git.prompt.md` |
| Status | **Scaffold landed** — overlay loaders + HTTPS sync; CI manifest publish = 129 |

## E8 — Harvester driver batch (CU1 lane)

| Item | Path |
|------|------|
| Findings | `_x431-work/decompile/findings/026-tablet-data-and-missing-features.md` |
| Harvester | `transfer/TabletDataHarvester.kt` + `HarvestBatch.kt` |
| Coordinator | `transfer/HarvestUploadCoordinator.kt` → `LanPushUploader` |
| Zip entry | `harvest-batch/manifest.json` |
| UI | Settings **Harvest & upload**; Connection readiness panel after scan |
| Status | **done** — driver sidecar on every LAN upload |

