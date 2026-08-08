# Task 203 — Fix the LAN file-transfer server (URL doesn't open from PC)

**Symptom:** Ricky tested task 200's "Export tablet data to PC" — the URL the app displayed didn't open in any browser on his PC.

## Likely causes (debug in this order)

1. **Wrong IP shown.** The app probably grabbed a non-active interface address — e.g., a stale Wi-Fi IP, a tethering IP, an IPv6 link-local, or 0.0.0.0. Fix: enumerate `NetworkInterface.getNetworkInterfaces()`, pick the one that has a non-loopback IPv4 AND is `up` AND has the SAME subnet that the PC is likely on. Show ALL candidate IPs in the UI as a list — let the tech pick if auto-detect picks wrong.
2. **Server didn't actually start.** Compose recomposition may have killed it, or NanoHTTPD threw on startup with the exception swallowed. Fix: hoist the server lifecycle out of the composable into a `LifecycleService` or a `remember { LanFileServer(...) }` block whose start/stop is keyed to the screen's lifecycle. Log every server state transition (`STARTING`, `LISTENING`, `STOPPED`, `ERROR(reason)`) and surface the current state in the UI in real time.
3. **PC firewall blocking inbound.** Common on Windows. Fix: print clear instructions on the tablet's UI: "If your browser shows ERR_CONNECTION_TIMED_OUT, your PC firewall is blocking port 8765. Run this once in PowerShell: `New-NetFirewallRule -DisplayName 'Together File Transfer' -Direction Inbound -LocalPort 8765 -Protocol TCP -Action Allow`."
4. **Wi-Fi AP isolation.** Some routers block peer-to-peer traffic between Wi-Fi clients. Detect by testing the tablet's connection to itself first — if `curl http://<tablet_ip>:8765/` from the tablet's adb shell works but PC can't reach it, AP isolation is on. Surface that finding clearly.
5. **Port collision.** Try 8765 first; on bind failure sweep 8000-8999 until one binds. Display the actual bound port.

## What to add

- **Live server-state widget** at the top of `ExportDataScreen` — green dot + "LISTENING on 192.168.1.42:8765" or red dot + actual error.
- **"Test from this tablet"** button — does an in-app HTTP GET to `http://localhost:<port>/health` and shows pass/fail. If pass: server is up; the problem is network. If fail: server itself is broken.
- **Multi-IP list** — show every candidate IPv4 address, let the tech pick.
- **QR code** to whichever URL is selected, regenerated when selection changes.

## Files

MODIFY:
- `app/src/main/kotlin/com/caseforge/scanner/transfer/LanFileServer.kt`
- `app/src/main/kotlin/com/caseforge/scanner/ui/transfer/ExportDataScreen.kt`

CREATE (if not done in 200):
- `app/src/main/kotlin/com/caseforge/scanner/transfer/NetworkInterfaceHelper.kt` — picks best LAN IP.

## Acceptance

- Tablet shows LIVE server state (green/red with reason).
- Tablet shows list of all candidate IPs; can pick.
- Tablet's in-app "Test from this tablet" button passes → confirms server itself is up.
- PC browser on same LAN can connect to the displayed URL.
- 6-digit pass code still gates `/download`.
- Stop button kills the server cleanly.

## Done

Branch `fix/lan-transfer-server`, self-merge on CI green. Move prompt to `cursor-dispatch/done/`.
