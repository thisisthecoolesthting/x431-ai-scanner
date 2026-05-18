# bootstrap-together-cowork.ps1
# One-shot install for Together Scanners AI's Cowork host-shell bridge.
# Run on any fresh Windows machine from PowerShell:
#   powershell -ExecutionPolicy Bypass -Command "irm https://raw.githubusercontent.com/thisisthecoolesthting/x431-ai-scanner/main/scripts/bootstrap-together-cowork.ps1 | iex"

$ErrorActionPreference = "Continue"

Write-Host ""
Write-Host "=== Together Scanners AI - Cowork bridge bootstrap ===" -ForegroundColor Cyan
Write-Host "Host: $env:COMPUTERNAME / User: $env:USERNAME / PS: $($PSVersionTable.PSVersion)" -ForegroundColor Gray
Write-Host ""

# Step 1: PowerShell 7
$pwsh = $null
$pwshCandidates = @(
    "$env:ProgramFiles\PowerShell\7\pwsh.exe",
    "$env:LOCALAPPDATA\Microsoft\WindowsApps\pwsh.exe"
)
foreach ($c in $pwshCandidates) { if (Test-Path $c) { $pwsh = $c; break } }

if (-not $pwsh) {
    Write-Host "Installing PowerShell 7 via winget..." -ForegroundColor Yellow
    where.exe winget 1>$null 2>$null
    if ($LASTEXITCODE -ne 0) {
        Write-Host "[X] winget unavailable. Install PS 7 from https://aka.ms/powershell-release-windows then re-run." -ForegroundColor Red
        if ($Host.Name -eq "ConsoleHost") { Read-Host "Press Enter to close" }
        exit 1
    }
    winget install --id Microsoft.PowerShell --silent --accept-package-agreements --accept-source-agreements
    foreach ($c in $pwshCandidates) { if (Test-Path $c) { $pwsh = $c; break } }
}
if (-not $pwsh) {
    Write-Host "[X] PS 7 install completed but pwsh.exe not at expected location." -ForegroundColor Red
    if ($Host.Name -eq "ConsoleHost") { Read-Host "Press Enter to close" }
    exit 1
}
Write-Host "[OK] PowerShell 7: $pwsh" -ForegroundColor Green

# Step 2: relaunch in PS 7 if not already
if ($PSVersionTable.PSVersion.Major -lt 7) {
    Write-Host "Relaunching in PowerShell 7..." -ForegroundColor Yellow
    & $pwsh -ExecutionPolicy Bypass -Command "irm https://raw.githubusercontent.com/thisisthecoolesthting/x431-ai-scanner/main/scripts/bootstrap-together-cowork.ps1 | iex"
    exit
}

# === Below here we are in PS 7 ===

# Step 3: PSGallery trust
$gallery = Get-PSRepository -Name PSGallery -ErrorAction SilentlyContinue
if ($gallery -and $gallery.InstallationPolicy -ne "Trusted") {
    Set-PSRepository -Name PSGallery -InstallationPolicy Trusted
}

# Step 4: PowerShell.MCP install
$installed = Get-Module -ListAvailable -Name PowerShell.MCP | Select-Object -First 1
if (-not $installed) {
    Write-Host "Installing PowerShell.MCP from PSGallery..." -ForegroundColor Yellow
    Install-Module -Name PowerShell.MCP -Scope CurrentUser -Force -AllowClobber -ErrorAction Continue
    $installed = Get-Module -ListAvailable -Name PowerShell.MCP | Select-Object -First 1
}
if (-not $installed) {
    Write-Host "[X] PowerShell.MCP install failed." -ForegroundColor Red
    if ($Host.Name -eq "ConsoleHost") { Read-Host "Press Enter to close" }
    exit 1
}
Write-Host "[OK] PowerShell.MCP $($installed.Version)" -ForegroundColor Green

Import-Module PowerShell.MCP -Force -ErrorAction SilentlyContinue
$proxyPath = $null
try { $proxyPath = Get-MCPProxyPath 2>$null } catch { $proxyPath = $null }
if (-not $proxyPath) {
    Write-Host "[X] Could not resolve PowerShell.MCP proxy path." -ForegroundColor Red
    if ($Host.Name -eq "ConsoleHost") { Read-Host "Press Enter to close" }
    exit 1
}
Write-Host "[OK] Proxy: $proxyPath" -ForegroundColor Green

# Step 5: Claude Desktop config merge
$configPath = Join-Path $env:APPDATA "Claude\claude_desktop_config.json"
$configDir = Split-Path $configPath -Parent
if (-not (Test-Path $configDir)) { New-Item -ItemType Directory -Path $configDir -Force | Out-Null }
$config = $null
if (Test-Path $configPath) {
    $raw = Get-Content $configPath -Raw -ErrorAction SilentlyContinue
    if ($raw -and $raw.Trim()) {
        try { $config = $raw | ConvertFrom-Json -AsHashtable -ErrorAction Stop } catch { $config = $null }
    }
}
if (-not $config) { $config = [ordered]@{} }
if (-not $config.ContainsKey("mcpServers")) { $config["mcpServers"] = [ordered]@{} }
$config["mcpServers"]["powershell-mcp"] = [ordered]@{ command = $proxyPath }
$json = $config | ConvertTo-Json -Depth 20
[IO.File]::WriteAllText($configPath, $json, [Text.UTF8Encoding]::new($false))
Write-Host "[OK] Claude Desktop config: $configPath" -ForegroundColor Green

# Step 6: user-level CLAUDE.md autonomy law
$userClaudeDir = "$env:USERPROFILE\.claude"
if (-not (Test-Path $userClaudeDir)) { New-Item -ItemType Directory -Path $userClaudeDir -Force | Out-Null }
$userClaudeMd = Join-Path $userClaudeDir 'CLAUDE.md'
$marker = '# Cowork Host-Shell Bridge'
$has = (Test-Path $userClaudeMd) -and ((Get-Content $userClaudeMd -Raw) -match [regex]::Escape($marker))
if (-not $has) {
    try {
        $law = Invoke-RestMethod -Uri 'https://raw.githubusercontent.com/thisisthecoolesthting/x431-ai-scanner/main/scripts/cowork-autonomy-law.md' -ErrorAction Stop
        Add-Content -Path $userClaudeMd -Value "`r`n`r`n$law" -NoNewline -Encoding UTF8
        Write-Host "[OK] User CLAUDE.md updated with autonomy law: $userClaudeMd" -ForegroundColor Green
    } catch {
        Write-Host "[!] Could not fetch law from GitHub. Add it manually." -ForegroundColor Yellow
    }
} else {
    Write-Host "[SKIP] User CLAUDE.md already has the law" -ForegroundColor Gray
}

Write-Host ""
Write-Host "=== Manual steps remaining (the only ones left) ===" -ForegroundColor Cyan
Write-Host "1. Open Claude Desktop and the Cowork mode" -ForegroundColor White
Write-Host "2. Flip the mode dropdown to 'Act without asking'" -ForegroundColor White
Write-Host "3. Quit Claude Desktop fully (system tray -> Quit) and reopen" -ForegroundColor White
Write-Host ""
Write-Host "After reload, Claude has mcp__powershell-mcp__* tools and runs PowerShell directly on this machine." -ForegroundColor Green
Write-Host ""
if ($Host.Name -eq "ConsoleHost") { Read-Host "Press Enter to close" }