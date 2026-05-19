# Schema notes -- <report-id>

**Status:** draft | reviewed  
**Paired report:** `decompile/findings/<report-id>-share-export-report.md`  
**Local extract (not in git):** `decompile/work/<report-id>/`

Fill this after the first successful analyzer run. Copy this file to
`<report-id>-schema-notes.md` before editing.

---

## Primary database candidate

| Field | Value |
|-------|-------|
| Redacted filename | (e.g. `vehicle_data.db` -- use Redact-Name rules below) |
| Magic (from report) | sqlite / unknown |
| Size (approx) | KB or MB |
| Relative path inside extract | folder names only; redact vendor tokens |

## Tables (names only)

List `sqlite_master` table names. Do **not** paste row data, VINs, keys, or DTC text.

| Table | Role guess | Notes |
|-------|------------|-------|
| | | |

## Columns (hypothesis)

Document only what you need for a future read-only parser. Prefer aggregate notes
over wide dumps.

| Table | Column | Type guess | Notes |
|-------|--------|------------|-------|
| | | | |

## Relationships / keys

- Primary keys observed:
- Foreign-key guesses:
- Indexes worth noting:

## Non-DB artifacts (optional)

| Kind | Redacted name | Purpose guess |
|------|---------------|---------------|
| catalog | | |
| report | | |

## Open questions

1.
2.

## Redaction rules (required before commit)

When this file is committed:

- Replace vendor tokens in names: `cnlaunch`, `x431`, `launch pad` -> `oem`
- Replace package-like strings: `com.*` -> `app`
- No full proprietary directory trees; use `.../oem/...` style paths
- No binary DB files, no SQL dumps with live data, no screenshots of customer data
- Truncate any single filename sample to 52 characters in prose lists

## Next engineering step

- [ ] Confirm sqlite candidate opens in DB Browser (local only)
- [ ] Map one table to an app feature (menu tree, history, DTC list, etc.)
- [ ] File a follow-up task if a read-only parser is justified
