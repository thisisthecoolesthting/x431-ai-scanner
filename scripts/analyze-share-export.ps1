<#
.SYNOPSIS
  Analyze a Together Car Works share-export zip (no ADB).

.DESCRIPTION
  Unzips to decompile/work/, inventories files, probes magic bytes on DB-like files,
  writes markdown + JSON under decompile/findings/.

.PARAMETER ZipPath
  Path to tcw-bundle zip. If omitted, uses newest *.zip in decompile/inbox/.

.EXAMPLE
  powershell -ExecutionPolicy Bypass -File scripts\analyze-share-export.ps1
#>
[CmdletBinding()]
param(
    [string] $ZipPath = ""
)

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent (Split-Path -Parent $PSCommandPath)
$inbox = Join-Path $repoRoot "decompile\inbox"
$workRoot = Join-Path $repoRoot "decompile\work"
$findings = Join-Path $repoRoot "decompile\findings"

if (-not (Test-Path $findings)) { New-Item -ItemType Directory -Path $findings -Force | Out-Null }
if (-not (Test-Path $workRoot)) { New-Item -ItemType Directory -Path $workRoot -Force | Out-Null }

function Get-MagicLabel {
    param([byte[]] $Head)
    if ($Head.Length -ge 16 -and $Head[0] -eq 0x53 -and $Head[1] -eq 0x51 -and $Head[2] -eq 0x4c -and $Head[3] -eq 0x69) { return "sqlite" }
    if ($Head.Length -ge 2 -and $Head[0] -eq 0x1f -and $Head[1] -eq 0x8b) { return "gzip" }
    if ($Head.Length -ge 4 -and $Head[0] -eq 0x50 -and $Head[1] -eq 0x4b) { return "zip" }
    if ($Head.Length -ge 4 -and $Head[0] -eq 0x7f -and $Head[1] -eq 0x45 -and $Head[2] -eq 0x4c -and $Head[3] -eq 0x46) { return "elf" }
    if ($Head.Length -ge 4 -and $Head[0] -eq 0x89 -and $Head[1] -eq 0x50 -and $Head[2] -eq 0x4e -and $Head[3] -eq 0x47) { return "png" }
    $hex = ($Head | Select-Object -First 8 | ForEach-Object { $_.ToString("x2") }) -join " "
    return "unknown ($hex)"
}

function Get-FileKind {
    param([string] $Name)
    $lower = $Name.ToLowerInvariant()
    $ext = [System.IO.Path]::GetExtension($lower).TrimStart(".")
    $dbExt = @("db", "sqlite", "sqlite3", "sdb", "db3")
    $reportExt = @("pdf", "html", "htm", "txt")
    $catalogExt = @("xml", "ini", "cfg", "dat", "bin", "lst", "idx", "res")
    if ($dbExt -contains $ext) { return "database" }
    if ($reportExt -contains $ext) { return "report" }
    if ($catalogExt -contains $ext) { return "catalog" }
    if ($lower -match "report|history|diag|dtc|freeze|snapshot|log") { return "report" }
    if ($lower -match "menu|tree|catalog|vehicle|ecu|module") { return "catalog" }
    return "other"
}

function Redact-Name {
    param([string] $Raw)
    $n = [System.IO.Path]::GetFileName($Raw)
    $n = $n -replace "(?i)cnlaunch|x431|launch\s*pad", "oem"
    $n = $n -replace "(?i)com\.[a-z0-9_.]{4,}", "app"
    if ($n.Length -gt 52) { $n = $n.Substring(0, 48) + "…" }
    return $n
}

if ([string]::IsNullOrWhiteSpace($ZipPath)) {
    $candidates = @()
    if (Test-Path $inbox) {
        $candidates += Get-ChildItem -Path $inbox -Filter "*.zip" -File -ErrorAction SilentlyContinue
    }
    $candidates += Get-ChildItem -Path $env:USERPROFILE\Downloads -Filter "tcw-bundle*.zip" -File -ErrorAction SilentlyContinue
    $pick = $candidates | Sort-Object LastWriteTime -Descending | Select-Object -First 1
    if ($pick) { $ZipPath = $pick.FullName }
}

if ([string]::IsNullOrWhiteSpace($ZipPath) -or -not (Test-Path $ZipPath)) {
    Write-Host "=== TCW share-export analyze ===" -ForegroundColor Cyan
    Write-Host ""
    Write-Host "No zip found." -ForegroundColor Yellow
    Write-Host "  1. Export from tablet (Share mode) and copy zip to:" -ForegroundColor White
    Write-Host "     $inbox" -ForegroundColor Gray
    Write-Host "  2. Re-run this script." -ForegroundColor White
    $stubId = Get-Date -Format "yyyyMMdd-HHmmss"
    $stubMd = Join-Path $findings "$stubId-waiting-for-share-zip.md"
    @"
# Share-zip decompile — waiting for input

**Status:** lane started; **no zip on disk yet.**

## Drop zone

Copy your tablet export here:

``````
decompile/inbox/
``````

Then run:

``````powershell
powershell -ExecutionPolicy Bypass -File scripts\analyze-share-export.ps1
``````

## Tablet reminder

Home → **Share** → **Export & share (free)** → save to PC.
"@ | Set-Content -Path $stubMd -Encoding UTF8
    Write-Host ""
    Write-Host "Wrote: $stubMd" -ForegroundColor Green
    exit 2
}

$zipItem = Get-Item $ZipPath
$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$extractDir = Join-Path $workRoot $stamp
New-Item -ItemType Directory -Path $extractDir -Force | Out-Null

