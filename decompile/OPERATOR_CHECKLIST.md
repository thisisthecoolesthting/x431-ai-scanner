# Operator checklist -- share-zip decompile lane

No ADB. No OpenAI. Tablet export -> PC inbox -> PowerShell analyzer -> local DB Browser.

Print or keep this open beside the tablet.

---

## 1. Tablet export (Together Car Works / X431 Share mode)

- [ ] Open OEM scanner app on tablet
- [ ] Switch to **Share** mode (not full diagnostic session if export is blocked)
- [ ] Tap **Export & share (free)**
- [ ] Choose delivery: USB copy, Drive, email, or save to Downloads on tablet
- [ ] Confirm the file is a `.zip` (names like `tcw-bundle-*.zip` are common)
- [ ] Note export time and vehicle/session if you will match reports later (keep off git)

**Do not** commit tablet screenshots, VINs, or customer identifiers to the repo.

---

## 2. Copy zip to repo inbox

- [ ] On PC, locate the downloaded zip
- [ ] Copy (do not move yet, if you want a backup) into:

```
<repo-root>/decompile/inbox/
```

- [ ] Prefer a clear name, e.g. `tcw-bundle-20260519.zip`
- [ ] Confirm file size is not 0 bytes (see README troubleshooting if empty)

Inbox zips are **gitignored** (`decompile/inbox/*.zip`).

---

## 3. Run analyzer (repo root, PowerShell)

```powershell
cd <repo-root>
powershell -ExecutionPolicy Bypass -File scripts\analyze-share-export.ps1
```

Optional explicit path:

```powershell
powershell -ExecutionPolicy Bypass -File scripts\analyze-share-export.ps1 -ZipPath "C:\Users\you\Downloads\tcw-bundle-123.zip"
```

- [ ] Script exits 0 (success) or read the stub if exit 2 (no zip found)
- [ ] Open `decompile/findings/<timestamp>-share-export-report.md`
- [ ] Open matching `.json` if you want machine-readable counts

Extracted tree lands in `decompile/work/<timestamp>/` (gitignored).

---

## 4. DB Browser (local only)

Install [DB Browser for SQLite](https://sqlitebrowser.org/) if needed.

- [ ] From the report, pick the largest **sqlite** candidate in database probes
- [ ] Open that `.db` / `.sqlite` file from `decompile/work/<timestamp>/` only on your PC
- [ ] Browse **schema** (table list). Do not export customer rows into the repo
- [ ] Copy `decompile/findings/TEMPLATE-schema-notes.md` to
      `decompile/findings/<timestamp>-schema-notes.md`
- [ ] Fill table names and hypotheses using redaction rules in the template

**Never** commit the raw database file or a full SQL dump.

---

## 5. Commit-safe artifacts

| Artifact | Commit? |
|----------|---------|
| `findings/*-share-export-report.md` | Yes (redacted inventory) |
| `findings/*-share-export-report.json` | Yes |
| `findings/*-schema-notes.md` | Yes (table names only, redacted paths) |
| `inbox/*.zip` | No (gitignored) |
| `work/**` extract tree | No (gitignored) |
| DB Browser exports / CSV dumps | No |

---

## 6. Handoff to agent (optional)

Point the agent at:

1. `decompile/findings/<id>-share-export-report.md`
2. `decompile/findings/<id>-schema-notes.md` (after you fill or partially fill template)
3. `decompile/README.md` for scope limits

Ask for parser design or path mapping only -- not license bypass or key extraction.

---

## Quick redaction reference

Same rules as `scripts/analyze-share-export.ps1` (`Redact-Name`):

- `cnlaunch`, `x431`, `launch pad` (any case) -> `oem`
- `com.something.long` -> `app`
- Filename samples in markdown: max ~52 characters in lists

---

## Done when

- [ ] Report `.md` + `.json` exist under `findings/`
- [ ] Schema notes file started from template (even if partial)
- [ ] Zip and extract remain local; only redacted notes committed
