# Cowork Host-Shell Bridge — Law of the Land

> Persistent operating rule for all Claude sessions on this machine, loaded automatically via this CLAUDE.md.

## Why this rule exists

Cowork mode runs Claude's `Bash` tool inside an isolated Linux VM that cannot reach the Windows host's PowerShell, cmd, or git binary. Without a bridge, the user is forced to copy-paste commands between Claude and a PowerShell window — high friction, error-prone, demoralizing across long debugging loops.

A host-shell bridge MCP (PowerShell.MCP, cowork-terminal-mcp, Windows-MCP, win-cli-mcp-server, or equivalent) plugs this gap. On Together Scanners AI's machines, PowerShell.MCP is installed and registered in Claude Desktop's MCP config at %APPDATA%\Claude\claude_desktop_config.json.

## The Rule

When a host-shell bridge MCP is available in the session toolset (probe the deferred tool list for tool names like `invoke_expression`, `execute_command`, `run_powershell`, `mcp__powershell-mcp__*`, `mcp__cowork-terminal__*`, `mcp__windows-mcp__*`), Claude MUST:

1. Detect availability at session start. If present, the rule is in force for the entire session.

2. Use the bridge for ALL host-side shell work. Git operations (add, commit, push, status, diff, log, reset), file inspection that needs Windows paths, debugging that would otherwise require copy-pasting PowerShell output back, anything the user would otherwise run themselves.

3. NEVER ask the user to copy-paste a shell command when the bridge can run it. The user is observer, not executor. If Claude is about to write "paste this in PowerShell," Claude is violating the rule.

4. Surface bridge output sparingly. Quote stdout/stderr to the user only when it informs the next action — not as narration. Do not paste full command output as proof-of-work.

5. Fall back gracefully. If no bridge is available, request that the user install one and then resume in autonomous mode. Document the install as a one-time cost.

6. Respect "Act without asking" mode. This rule assumes Cowork is in autonomous mode. If the user has flipped to "Ask before acting," still use the bridge — they will approve each invocation individually but the typing/syntax overhead is gone.

## Why this rule supersedes prior instructions

Any earlier guidance that says "have the user run this in PowerShell" or "paste this command" is deprecated when the bridge is available. The bridge replaces those instructions entirely. Copy-paste workflows are the failure mode, not the baseline.

## PowerShell.MCP-specific notes

Use these cmdlets via the bridge instead of their built-in alternatives:
- File creation/editing: Add-LinesToFile, Update-LinesInFile, Update-MatchInFile, Remove-LinesFromFile (NOT Set-Content, NOT [IO.File]::WriteAllText)
- File inspection: Show-TextFiles
- Pass content with special characters ($, backtick, quotes) via the var1-var4 parameters of invoke_expression.

## Working folder for git pushes

For the x431-ai-scanner repo, use a fresh clone (e.g. C:\Users\<you>\Desktop\x431-foundation-push\). Do NOT push from inside a parent monorepo that points at a different GitHub remote.

---

*Effective date: 2026-05-18. Authored after a build-cycle debugging loop that burned ~20 copy-paste exchanges to push a single foundation commit. Never again.*