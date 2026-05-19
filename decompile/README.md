# Share-zip decompile lane (no ADB)

**Started:** 2026-05-19 — analyze vehicle data **after** tablet **Export & share**, without pulling the OEM APK.

## Operator flow

1. On tablet: **Share** mode → **Export & share (free)** → save/send zip to this PC (Drive, email, or USB copy).
2. Copy the zip into **`decompile/inbox/`** (any name; `tcw-bundle-*.zip` preferred).
3. From repo root (PowerShell):

```powershell
powershell -ExecutionPolicy Bypass -File scripts\analyze-share-export.ps1
```

Or point at a file:

```powershell
powershell -ExecutionPolicy Bypass -File scripts\analyze-share-export.ps1 -ZipPath "C:\Users\you\Downloads\tcw-bundle-123.zip"
```

4. Read the report:

- `decompile/findings/<id>-share-export-report.md`
- `decompile/findings/<id>-share-export-report.json`

Extracted trees stay in **`decompile/work/`** (gitignored). Only **reports** are committed.

## What this pass does

- Unzip and mirror `OemDataMiner`-style inventory (extensions, DB vs catalog vs report buckets).
- Magic-byte probe on database-like files (SQLite, ZIP, gzip, etc.).
- Redacted path samples (no full proprietary paths in git).
- **Does not** ship decompiled source or cracked DB contents to the repo.

## Next passes (after first report)

- Map stable directory layout under `cnlaunch/`.
- Pick one `.db` candidate and document header/schema hypotheses in `findings/`.
- Wire read-only parsers into the app only after format is understood.

## Out of scope

- SKREEM / PIN / key extraction.
- OEM license bypass.
