# CaseForge Scanner AI — one-click Windows build
# Downloads JDK 17, Android SDK, Gradle 8.9 into .build-cache (~2 GB the first time),
# then builds app-debug.apk. Subsequent runs reuse the cache and finish in <1 min.
#
# Usage:
#   Right-click → Run with PowerShell
#   OR from a PowerShell prompt:  .\build.ps1
#
# If PowerShell blocks execution, run once:
#   Set-ExecutionPolicy -Scope CurrentUser RemoteSigned

$ErrorActionPreference = 'Stop'
$ProgressPreference   = 'SilentlyContinue'   # makes Invoke-WebRequest ~10× faster

$root  = Split-Path -Parent $MyInvocation.MyCommand.Path
$cache = Join-Path $root '.build-cache'
New-Item -ItemType Directory -Force -Path $cache | Out-Null

function Get-File($url, $dest) {
    if (Test-Path $dest) { return }
    Write-Host "Downloading $(Split-Path $url -Leaf) ..."
    Invoke-WebRequest -Uri $url -OutFile $dest -UseBasicParsing
}

function Expand-IfMissing($zip, $target, $marker) {
    if (Test-Path (Join-Path $target $marker)) { return }
    Write-Host "Extracting $(Split-Path $zip -Leaf) ..."
    New-Item -ItemType Directory -Force -Path $target | Out-Null
    Expand-Archive -Path $zip -DestinationPath $target -Force
}

# 1. JDK 17 (Temurin) ---------------------------------------------------------
$jdkZip = Join-Path $cache 'jdk17.zip'
$jdkDir = Join-Path $cache 'jdk'
Get-File 'https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.13%2B11/OpenJDK17U-jdk_x64_windows_hotspot_17.0.13_11.zip' $jdkZip
Expand-IfMissing $jdkZip $jdkDir 'jdk-17.0.13+11'
$env:JAVA_HOME = Join-Path $jdkDir 'jdk-17.0.13+11'

# 2. Android command-line tools ----------------------------------------------
$ctZip = Join-Path $cache 'cmdline-tools.zip'
$sdkDir = Join-Path $cache 'android-sdk'
Get-File 'https://dl.google.com/android/repository/commandlinetools-win-11076708_latest.zip' $ctZip
$ctTarget = Join-Path $sdkDir 'cmdline-tools'
if (-not (Test-Path (Join-Path $ctTarget 'latest'))) {
    New-Item -ItemType Directory -Force -Path $ctTarget | Out-Null
    Expand-Archive -Path $ctZip -DestinationPath $ctTarget -Force
    Rename-Item (Join-Path $ctTarget 'cmdline-tools') 'latest'
}
$env:ANDROID_HOME = $sdkDir
$env:Path = "$($env:JAVA_HOME)\bin;$sdkDir\cmdline-tools\latest\bin;$sdkDir\platform-tools;$env:Path"

# 3. Accept licenses + install platform-tools, platform-34, build-tools 34 ---
if (-not (Test-Path (Join-Path $sdkDir 'platforms\android-34'))) {
    Write-Host "Accepting SDK licenses ..."
    'y','y','y','y','y','y','y','y','y','y' | & sdkmanager.bat --licenses | Out-Null
    Write-Host "Installing SDK packages (this is the slow first-time step) ..."
    & sdkmanager.bat 'platform-tools' 'platforms;android-34' 'build-tools;34.0.0'
}

# 4. Gradle 8.9 --------------------------------------------------------------
$gradleZip = Join-Path $cache 'gradle.zip'
$gradleDir = Join-Path $cache 'gradle'
Get-File 'https://services.gradle.org/distributions/gradle-8.9-bin.zip' $gradleZip
Expand-IfMissing $gradleZip $gradleDir 'gradle-8.9'
$env:Path = "$gradleDir\gradle-8.9\bin;$env:Path"

# 5. local.properties (so AGP finds the SDK) ---------------------------------
$sdkPropPath = ($sdkDir -replace '\\','/')
"sdk.dir=$sdkPropPath" | Set-Content -Path (Join-Path $root 'local.properties') -Encoding ASCII

# 6. Build! ------------------------------------------------------------------
Write-Host ""
Write-Host "Building debug APK ..."
Push-Location $root
try {
    & gradle.bat --no-daemon :app:assembleDebug
} finally {
    Pop-Location
}

$apk = Join-Path $root 'app\build\outputs\apk\debug\app-debug.apk'
if (Test-Path $apk) {
    Write-Host ""
    Write-Host "SUCCESS"  -ForegroundColor Green
    Write-Host "APK:    $apk"
    Write-Host "Install to a connected device with:"
    Write-Host "  $sdkDir\platform-tools\adb.exe install -r `"$apk`""
} else {
    Write-Host "Build finished but APK not found at $apk" -ForegroundColor Red
    exit 1
}
