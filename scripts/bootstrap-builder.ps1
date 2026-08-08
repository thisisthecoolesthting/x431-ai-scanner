<#
.SYNOPSIS
Bootstrap the builder machine with all tools needed for OEM tablet decompilation pipeline.

.DESCRIPTION
Installs:
  - JDK 21
  - JADX decompiler
  - Git
  - Cursor IDE
  - Codex CLI (via npm)
  - PowerShell MCP bridge
  - Clones the together-decompile queue repo

This script is idempotent: re-running it skips already-installed tools.

.EXAMPLE
.\bootstrap-builder.ps1
#>

[CmdletBinding()]
param()

# Ensure we're running as admin
if (-not ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
    Write-Host "ERROR: This script must run as Administrator." -ForegroundColor Red
    exit 1
}

$ErrorActionPreference = "Stop"
$ProgressPreference = "SilentlyContinue"

Write-Host "=== Together Car Works OEM Decompile Bootstrap ===" -ForegroundColor Cyan

# Helper function: Check if a command exists
function Test-CommandExists {
    param([string]$Command)
    $null = Get-Command $Command -ErrorAction SilentlyContinue
    return $?
}

# 1. Install JDK 21
Write-Host "`n[1/7] Checking JDK 21..." -ForegroundColor Yellow
if (Test-CommandExists "java") {
    $javaVersion = & java -version 2>&1 | Select-String "21"
    if ($javaVersion) {
        Write-Host "  JDK 21 already installed." -ForegroundColor Green
    } else {
        Write-Host "  Java is installed but not JDK 21. Installing JDK 21..." -ForegroundColor Cyan
        winget install Microsoft.OpenJDK.21 --accept-source-agreements -e
    }
} else {
    Write-Host "  Installing JDK 21..." -ForegroundColor Cyan
    winget install Microsoft.OpenJDK.21 --accept-source-agreements -e
}

# Refresh PATH for subsequent commands
$env:Path = [System.Environment]::GetEnvironmentVariable("Path", "Machine") + ";" + [System.Environment]::GetEnvironmentVariable("Path", "User")

# 2. Install JADX
Write-Host "`n[2/7] Checking JADX..." -ForegroundColor Yellow
if (Test-CommandExists "jadx") {
    Write-Host "  JADX already installed." -ForegroundColor Green
} else {
    Write-Host "  Installing JADX..." -ForegroundColor Cyan
    winget install skylot.jadx --accept-source-agreements -e
    if (-not $?) {
        Write-Host "  Fallback: downloading JADX from GitHub..." -ForegroundColor Cyan
        $jadxUrl = "https://github.com/skylot/jadx/releases/latest/download/jadx-windows.zip"
        $jadxZip = "$env:TEMP\jadx-windows.zip"
        $jadxDir = "$env:ProgramFiles\jadx"
        
        if (-not (Test-Path $jadxDir)) {
            New-Item -ItemType Directory -Path $jadxDir | Out-Null
        }
        
        Invoke-WebRequest -Uri $jadxUrl -OutFile $jadxZip
        Expand-Archive -Path $jadxZip -DestinationPath $jadxDir -Force
        Remove-Item $jadxZip
        
        # Add to PATH
        if (-not ($env:Path -like "*jadx*")) {
            [Environment]::SetEnvironmentVariable("Path", "$env:Path;$jadxDir\bin", [System.EnvironmentVariableTarget]::Machine)
        }
    }
}

# 3. Install Git
Write-Host "`n[3/7] Checking Git..." -ForegroundColor Yellow
if (Test-CommandExists "git") {
    Write-Host "  Git already installed." -ForegroundColor Green
} else {
    Write-Host "  Installing Git..." -ForegroundColor Cyan
    winget install Git.Git --accept-source-agreements -e
}

# Refresh PATH
$env:Path = [System.Environment]::GetEnvironmentVariable("Path", "Machine") + ";" + [System.Environment]::GetEnvironmentVariable("Path", "User")

# 4. Install Cursor IDE
Write-Host "`n[4/7] Checking Cursor IDE..." -ForegroundColor Yellow
if (Test-Path "$env:ProgramFiles\Cursor\Cursor.exe") {
    Write-Host "  Cursor IDE already installed." -ForegroundColor Green
} else {
    Write-Host "  Installing Cursor IDE..." -ForegroundColor Cyan
    winget install Anysphere.Cursor --accept-source-agreements -e
}

