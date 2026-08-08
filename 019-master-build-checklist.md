# 019 — Master build checklist (CU1)

Tracks **implemented** vs **verify on device** for Plan B G0/G1. Source: `014-planb-build-progress.md` + lane E1–E8.

## Compile / G0

- [x] JDK 17 pin (`gradle.properties` + `JAVA_HOME`)
- [x] KSP clean hook in `app/build.gradle.kts`
- [ ] **G0** `assembleDebug` green on office-pc (RUN LAST)
- [ ] **G0** scoped `testDebugUnitTest` (`planb`, `obd`, `oem`, `agent.discovery`)

## Features landed in repo

- [x] E1 Marque wedge matrix + tests
- [x] E2 Copilot native OBD tools (experimental gate)
- [x] E3 Jeep/Ford/Dodge VIN hints
- [x] E4 Plan B tier toggles + connection preserve test
- [x] E5 Windstar discovery agent + Connection readiness UI
- [x] E6 SKREEM / SKIM (022) Immo + Programming
- [x] E7 Live update coordinator + Settings actions
- [x] E8 Harvester + manifest in LAN zip
- [x] G2 Gateway scaffold + tests
- [x] K1 transfer_log route + ExportDataScreen(settings)
- [x] **TCW-132** New Session — wizard (engine bay → door VIN → dashboard), `SessionChatScreen`, Room `customer_sessions`, `SessionEventLogger` harvest sidecar, `SessionWorkflowEngine` (Sonnet via `SettingsRepo.model`)

## Operator verify (post-APK)

- [ ] Ford G1 smoke banner (127)
- [ ] Windstar OTG/BT harvest (128)
- [ ] SKREEM Jeep/Dodge vs Ford PATS N/A
- [ ] Tier 0 only on first connect
- [ ] New Session wizard + chat on tablet (camera, OCR VIN, chat reply with API key)

## Gates pending code

- [ ] G1 body read beyond stub
- [ ] G4 reversible coding (flag-gated)

_Last RUN LAST update: 2026-05-20_
