# cursor-dispatch — Together Scanners AI

Numbered task prompts for Cursor (with Codex). House style follows Ricky's CaseForge cursor-dispatch convention.

## How it works

- `outbox/` — pending tasks, take in numeric order
- `done/` — completed tasks, move here when finished (git mv)
- Each task is a self-contained `.prompt.md` file with goal, files, acceptance criteria

## Current queue (in order)

| # | Task | Owner |
|---|------|-------|
| 001 | Fix CI red, confirm green on main | Cursor / Codex |
| 002 | Integrate F8 (Recall/TSB Auto-Flag) | Cursor Composer |
| 003 | Integrate F3 (Evidence Capture + Repair Story PDF) | Cursor Composer |
| 004 | Integrate F1 (Predictive Next-Test) | Codex CLI |
| 005 | Integrate F2 (Cross-Module Correlator) | Codex CLI |
| 006 | Integrate F5 (Hands-Free Voice Mode) | Cursor Composer |
| 007 | Integrate F7 (Multi-Step Test Sequences) | Codex CLI |
| 008 | Apply OEM corrections (47 entries) | Cursor Composer |
| 009 | Phase 2 spike: Direct VCI (2-week budget) | Codex CLI, `spike/direct-vci` branch |
| 010 | Tablet smoke pass (TESTING.md checklist) | Human (Ricky) + Cursor for any code fixes |

Add new tasks as `cursor-dispatch/outbox/NNN-<slug>.prompt.md`. Number monotonically.
