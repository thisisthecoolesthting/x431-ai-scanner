# Analyzer capabilities (share-export, no ADB)

**Script:** `scripts/analyze-share-export.ps1`  
**Smoke:** `scripts/analyze-share-export-smoke.ps1` (parse-only, no zip)

## Inputs

| Source | Resolution |
|--------|------------|
| `-ZipPath` | Explicit file |
| Default | Newest `*.zip` in `decompile/inbox/`, else newest `tcw-bundle*.zip` in Downloads |
| Missing zip | Writes `findings/<stamp>-waiting-for-share-zip.md`, exit code 2 |

Extract tree: `decompile/work/<stamp>/` (gitignored).

## Probes (what each pass collects)

### 1. Inventory

- File count, total bytes, top-level folder names
- Extension histogram (top 15 in markdown)
- Kind buckets: `database`, `catalog`, `report`, `other` (by extension + filename heuristics)

### 2. Magic-byte probe (database-like files)

First 64 bytes read; labels:

| Label | Signature |
|-------|-----------|
| `sqlite` | `SQLite` header |
| `gzip` | `1f 8b` |
| `zip` | `PK` |
| `elf` | `7f 45 4c 46` |
| `png` | `89 50 4e 47` |
| `unknown (...)` | First 8 bytes as hex |

Up to 12 DB-kind files per run. Paths and names redacted in reports.

### 3. OEM data roots (`cnlaunch`-style)

Walks all directories under the extract:

1. **Exact:** folder name matches `cnlaunch`, `cn_launch`, `launchpad`, or `x431` (case-insensitive).
2. **Fuzzy fallback** (only if exact finds none): up to 3 dirs whose names contain `cnlaunch`, `x431`, `launch`, `oem`, `vehicle`, or `diag`.

Per root: redacted relative path, file count, total bytes under that subtree. Fuzzy hits tagged `fuzzy-match` in JSON.

### 4. Largest files (top 20)

Sorted by size descending. Each row: redacted relative path, redacted filename, size, kind bucket.

### 5. Text sniff (small config files)

| Filter | Value |
|--------|-------|
| Extensions | `.xml`, `.ini`, `.json` |
| Max file size | 64 KB (`65536`) |
| Read limit | First 2 KB UTF-8 |
| Max files | 24 (smallest first among candidates) |

Preview is one line, vendor strings redacted (`cnlaunch`, `x431`, `launch pad`, `tcw`, `com.*` packages). Not committed as separate files -- only in report JSON/markdown.

### 6. Redaction (all outputs)

- Filenames and path segments: vendor tokens -> `oem`, Java-style packages -> `app`
- Long names truncated with ASCII `...`
- No full proprietary paths in git-tracked findings

## Outputs

| Artifact | Path |
|----------|------|
| Markdown report | `decompile/findings/<stamp>-share-export-report.md` |
| Machine report | `decompile/findings/<stamp>-share-export-report.json` |

JSON keys include: `oemDataRoots`, `largestFiles`, `textSniffs`, `databaseProbes`, `kindCounts`, `kindBytes`.

## Validation

```powershell
powershell -ExecutionPolicy Bypass -File scripts\analyze-share-export-smoke.ps1
```

Checks PowerShell parser acceptance and presence of core functions (`Get-MagicLabel`, `Find-OemDataRoots`, `Get-TextSniffs`, `Redact-Name`).

## Out of scope

- ADB / APK pull
- SQLite schema dump or DB file commit
- OpenAI or other cloud APIs
- SKREEM / license bypass

## Next passes (operator)

1. Drop first real share zip in `decompile/inbox/`.
2. Run analyzer; use **OEM data roots** + **largest files** to pick DB candidates.
3. Document table names only in a follow-up `findings/<stamp>-schema-notes.md` (local DB Browser).
