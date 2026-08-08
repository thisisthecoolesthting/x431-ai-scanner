<#
.SYNOPSIS
Extract the OEM diagnostic tablet APK from the device via ADB.

.DESCRIPTION
Pulls base.apk and split APKs from the tablet to apks/ directory.
Computes SHA256 hashes to verify integrity.
Idempotent: skips if APK already present and hash matches.

Requires:
  - Android Platform Tools (adb command)
  - OEM diagnostic tablet connected via ADB
  - This script run from the repository root

.EXAMPLE
.\scripts\extract-oem-tablet-apk.ps1
#>

[CmdletBinding()]
param()

$ErrorActionPreference = "Stop"
$ProgressPreference = "SilentlyContinue"

Write-Host "=== OEM tablet APK extraction ===" -ForegroundColor Cyan

$repoRoot = Split-Path -Parent (Split-Path -Parent $PSCommandPath)
$apksDir = Join-Path $repoRoot "apks"

if (-not (Test-Path $apksDir)) {
    New-Item -ItemType Directory -Path $apksDir | Out-Null
}

Write-Host "`n[1/4] Checking for ADB..." -ForegroundColor Yellow
if (-not (Get-Command adb -ErrorAction SilentlyContinue)) {
    Write-Host "  ADB not found. Installing Android Platform Tools..." -ForegroundColor Cyan
    winget install Google.PlatformTools --accept-source-agreements -e
    $env:Path = [System.Environment]::GetEnvironmentVariable("Path", "Machine") + ";" + [System.Environment]::GetEnvironmentVariable("Path", "User")
}

if (-not (Get-Command adb -ErrorAction SilentlyContinue)) {
    Write-Host "ERROR: ADB installation failed." -ForegroundColor Red
    exit 1
}
Write-Host "  ADB is available." -ForegroundColor Green

Write-Host "`n[2/4] Verifying tablet connection via ADB..." -ForegroundColor Yellow
$devices = adb devices | Select-Object -Skip 1 | Where-Object { $_ -match "device$" }
if ($devices.Count -eq 0) {
    Write-Host "ERROR: No ADB devices found. Ensure the tablet is connected and ADB is enabled." -ForegroundColor Red
    exit 1
}

Write-Host "`n[3/4] Locating OEM diagnostic APK on tablet..." -ForegroundColor Yellow
$oemPackage = "com.cnlaunch.x431padv"
$apkPath = adb shell pm path $oemPackage 2>&1

if ($LASTEXITCODE -ne 0 -or -not $apkPath) {
    Write-Host "ERROR: OEM diagnostic package ($oemPackage) not found on tablet." -ForegroundColor Red
    exit 1
}

$apkBase = $apkPath -replace "package:", "" -replace "/base.apk", ""
Write-Host "  APK base path: $apkBase" -ForegroundColor Green

$apkFiles = adb shell ls -la "$apkBase" | Where-Object { $_ -match "\.apk$" } | ForEach-Object { ($_ -split '\s+')[-1] }

if (-not $apkFiles) {
    Write-Host "ERROR: No APK files found in $apkBase" -ForegroundColor Red
    exit 1
}

Write-Host "`n[4/4] Pulling APK files..." -ForegroundColor Yellow
$hashFile = Join-Path $apksDir ".sha256"
$hashes = @{}

if (Test-Path $hashFile) {
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
        $localHash = (Get-FileHash $localPath -Algorithm SHA256).Hash.ToLower()
        if ($hashes.ContainsKey($apkFile) -and $hashes[$apkFile] -eq $localHash) {
            Write-Host "  $apkFile already present (hash ok)." -ForegroundColor Green
            continue
        }
    }

    Write-Host "  Pulling $apkFile..." -ForegroundColor Cyan
    adb pull "$apkBase/$apkFile" "$localPath" 2>&1 | Out-Null

    if ($LASTEXITCODE -eq 0) {
        $hash = (Get-FileHash $localPath -Algorithm SHA256).Hash.ToLower()
        $hashes[$apkFile] = $hash
        Write-Host "    Pulled $apkFile" -ForegroundColor Green
    } else {
        Write-Host "    Failed to pull $apkFile" -ForegroundColor Red
        $allSuccess = $false
    }
}

$hashes.GetEnumerator() | ForEach-Object { "$($_.Value)  $($_.Key)" } | Set-Content $hashFile

if ($allSuccess) {
    Write-Host "`nExtraction complete. APKs in: $apksDir" -ForegroundColor Green
} else {
    Write-Host "`nSome APK files failed to extract." -ForegroundColor Red
    exit 1
}