Write-Host "=== TCW share-export analyze ===" -ForegroundColor Cyan
Write-Host "Zip     : $($zipItem.FullName)"
Write-Host "Size    : $([math]::Round($zipItem.Length / 1MB, 2)) MB"
Write-Host "Extract : $extractDir"
Write-Host ""

Expand-Archive -Path $zipItem.FullName -DestinationPath $extractDir -Force

$extCounts = @{}
$kindCounts = @{ database = 0; catalog = 0; report = 0; other = 0 }
$kindBytes = @{ database = 0L; catalog = 0L; report = 0L; other = 0L }
$totalFiles = 0
$totalBytes = 0L
$samples = [System.Collections.Generic.List[string]]::new()
$dbProbes = [System.Collections.Generic.List[object]]::new()
$topLevelDirs = @()

Get-ChildItem -Path $extractDir -Directory -ErrorAction SilentlyContinue | ForEach-Object {
    $topLevelDirs += $_.Name
}

$files = Get-ChildItem -Path $extractDir -Recurse -File -ErrorAction SilentlyContinue
foreach ($f in $files) {
    $totalFiles++
    $totalBytes += $f.Length
    $ext = $f.Extension.TrimStart(".").ToLowerInvariant()
    if ($ext) {
        if ($extCounts.ContainsKey($ext)) { $extCounts[$ext]++ } else { $extCounts[$ext] = 1 }
    }
    $kind = Get-FileKind -Name $f.Name
    $kindCounts[$kind]++
    $kindBytes[$kind] += $f.Length
    if ($samples.Count -lt 16) {
        $s = Redact-Name $f.Name
        if ($samples -notcontains $s) { [void]$samples.Add($s) }
    }
    if ($kind -eq "database" -and $dbProbes.Count -lt 12) {
        $fs = [System.IO.File]::OpenRead($f.FullName)
        try {
            $buf = New-Object byte[] 64
            $read = $fs.Read($buf, 0, 64)
            $head = $buf[0..([Math]::Max(0, $read - 1))]
            $magic = Get-MagicLabel -Head $head
            $dbProbes.Add([ordered]@{
                name = Redact-Name $f.Name
                sizeBytes = $f.Length
                magic = $magic
            })
        } finally { $fs.Dispose() }
    }
}

$report = [ordered]@{
    id = $stamp
    sourceZip = $zipItem.Name
    sourceZipBytes = $zipItem.Length
    extractedTo = $extractDir
    analyzedAt = (Get-Date).ToUniversalTime().ToString("o")
    topLevelDirs = $topLevelDirs
    fileCount = $totalFiles
    totalBytes = $totalBytes
    extensionCounts = $extCounts.GetEnumerator() | Sort-Object -Property Value -Descending | ForEach-Object { @{ ext = $_.Key; count = $_.Value } }
    kindCounts = $kindCounts
    kindBytes = $kindBytes
    sampleNames = $samples
    databaseProbes = $dbProbes
    notes = @(
        "Shallow inventory only; proprietary DB contents are not committed.",
        "Next: pick largest sqlite candidate and document schema in findings/."
    )
}

$jsonPath = Join-Path $findings "$stamp-share-export-report.json"
$mdPath = Join-Path $findings "$stamp-share-export-report.md"

$report | ConvertTo-Json -Depth 6 | Set-Content -Path $jsonPath -Encoding UTF8

$extLines = ($extCounts.GetEnumerator() | Sort-Object Value -Descending | Select-Object -First 15 | ForEach-Object { "- .$($_.Key): $($_.Value)" }) -join "`n"
if (-not $extLines) { $extLines = "- (none)" }
$dbLines = ($dbProbes | ForEach-Object { "- $($_.name) ($([math]::Round($_.sizeBytes/1KB)) KB) magic=$($_.magic)" }) -join "`n"
if (-not $dbLines) { $dbLines = "- (no database-like extensions in this zip)" }
$dirLines = if ($topLevelDirs.Count -gt 0) { ($topLevelDirs | ForEach-Object { "- $_" }) -join "`n" } else { "- (flat zip)" }

@"
# Share-export analysis — $stamp

| Field | Value |
|-------|-------|
| Source zip | ``$($zipItem.Name)`` |
| Size | $([math]::Round($zipItem.Length / 1MB, 2)) MB |
| Files | $totalFiles |
| Total bytes | $([math]::Round($totalBytes / 1MB, 2)) MB |
| Extracted (local) | ``$extractDir`` |

## Top-level folders

$dirLines

## By kind

| Kind | Files | Bytes (MB) |
|------|-------|------------|
| database | $($kindCounts.database) | $([math]::Round($kindBytes.database / 1MB, 2)) |
| catalog | $($kindCounts.catalog) | $([math]::Round($kindBytes.catalog / 1MB, 2)) |
| report | $($kindCounts.report) | $([math]::Round($kindBytes.report / 1MB, 2)) |
| other | $($kindCounts.other) | $([math]::Round($kindBytes.other / 1MB, 2)) |

## Extensions (top)

$extLines

## Database probes (magic bytes)

$dbLines

## Redacted name samples

$((($samples | ForEach-Object { "- $_" }) -join "`n"))

## Next step

1. Identify the primary ``cnlaunch`` (or oem) data root inside the extract tree.
2. Open largest **sqlite** candidate in DB Browser locally — do not commit the DB.
3. Add ``findings/${stamp}-schema-notes.md`` with table names only.
"@ | Set-Content -Path $mdPath -Encoding UTF8

Write-Host "Report  : $mdPath" -ForegroundColor Green
Write-Host "JSON    : $jsonPath" -ForegroundColor Green
Write-Host "Done." -ForegroundColor Green
