<#
.SYNOPSIS
  Smoke test: ensure analyze-share-export.ps1 parses (no zip required).
#>
[CmdletBinding()]
param()

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent (Split-Path -Parent $PSCommandPath)
$target = Join-Path $repoRoot "scripts\analyze-share-export.ps1"

if (-not (Test-Path $target)) {
    Write-Error "Missing script: $target"
    exit 1
}

$parseErrors = $null
$ast = [System.Management.Automation.Language.Parser]::ParseFile(
    $target,
    [ref]$null,
    [ref]$parseErrors
)

if ($parseErrors -and $parseErrors.Count -gt 0) {
    Write-Host "Parse errors in analyze-share-export.ps1:" -ForegroundColor Red
    foreach ($e in $parseErrors) {
        Write-Host "  $($e.Message) @ line $($e.Extent.StartLineNumber)" -ForegroundColor Red
    }
    exit 1
}

$fnAst = $ast.FindAll({
    param($node)
    $node -is [System.Management.Automation.Language.FunctionDefinitionAst]
}, $true)
$fnNames = @($fnAst | ForEach-Object { $_.Name })

$required = @(
    "Get-MagicLabel",
    "Find-OemDataRoots",
    "Get-TextSniffs",
    "Redact-Name"
)
$missing = @()
foreach ($r in $required) {
    if ($fnNames -notcontains $r) { $missing += $r }
}

if ($missing.Count -gt 0) {
    Write-Host "Missing expected functions: $($missing -join ', ')" -ForegroundColor Red
    exit 1
}

Write-Host "OK: analyze-share-export.ps1 parses; $($fnNames.Count) functions seen." -ForegroundColor Green
exit 0