# 5. Install Node.js and Codex CLI
Write-Host "`n[5/7] Checking Node.js and Codex CLI..." -ForegroundColor Yellow
if (Test-CommandExists "npm") {
    Write-Host "  Node.js/npm already installed." -ForegroundColor Green
} else {
    Write-Host "  Installing Node.js..." -ForegroundColor Cyan
    winget install OpenJS.NodeJS --accept-source-agreements -e
}

# Refresh PATH
$env:Path = [System.Environment]::GetEnvironmentVariable("Path", "Machine") + ";" + [System.Environment]::GetEnvironmentVariable("Path", "User")

# Install Codex CLI (note: package name may vary; adjust if needed)
Write-Host "  Installing Codex CLI via npm..." -ForegroundColor Cyan
try {
    npm install -g @openai/codex 2>&1 | Out-Null
    Write-Host "  Codex CLI installed." -ForegroundColor Green
} catch {
    Write-Host "  WARNING: Codex CLI install may have failed. Verify manually: npm install -g @openai/codex" -ForegroundColor Yellow
}

# 6. PowerShell MCP Bridge
Write-Host "`n[6/7] Checking PowerShell MCP bridge..." -ForegroundColor Yellow
$mcp_bridge_script = "https://raw.githubusercontent.com/thisisthecoolesthting/together-car-works/main/scripts/bootstrap-together-cowork.ps1"
if (Test-Path "$env:ProgramFiles\Together\PowerShell.MCP.Bridge.exe") {
    Write-Host "  PowerShell MCP bridge already installed." -ForegroundColor Green
} else {
    Write-Host "  Installing PowerShell MCP bridge..." -ForegroundColor Cyan
    try {
        $bridge_script = Invoke-WebRequest -Uri $mcp_bridge_script -UseBasicParsing | Select-Object -ExpandProperty Content
        Invoke-Expression $bridge_script
        Write-Host "  PowerShell MCP bridge installed." -ForegroundColor Green
    } catch {
        Write-Host "  WARNING: PowerShell MCP bridge install failed. Verify manually or install from together-car-works repo." -ForegroundColor Yellow
    }
}

# 7. Clone the Queue Repository
Write-Host "`n[7/7] Setting up queue repository..." -ForegroundColor Yellow
$queueRepoPath = "$env:USERPROFILE\together-decompile"
if (Test-Path $queueRepoPath) {
    Write-Host "  Queue repository already exists at $queueRepoPath" -ForegroundColor Green
    Push-Location $queueRepoPath
    git pull origin main 2>&1 | Out-Null
    Write-Host "  Updated queue repository from origin." -ForegroundColor Green
    Pop-Location
} else {
    Write-Host "  Cloning queue repository to $queueRepoPath..." -ForegroundColor Cyan
    git clone https://github.com/together-scanners-ai/together-decompile.git $queueRepoPath
    Write-Host "  Queue repository cloned." -ForegroundColor Green
}

# Verification
Write-Host "`n=== Bootstrap Complete ===" -ForegroundColor Cyan
Write-Host "`nVerifying installations..." -ForegroundColor Yellow

$installs = @{
    "java" = "JDK 21"
    "jadx" = "JADX"
    "git" = "Git"
    "npm" = "Node.js/npm"
}

foreach ($cmd in $installs.Keys) {
    if (Test-CommandExists $cmd) {
        Write-Host "  ✓ $($installs[$cmd]) is available" -ForegroundColor Green
    } else {
        Write-Host "  ✗ $($installs[$cmd]) is NOT available" -ForegroundColor Red
    }
}

if (Test-Path "$env:USERPROFILE\together-decompile\queue\inbox") {
    Write-Host "  ✓ Queue repository is cloned and ready" -ForegroundColor Green
} else {
    Write-Host "  ✗ Queue repository was not set up correctly" -ForegroundColor Red
}

Write-Host "`nNext steps:" -ForegroundColor Cyan
Write-Host "  1. cd $queueRepoPath"
Write-Host "  2. Review queue/inbox/ for available tasks"
Write-Host "  3. Claim a task and move it to queue/in-progress/"
Write-Host "  4. Follow task instructions and commit findings/"
Write-Host ""
