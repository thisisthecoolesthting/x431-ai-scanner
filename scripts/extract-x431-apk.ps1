<#
.SYNOPSIS
Extract the X431 APK from the tablet via ADB.

.DESCRIPTION
Pulls base.apk and split APKs from the X431 tablet to apks/ directory.
Computes SHA256 hashes to verify integrity.
Idempotent: skips if APK already present and hash matches.

Requires:
  - Android Platform Tools (adb command)
  - X431 tablet connected via ADB
  - This script run from the together-decompile repository root

.EXAMPLE
.\scripts\extract-x431-apk.ps1
#>

[CmdletBinding()]
param()

$ErrorActionPreference = "Stop"
$ProgressPreference = "SilentlyContinue"

Write-Host "=== X431 APK Extraction ===" -ForegroundColor Cyan

# Get repo root
$repoRoot = Split-Path -Parent (Split-Path -Parent $PSCommandPath)
$apksDir = Join-Path $repoRoot "apks"

if (-not (Test-Path $apksDir)) {
    New-Item -ItemType Directory -Path $apksDir | Out-Null
}

# 1. Check for ADB
Write-Host "`n[1/4] Checking for ADB..." -ForegroundColor Yellow
if (-not (Get-Command adb -ErrorAction SilentlyContinue)) {
    Write-Host "  ADB not found. Installing Android Platform Tools..." -ForegroundColor Cyan
    winget install Google.PlatformTools --accept-source-agreements -e
    
    # Refresh PATH
    $env:Path = [System.Environment]::GetEnvironmentVariable("Path", "Machine") + ";" + [System.Environment]::GetEnvironmentVariable("Path", "User")
}

if (Get-Command adb -ErrorAction SilentlyContinue) {
    Write-Host "  ADB is available." -ForegroundColor Green
} else {
    Write-Host "ERROR: ADB installation failed." -ForegroundColor Red
    exit 1
}

# 2. Verify tablet connection
Write-Host "`n[2/4] Verifying tablet connection via ADB..." -ForegroundColor Yellow
$devices = adb devices | Select-Object -Skip 1 | Where-Object { $_ -match "device$" }
if ($devices.Count -eq 0) {
    Write-Host "ERROR: No ADB devices found. Ensure X431 is connected and ADB is enabled." -ForegroundColor Red
    exit 1
}

Write-Host "  Found $($devices.Count) device(s):" -ForegroundColor Green
$devices | ForEach-Object { Write-Host "    $_" }

# 3. Find and pull APK(s)
Write-Host "`n[3/4] Locating X431 APK on tablet..." -ForegroundColor Yellow
$x431Package = "com.cnlaunch.x431padv"
$apkPath = adb shell pm path $x431Package 2>&1

if ($LASTEXITCODE -ne 0 -or -not $apkPath) {
    Write-Host "ERROR: X431 package ($x431Package) not found on tablet." -ForegroundColor Red
    exit 1
}

# pm path returns "package:/data/app/com.cnlaunch.x431padv-xxx/base.apk"
# Extract the directory
$apkBase = $apkPath -replace "package:", "" -replace "/base.apk", ""

Write-Host "  APK base path: $apkBase" -ForegroundColor Green

# List all APK files in the package directory
$apkFiles = adb shell ls -la "$apkBase" | Where-Object { $_ -match "\.apk$" } | ForEach-Object { ($_ -split '\s+')[-1] }

if (-not $apkFiles) {
    Write-Host "ERROR: No APK files found in $apkBase" -ForegroundColor Red
    exit 1
}

Write-Host "  Found $($apkFiles.Count) APK file(s):" -ForegroundColor Green
$apkFiles | ForEach-Object { Write-Host "    $_" }

# Pull each APK and verify hash
Write-Host "`n[4/4] Pulling APK files..." -ForegroundColor Yellow
$hashFile = Join-Path $apksDir ".sha256"
$hashes = @{}

if (Test-Path $hashFile) {
    $hashes = @{}
    Get-Content $hashFile | ForEach-Object {
        if ($_ -match "^([a-f0-9]+)\s+(.+)$") {
            $hashes[$matches[2]] = $matches[1]
        }
    }
}

$allSuccess = $true

foreach ($apkFile in $apkFiles) {
    $localPath = Join-Path $apksDir $apkFile
    
    if (Test-Path $localPath) {
        Write-Host "  $apkFile already present. Computing hash..." -ForegroundColor Cyan
        $localHash = (Get-FileHash $localPath -Algorithm SHA256).Hash.ToLower()
        
        if ($hashes.ContainsKey($apkFile) -and $hashes[$apkFile] -eq $localHash) {
            Write-Host "    ✓ Hash matches. Skipping." -ForegroundColor Green
            continue
        } else {
            Write-Host "    Hash mismatch. Re-pulling..." -ForegroundColor Yellow
        }
    }
    
    Write-Host "  Pulling $apkFile..." -ForegroundColor Cyan
    adb pull "$apkBase/$apkFile" "$localPath" 2>&1 | Out-Null
    
    if ($LASTEXITCODE -eq 0) {
        $hash = (Get-FileHash $localPath -Algorithm SHA256).Hash.ToLower()
        $hashes[$apkFile] = $hash
        Write-Host "    ✓ Pulled: $apkFile (SHA256: $($hash.Substring(0, 16))...)" -ForegroundColor Green
    } else {
        Write-Host "    ✗ Failed to pull $apkFile" -ForegroundColor Red
        $allSuccess = $false
    }
}

# Save hashes
Write-Host "`n  Saving hash record to $hashFile" -ForegroundColor Cyan
$hashes.GetEnumerator() | ForEach-Object { "$($_.Value)  $($_.Key)" } | Set-Content $hashFile

Write-Host "`n=== Extraction Complete ===" -ForegroundColor Cyan
if ($allSuccess) {
    Write-Host "All APK files successfully extracted." -ForegroundColor Green
    Write-Host "APKs are in: $apksDir" -ForegroundColor Green
    Write-Host "`nNext: commit the extraction record and start decompilation tasks." -ForegroundColor Cyan
} else {
    Write-Host "Some APK files failed to extract. Review errors above." -ForegroundColor Red
}
