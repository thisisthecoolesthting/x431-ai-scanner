# Share-zip decompile lane — STARTED

**Date:** 2026-05-19  
**Mode:** A — no ADB; tablet **Export & share** zip only.

## Status

| Step | State |
|------|--------|
| Runbook + analyzer script | Done (`decompile/README.md`, `scripts/analyze-share-export.ps1`) |
| Zip in `decompile/inbox/` | **Waiting on operator** |
| First inventory report | Blocked until zip lands |

## Your next action

1. Tablet: **Share** → **Export & share (free)** → send/save zip to PC.
2. Copy zip to: `decompile/inbox/`
3. Run: `powershell -ExecutionPolicy Bypass -File scripts\analyze-share-export.ps1`

Reports appear under `decompile/findings/`.
