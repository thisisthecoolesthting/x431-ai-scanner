# Task 003 — Integrate F3: Evidence Capture & Repair Story PDF

**Goal:** Integrate `drafts/F3-REPAIR-STORY/`. Add "Capture Evidence" button mid-diagnosis that bookmarks live-data graphs + snaps photos via tablet camera. Composes with existing PDF report generator and Customer/RO DB.

## What ships

1. New composable `app/src/main/kotlin/com/caseforge/scanner/overlay/compose/screens/EvidenceCaptureScreen.kt` — launches camera intent, captures image, stores in Evidence DB.
2. New data class `app/src/main/kotlin/com/caseforge/scanner/data/EvidenceRecord.kt` — {timestamp, VIN, DTC, photoUri, graphSnapshot}.
3. New Room DAO/Entity in `data/` — Evidence table, keyed by (repairOrderId, timestamp).
4. New ScreenKind enum variant: `EvidenceCapture` in `overlay/compose/ScreenKind.kt`.
5. "Capture Evidence" FAB in LiveDataScreen that routes to EvidenceCapture.
6. Modify PDF report generator to include Evidence gallery (photo grid) if evidence records exist.
7. Add `android.permission.CAMERA` to manifest if not already present.

## Files to read

- `drafts/F3-REPAIR-STORY/` (all Kotlin sources + sample photo)
- `app/src/main/kotlin/com/caseforge/scanner/data/ReportGenerator.kt` (existing PDF builder)
- `app/src/main/kotlin/com/caseforge/scanner/data/CustomerDb.kt` (Room config)
- `app/src/main/AndroidManifest.xml` (check CAMERA permission)
- `app/src/main/kotlin/com/caseforge/scanner/overlay/compose/screens/LiveDataScreen.kt` (where button lives)

## Files to write/modify

- **Create:** `app/src/main/kotlin/com/caseforge/scanner/overlay/compose/screens/EvidenceCaptureScreen.kt`
- **Create:** `app/src/main/kotlin/com/caseforge/scanner/data/EvidenceEntity.kt` (Room entity + DAO)
- **Modify:** `overlay/compose/ScreenKind.kt` — add `EvidenceCapture` variant
- **Modify:** `overlay/compose/screens/LiveDataScreen.kt` — add "Capture Evidence" FAB
- **Modify:** `data/ReportGenerator.kt` — inject Evidence gallery into PDF if present
- **Modify:** `AndroidManifest.xml` — add CAMERA permission if missing

## Acceptance

- Compiles clean.
- Camera permission declared and requested at runtime.
- EvidenceCaptureScreen launches system camera, saves photo to Evidence DB.
- Photo appears in PDF report gallery section.
- No regression in existing PDF generator API.

## Done

`git mv cursor-dispatch/outbox/003-integrate-f3-repair-story-pdf.prompt.md cursor-dispatch/done/`, commit `Bundle: F3 Evidence capture + Repair Story PDF`, push to main.
