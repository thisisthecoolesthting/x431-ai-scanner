# Share-zip decompile lane (no ADB)

**Started:** 2026-05-19 -- analyze vehicle data **after** tablet **Export & share**, without pulling the OEM APK.

**Operator checklist:** `decompile/OPERATOR_CHECKLIST.md`  
**Schema template (after first zip):** `decompile/findings/TEMPLATE-schema-notes.md`

## Operator flow

1. On tablet: **Share** mode -> **Export & share (free)** -> save/send zip to this PC (Drive, email, or USB copy).
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

5. Copy `findings/TEMPLATE-schema-notes.md` to `findings/<id>-schema-notes.md` and document table names locally (DB Browser).

Extracted trees stay in **`decompile/work/`** (gitignored). Only **reports** and **redacted schema notes** are committed.

## What this pass does

- Unzip and mirror `OemDataMiner`-style inventory (extensions, DB vs catalog vs report buckets).
- Magic-byte probe on database-like files (SQLite, ZIP, gzip, etc.).
- Redacted path samples (no full proprietary paths in git).
- **Does not** ship decompiled source or cracked DB contents to the repo.

## Vendor redaction (committed files)

Applied automatically in analyzer output; apply the same when writing schema notes:

| Pattern | Replace with |
|---------|----------------|
| `cnlaunch`, `x431`, `launch pad` (case insensitive) | `oem` |
| `com.<package>.<...>` | `app` |
| Long filename in lists | Truncate to ~52 chars |

Do not commit: raw `.db` files, SQL dumps with row data, full `cnlaunch/...` path trees, or customer/session identifiers.

## Next passes (after first report)

- Map stable directory layout under the oem data root inside the extract.
- Pick one `.db` candidate and document header/schema hypotheses in `findings/<id>-schema-notes.md`.
- Wire read-only parsers into the app only after format is understood.

## Troubleshooting

### Empty zip (0 bytes or "0 files" in report)

**Symptoms:** File size 0 in Explorer; analyzer reports `Files | 0` and flat zip.

**Checks:**

1. Re-export on tablet -- wait until the share UI shows completion, not just "starting."
2. If using email/Drive, confirm download finished (partial downloads can be 0 KB).
3. Unzip manually once: `Expand-Archive` in PowerShell or 7-Zip. If empty, the tablet export failed.

**Fix:** New export -> copy again to `decompile/inbox/` -> re-run analyzer.

### Permission denied

**Symptoms:** `Access to the path ... is denied` on inbox, extract, or zip.

**Checks:**

1. Close DB Browser and any Explorer window open inside `decompile/work/`.
2. Ensure antivirus is not locking the zip (temporarily exclude inbox or copy zip to `%TEMP%` and pass `-ZipPath`).
3. Run PowerShell as your normal user with write access to the repo folder (not read-only media).
4. If the zip came from email, **Unblock** the file: file Properties -> Unblock -> OK.

**Fix:** Release locks, unblock file, re-run script. Delete stale `decompile/work/<old-stamp>/` if a prior extract failed mid-write.

### Huge zip (multi-GB, very slow, disk full)

**Symptoms:** Extract takes many minutes; drive low on space; PowerShell path-length errors.

**Checks:**

1. Confirm free space on the drive holding the repo (need roughly **2x zip size** for extract + headroom).
2. Prefer a short repo path (e.g. `C:\dev\x431-work`) to avoid Windows MAX_PATH issues under deep oem trees.
3. Report still runs shallow inventory -- you do not need to open every file for the first pass.

**Mitigations:**

- Run analyzer when disk has space; read `.md` report first before opening huge DBs in DB Browser.
- For one-off inspection, pass `-ZipPath` pointing at zip on a large data drive; extract dir is still under `decompile/work/` unless you symlink `work` (advanced).
- Delete old stamps under `decompile/work/` after notes are captured (folders are gitignored).

### No zip found (exit code 2)

Script writes `findings/<timestamp>-waiting-for-share-zip.md`.

**Fix:** Place any `*.zip` in `decompile/inbox/` or pass `-ZipPath` explicitly. Script also checks `%USERPROFILE%\Downloads\tcw-bundle*.zip`.

### Analyzer succeeds but no sqlite in probes

Export may be reports-only (PDF/HTML) or non-standard DB extensions. Use extension and kind tables in the report; open largest **database** or **catalog** candidates in a hex viewer locally if needed. Document guesses in schema notes without dumping binary to git.

## Out of scope

- SKREEM / PIN / key extraction.
- OEM license bypass.
- OpenAI or other cloud analysis of zip contents (local tools only).
