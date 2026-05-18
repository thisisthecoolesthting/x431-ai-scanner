# Task 008 — Apply OEM Path Corrections & Re-Merge Baseline

**Goal:** Replace 8 OEM JSON files at `app/src/main/assets/capabilities/<oem>.json` with corrected versions from `drafts/OEM-FIX/`. Re-merge into single `capabilities.json` baseline (242 unique entries, dedupe by id with remote-wins on collision).

## What ships

1. Eight corrected OEM JSON files (copy from `drafts/OEM-FIX/`): replace originals at `app/src/main/assets/capabilities/`.
2. Re-merge script: dedupe 242 entries by `id`, collision strategy = remote (newer version) wins, output to single `capabilities.json`.
3. Validation: ensure all entries valid JSON, no orphaned references.
4. Commit merged baseline.

## Files to read

- `drafts/OEM-FIX/` — 8 JSON files (the corrections)
- `app/src/main/assets/capabilities/` — current OEM files (to be replaced)
- `decompile/findings/012-menu-tree-apk-verified.md` (context: 47 corrections already applied)

## Files to write/modify

- **Replace:** `app/src/main/assets/capabilities/{oem1}.json`, `{oem2}.json`, …, `{oem8}.json` (from drafts/OEM-FIX/)
- **Create/Update:** `app/src/main/assets/capabilities/capabilities.json` (merged + deduped baseline)
- **Create:** (optional) merge script `scripts/merge-capabilities.py` or similar if needed for future baseline updates

## Acceptance

- All 8 OEM files replaced.
- Merged baseline contains exactly 242 unique entries (by id).
- No duplicate ids.
- No JSON parse errors; all entries valid.
- App loads capabilities without error (test via CapabilityMap.init()).
- Commit message: `Apply OEM path corrections (47 entries fixed per finding 012)`.

## Done

`git mv cursor-dispatch/outbox/008-apply-oem-corrections.prompt.md cursor-dispatch/done/`, commit, push to main.
