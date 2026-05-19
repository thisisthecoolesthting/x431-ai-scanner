# OEM path alignment — resolver, miner, zipper

**Date:** 2026-05-19  
**Status:** static analysis (waiting for first share zip in `decompile/inbox/`)

## Zip layout (tablet Share export)

| Layer | Path | Notes |
|-------|------|--------|
| File | `tcw-bundle-<epoch>.zip` | From `VehicleDatabaseShareExport` |
| Prefix | `vehicle-database/` | Contents of winning OEM root (`/sdcard/cnlaunch/`), not an extra `cnlaunch/` folder |

Example: `/sdcard/cnlaunch/Db/ecu.sqlite` becomes `vehicle-database/Db/ecu.sqlite`.

## On-tablet roots scanned

Canonical: `/sdcard/cnlaunch/`. Also `Android/data/<oem-pkg>/files/cnlaunch` for seven Launch package names (see `VehicleDatabasePathResolver.kt`).

## OemDataMiner priorities (decompile)

| Kind | Priority | Action |
|------|----------|--------|
| DATABASE | P0 | Magic bytes, local SQLite schema (table names only in git) |
| CATALOG | P1 | Text sniff xml/ini/json |
| REPORT | P2 | Inventory only |
| OTHER | P3 | Extension histogram |

## Analyzer alignment

`analyze-share-export.ps1` treats top-level **`vehicle-database/`** as the primary OEM root (`tcw-zip-prefix`), because inner folder may not be named `cnlaunch`.

## Kotlin hooks (future)

- `OemDataIndex.fromJson()` — ingest redacted PC report summary
- `VehicleDatabaseZipper` — strip `vehicle-database/` when mapping paths
- `VciDiagnosticsScreen` / `oem_data` route — show merged inventory

## Next step

Drop zip in `decompile/inbox/`, run analyzer, copy `TEMPLATE-schema-notes.md` to `<stamp>-schema-notes.md` with real `sqlite_master` names (local only).
