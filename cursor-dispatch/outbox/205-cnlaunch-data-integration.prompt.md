# Task 205 — cnlaunch data integration (TRIGGERED when Ricky uploads)

**Do NOT start this task until:**
- Task 204 is merged (single-screen UI live).
- Ricky has uploaded `/sdcard/cnlaunch/` via task 203's LAN transfer.
- The zip has been placed at `E:\Projects\together-decompile\data\cnlaunch-bundle.zip` (or wherever Ricky parks it; ask in chat if uncertain).

## What to extract from the data

The cnlaunch folder typically contains, per OEM:

1. **DTC code → description** mappings (in `.txt`, `.xml`, or sometimes binary `.dat`)
2. **Menu tree definitions** — the exact navigation paths X431 uses (text strings per language)
3. **Per-OEM native `.so` libraries** — proprietary protocol layers (KWP2000, UDS, OEM-specific)
4. **Vehicle/year/engine mapping tables** — VIN → which protocol/.so/menu

For Phase 2 v1, we care about #1 and #2 — those make Together's standalone diagnostics richer. #3 and #4 are Phase 2 v2+ (deferred).

## Pipeline

1. **Unzip + crawl** — write `scripts/index_cnlaunch.py` (already partially seeded as `scripts/build_cnlaunch_assets.py`; extend it). Walks the unzipped tree, classifies every file by extension + first-bytes signature.
2. **Build two JSON indexes** at `app/src/main/assets/cnlaunch/`:
   - `dtc-descriptions.json` — keyed by `<oem>:<code>` → description (English). Pull from any plain-text or XML file that matches the DTC pattern `[PBCU][0-9A-F]{4}`.
   - `menu-trees.json` — keyed by `<oem>:<capability-id>` → ordered list of menu strings. Use these to override the educated-guess paths in our CapabilityMap baseline.
3. **Wire into runtime**:
   - `VciCommunicator.readDtcs()` annotates each `Dtc.description` from the JSON index instead of leaving it blank.
   - `CapabilityCatalogStore.load()` merges the menu-tree JSON over the baked baseline (menu-tree entries win on collision for the path field).
   - Keep cache + hot-patch fetch behavior intact.
4. **Test on real DTCs**: pick 5 common codes (P0420, P0171, P0300, P0455, U0100) — each one should now return a description in the Together UI when scanned. If any returns empty, the indexing missed that OEM's table format — fix the crawler.
5. **OPTIONAL Phase 2 v2 hook**: archive the per-OEM `.so` files at `app/src/main/jniLibs/<abi>/` ONLY if you have time and they're <10 MB total. Otherwise zip them as `app/src/main/assets/cnlaunch/native-libs.zip` (do NOT load them yet — that's a future task).

## Files

CREATE:
- `app/src/main/assets/cnlaunch/dtc-descriptions.json`
- `app/src/main/assets/cnlaunch/menu-trees.json`
- `scripts/index_cnlaunch.py` (or extend the existing builder)

MODIFY:
- `app/src/main/kotlin/com/caseforge/scanner/vci/VciCommunicator.kt` — annotate DTCs from the index.
- `app/src/main/kotlin/com/caseforge/scanner/engine/CapabilityCatalogStore.kt` — overlay menu-tree JSON onto baseline.

## Size warning

The cnlaunch zip is likely 5-20 GB. Don't commit the raw zip to git. Add `data/cnlaunch-bundle.zip` and `data/cnlaunch-extracted/` to `.gitignore`. Only the derived JSON indexes ship in the APK (which keeps the APK under ~80 MB).

## Acceptance

- After upload + integration, scanning a vehicle with a known DTC returns the human-readable description (e.g., "P0420 — Catalyst System Efficiency Below Threshold Bank 1").
- CapabilityMap entries for at least 4 of the 8 OEMs now reflect the cnlaunch menu paths (verify by spot-checking 5 capabilities per OEM).
- APK size remains under 80 MB.
- CI green on `feat/cnlaunch-data` branch. Self-merge.

## Done

When this merges, the app is feature-complete for v1. Move this prompt to `cursor-dispatch/done/`. Notify Ricky: **"v1 ship-ready. Tablet test welcomed."**
