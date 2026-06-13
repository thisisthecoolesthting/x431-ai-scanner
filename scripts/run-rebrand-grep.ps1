# Fails with exit 1 if forbidden vendor branding appears outside compat seams.
$pattern = '\b(Launch|X431|x431|cnlaunch|CaseForge|caseforge)\b'
$paths = @('app/src', '.github/workflows', 'scripts', 'README.md', 'docs/STANDALONE-vs-FACTORY-OVERLAY.md')

$rawHits = & rg --no-heading --line-number $pattern $paths 2>$null
$hits = @(
    $rawHits |
        Where-Object { $_ -and $_.Trim() -ne '' } |
        Where-Object { $_ -notmatch 'com[./\\]caseforge' } |
        Where-Object { $_ -notmatch 'agent[/\\]Updater\.kt' } |
        Where-Object { $_ -notmatch 'OEM_DATA_PATH' } |
        Where-Object { $_ -notmatch 'OEM_DATA_CANDIDATES' } |
        Where-Object { $_ -notmatch 'com\.cnlaunch\.' } |
        Where-Object { $_ -notmatch 'com\.x431\.' } |
        Where-Object { $_ -notmatch 'OemTabletCompat' } |
        Where-Object { $_ -notmatch 'oem_compat_strings' } |
        Where-Object { $_ -notmatch 'run-rebrand-grep\.ps1' } |
        Where-Object { $_ -notmatch 'lan-export-receiver\.ps1' } |
        Where-Object { $_ -notmatch 'x431-ai-scanner' } |
        Where-Object { $_ -notmatch 'extract-x431-apk\.ps1' } |
        Where-Object { $_ -notmatch 'analyze-share-export\.ps1' } |
        Where-Object { $_ -notmatch 'build_cnlaunch_assets\.py' } |
        Where-Object { $_ -notmatch 'frida-vci-intercept\.js' } |
        Where-Object { $_ -notmatch 'rememberLauncherForActivityResult' } |
        Where-Object { $_ -notmatch 'Launcher\.launch' } |
        Where-Object { $_ -notmatch 'permissionLauncher' } |
        Where-Object { $_ -notmatch 'speechLauncher' } |
        Where-Object { $_ -notmatch 'vinScanLauncher' } |
        Where-Object { $_ -notmatch 'btPermissionLauncher' } |
        Where-Object { $_ -notmatch 'mediaProjectionLauncher' } |
        Where-Object { $_ -notmatch 'recordAudioLauncher' } |
        Where-Object { $_ -notmatch 'btPermLauncher' } |
        Where-Object { $_ -notmatch 'btLauncher' } |
        Where-Object { $_ -notmatch 'scanLauncher' } |
        Where-Object { $_ -notmatch 'LaunchedEffect' } |
        Where-Object { $_ -notmatch 'launchMode' } |
        Where-Object { $_ -notmatch 'ic_launcher' } |
        Where-Object { $_ -notmatch 'kotlinx\.coroutines\.launch' } |
        Where-Object { $_ -notmatch 'getLaunchIntentForPackage' } |
        Where-Object { $_ -notmatch 'launchViewInstallIntent' } |
        Where-Object { $_ -notmatch 'launchCamera' } |
        Where-Object { $_ -notmatch 'launchSequence' } |
        Where-Object { $_ -notmatch 'auto-launch' } |
        Where-Object { $_ -notmatch 'first launch' } |
        Where-Object { $_ -notmatch 'first-launch' } |
        Where-Object { $_ -notmatch 'onboarding' } |
        Where-Object { $_ -notmatch 'DEV1-TASK' }
)

if ($hits.Count -gt 0) {
    Write-Host 'REBRAND FAIL - forbidden words remain:'
    $hits | ForEach-Object { Write-Host "  $_" }
    exit 1
}

Write-Host 'Rebrand audit clean.'
