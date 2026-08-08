# Tier 4 — Manual / partner-only flash runbook

**Audience:** Authorized partner shops and OEM tooling operators. CaseForge Scanner does **not** perform PCM, immobilizer, or security provisioning in Tier 4. Use this checklist with your licensed workflow only.

---

## Jeep (Stellantis / FCA family)

1. Confirm battery stabilizer (>13 V under load preference) and **ignition/power mode** matches the tooling requirement (often ON, not crank).
2. Resolve **gateway / Security Gateway (SGW)** policy before asserting flash paths; some vehicles require unlocking or routed sessions documented for that VIN/market module set.
3. Identify **PCM (or ECM)** and compatible controller family for the calibration part number; discard partial / interrupted sessions per OEM procedure—never power-drop mid-write.
4. Complete **SKIM/SKREEM-sensitive steps** strictly on OEM or partner-certified tools requiring PIN/key strategy; record pre/post IDS or module DID snapshots when your program requires receipts.
5. Post-flash: clear **adaptives/learned values** if the procedure dictates, cycle ignition, validate **RPM/fuel trims/DTC readiness** against baseline, and archive the session log under your shop QA policy.

---

## Ford

1. Verify **Ford Powertrain Control Module flash** prerequisites (charging, DLC integrity, DLC adapter type for long sessions).
2. For **PATS-related** architectures, flash and security steps stay on IDS / FM / authorized equivalents—no improvised key or module marry from this product.
3. Apply **strategy / As-Built data** strictly from approved sources matched to hardware type and PCM strategy ID; reconcile after any module replacement.
4. After flash or module exchange: execute **PCM relearn / KOER / crank relearn / TPMS resets** **only when** mandated by calibration notes (avoid unnecessary resets).
5. Document **strategy ID**, tool session ID or export, and mileage/timestamp before returning the vehicle.

---

## Dodge / Ram (Stellantis)

1. Assume **PCM and security provisioning** behave like Jeep-family Stellantis policy: gateway and regional security rules first.
2. For **Ram DT**–class platforms: confirm **Ethernet vs legacy bus** tooling path as required by authorized software for that MY; mismatch causes false timeouts.
3. **RFH / SCM / SKREEM-linked** workflows stay partner-only—match PIN entry and ignition sequence to OEM notes.
4. **Manual flash interruption:** follow OEM recovery procedure (some platforms require dealership re-init); abort if adapters or gateway lockout are unresolved.
5. Close with **verification drive / monitor DTC latch / emission readiness** criteria your shop mandates for Tier 4 sign-off.

---

## Cross-cutting (Jeep / Ford / Dodge)

- **Firmware source:** Authorized portal or subcontractor feed only—incompatible images brick modules.
- **Liability:** The technician verifies every step independently; Tier 4 is never automated from CaseForge Scanner.
- **Rollback:** Maintain a reproducible rollback plan (prior calibration backup, donor module clause) before starting.

---

_Copy also ships in the APK as `planb/Tier4-Manual-Flash-Runbook.md`._
