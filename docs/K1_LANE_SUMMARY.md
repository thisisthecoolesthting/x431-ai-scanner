# K1 Lane Summary — Data Transfer P0 Fix

Branch: `fix/data-transfer-actually-works`
Date: 2026-05-18

## Files Touched

### New files
| File | Purpose |
|---|---|
| `transfer/LanExportConfig.kt` | Top-level constants: `DEFAULT_RECEIVER_HOST`, `DEFAULT_RECEIVER_PORT` |
| `transfer/VehicleDatabasePathResolver.kt` | Renamed from the previous OEM-data resolver; adds private `OEM_DATA_PATH` constant |
| `transfer/VehicleDatabaseStorageAccess.kt` | Renamed from the previous OEM-data storage helper |
| `transfer/VehicleDatabaseZipper.kt` | Renamed from the previous OEM-data zipper; updated exception name, zip entry prefix |
| `transfer/VehicleDatabaseQuickSend.kt` | Renamed from the previous OEM-data quick-send helper; takes `SettingsRepo` + `LanPushUploader` |
| `transfer/TransferLog.kt` | Thread-safe ring buffer (500 entries) + `StateFlow`; ISO-8601 timestamps |
| `transfer/SendState.kt` | Sealed class for upload state machine + `Remediation` enum |
| `ui/transfer/OneTapSendCard.kt` | Full 6-step state machine UI; replaces the old send card |
| `ui/transfer/TransferLogScreen.kt` | Log viewer: Copy all, Email to support, Clear |
| `scripts/install-tcw-receiver.ps1` | Sets up Scheduled Task + firewall rule (idempotent) |
| `scripts/tcw-raw-receiver.ps1` | Stub; documented placeholder for raw-TCP fallback |

### Modified files
| File | Change |
|---|---|
| `transfer/LanPushUploader.kt` | Full rewrite: `/health` pre-flight, raw POST + SHA-256, resumable PATCH, `SendState` flow |
| `transfer/LanFileServer.kt` | Updated legacy zipper references to `VehicleDatabaseZipper` |
| `data/SettingsRepo.kt` | Added `receiverPcHost`, `receiverPcPort`, `useMultipartFallback` |
| `ui/transfer/ExportDataScreen.kt` | Updated to use `OneTapSendCard` with `SettingsRepo` param |
| `scripts/lan-export-receiver.ps1` | Full rewrite: all 4 routes (`/health`, `POST /upload`, `HEAD /upload`, `PATCH /upload`), sha256 verify, logging |
| `app/src/main/res/values/strings.xml` | Added `tcw_`-prefixed strings; old strings kept for K2 sweep |

### Deleted files
| File | Replaced by |
|---|---|
| Previous OEM-data resolver | `VehicleDatabasePathResolver.kt` |
| Previous OEM-data storage helper | `VehicleDatabaseStorageAccess.kt` |
| Previous OEM-data zipper | `VehicleDatabaseZipper.kt` |
| Previous OEM-data quick-send helper | `VehicleDatabaseQuickSend.kt` |
| Previous one-tap send card | `ui/transfer/OneTapSendCard.kt` |

## Behavior Changes

### Was broken, now fixed
1. **Empty zip silent success** — `VehicleDatabaseZipper` throws `EmptyVehicleDatabaseException` if 0 files found; `LanPushUploader` surfaces this as `SendState.Failed` with `GRANT_ALL_FILES` remediation before any upload attempt.
2. **No receiver feedback** — Pre-flight `GET /health` before zip work; if the PC is unreachable the send card shows the exact reason and a one-tap remediation button.
3. **Hardcoded IP** — IP/port live in `SettingsRepo.receiverPcHost`/`receiverPcPort`; default `192.168.1.129:8765`; editable from the C5 Settings screen.
4. **Multipart truncation** — Primary path is raw `POST /upload` with `Content-Length` header and SHA-256 verification (422 on mismatch → retry). Multipart kept as an explicit opt-in (`SettingsRepo.useMultipartFallback`).
5. **No resume** — `PATCH /upload?name=<fn>&offset=<n>` supported on both tablet and PC; two resume attempts before giving up.
6. **PC receiver crashes silently** — Receiver wraps every route in `try/catch`; always returns JSON; logs every request to `%TEMP%\tcw-receiver.log`.
7. **Receiver not installed / not autostarting** — `install-tcw-receiver.ps1` registers a Scheduled Task that starts at logon (hidden PowerShell window).

### New capability
- **PC health pill** polls `/health` every 30 s; green dot with save path + free MB + latency, or red dot with reason.
- **6-step progress dots** with linear bars for Zipping and Uploading (MB/s + ETA).
- **Transfer log** ring buffer; accessible via icon button on the send card → `TransferLogScreen`.
- **SHA-256 integrity** — computed on tablet after zip build, sent in URL, verified by PC on write. Mismatch → PC deletes `.part`, returns 422 → tablet retries.

## TODOs for Other Lanes

### MainActivity (C-lane or K2 merge)
- Register route `"transfer_log"` in the `NavHost` wired to `TransferLogScreen(onBack = { navController.popBackStack() })`.
- `ExportDataScreen` now takes `settings: SettingsRepo` and `onOpenTransferLog: (() -> Unit)?` — callers must pass these. The old signature `ExportDataScreen(onBack: () -> Unit)` is replaced.
- `LanFileServer.create(...)` takes `VehicleDatabaseZipper` now — any call sites outside the K1 file set (e.g. `MainScreen.kt`) need updating by K2.

### K2 Rebrand (cross-cutting)
- `strings.xml`: old `export_*` strings can be removed after K2 sweep verifies no compile references remain.
- `LanFileServer.kt` legacy download labels should stay neutral: vehicle data export, `tcw-vehicle-data.zip`.
- `DEFAULT_AGENT_NOTES` in `SettingsRepo.kt` should stay product-neutral and avoid OEM brand names.

### Settings screen (C5 lane)
- Wire `SettingsRepo.receiverPcHost` / `receiverPcPort` to text fields.
- Wire `SettingsRepo.useMultipartFallback` to a toggle (hidden under "Advanced").

### VciConnector / DiagnosticConnector (K2 or C lane)
- `DiagnosticConnector.LinkKind.OEM_USB/OEM_BT` is the expected post-rebrand naming.

## Acceptance Test (manual, fresh install)
1. Run `scripts/install-tcw-receiver.ps1` on the office PC (one-time).
2. Sideload APK on tablet with OEM diagnostic app + at least one downloaded vehicle database.
3. Grant All Files Access when prompted.
4. Open Together Car Works → Data Transfer.
5. Expected: PC pill turns green within 5 s; tap "Send to PC"; 6-step dots advance to Done; zip appears in `%USERPROFILE%\TCWBundles\`; sha256 matches.
