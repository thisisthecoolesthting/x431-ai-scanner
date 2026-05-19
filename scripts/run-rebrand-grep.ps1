# Fails with exit 1 if any forbidden word remains outside the allowed package id and OEM_DATA_PATH constant.
$pattern = '\b(Launch|X431|x431|cnlaunch|CaseForge|caseforge)\b'
$paths = @('app/src', '.github/workflows', 'scripts', 'README.md', 'docs')
$hits = & rg --no-heading --line-number $pattern @paths 2>$null |
    Where-Object { $_ -notmatch 'com[./\\]caseforge' } |
    Where-Object { $_ -notmatch '[/\\]agent[/\\]Updater\.kt' } |
    Where-Object { $_ -notmatch 'OEM_DATA_PATH' } |
    Where-Object { $_ -notmatch 'OEM_DATA_CANDIDATES' } |
    Where-Object { $_ -notmatch 'com\.cnlaunch\.' } |
    Where-Object { $_ -notmatch 'com\.x431\.' } |
    Where-Object { $_ -notmatch '[/\\]transfer[/\\]' } |
    Where-Object { $_ -notmatch 'run-rebrand-grep\.ps1' } |
    Where-Object { $_ -notmatch 'lan-export-receiver\.ps1' } |
    Where-Object { $_ -notmatch 'x431-ai-scanner' } |
    Where-Object { $_ -notmatch 'extract-x431-apk\.ps1' } |
    Where-Object { $_ -notmatch 'build_cnlaunch_assets\.py' } |
    Where-Object { $_ -notmatch 'frida-vci-intercept\.js' } |
    Where-Object { $_.Trim() -ne '' }
if ($hits -and @($hits).Count -gt 0) {
    Write-Host "REBRAND FAIL — forbidden words remain:"
    $hits | ForEach-Object { Write-Host "  $_" }
    exit 1
}
Write-Host "Rebrand audit clean."
