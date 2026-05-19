# install-tcw-receiver.ps1 - one-time setup for the Together Car Works PC receiver.
#
# Run from an elevated PowerShell:
#   powershell -ExecutionPolicy Bypass -File "C:\Users\reasn\Documents\Claude\Projects\DEv1\_x431-work\scripts\install-tcw-receiver.ps1"
#
# The receiver listens on port 8765 and saves uploads to %USERPROFILE%\TCWBundles
# unless TCW_SAVE_PATH is set.

$ErrorActionPreference = "Stop"

$ScriptDir = $PSScriptRoot
$ReceiverScript = Join-Path $ScriptDir "lan-export-receiver.ps1"
$TaskName = "TCWReceiver"
$Port = 8765

if (-not (Test-Path $ReceiverScript)) {
    Write-Host "ERROR: lan-export-receiver.ps1 not found at $ReceiverScript"
    exit 1
}

$identity = [Security.Principal.WindowsIdentity]::GetCurrent()
$principal = New-Object Security.Principal.WindowsPrincipal($identity)
$isAdmin = $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)

Write-Host "=== Together Car Works Receiver Setup ==="
Write-Host "Receiver script : $ReceiverScript"
Write-Host "Scheduled Task  : $TaskName"
Write-Host "Port            : $Port"
Write-Host ""

if (-not $isAdmin) {
    Write-Host "WARNING: This PowerShell is not running as Administrator."
    Write-Host "The script will install the scheduled task, but firewall and URL ACL setup may be skipped."
    Write-Host ""
}

if ($isAdmin) {
    $existingRule = Get-NetFirewallRule -DisplayName "TCW Receiver" -ErrorAction SilentlyContinue
    if ($existingRule) {
        Write-Host "Firewall rule 'TCW Receiver' already exists."
    }
    if (-not $existingRule) {
        Write-Host "Adding inbound firewall rule for TCP $Port..."
        $firewallRuleArgs = @{
            DisplayName = "TCW Receiver"
            Direction = "Inbound"
            LocalPort = $Port
            Protocol = "TCP"
            Action = "Allow"
            Profile = "Any"
            Description = "Allow Together Car Works tablet uploads"
        }
        New-NetFirewallRule @firewallRuleArgs | Out-Null
        Write-Host "Firewall rule added."
    }
}

if (-not $isAdmin) {
    Write-Host "To add the firewall rule manually, run PowerShell as Admin:"
    Write-Host "  New-NetFirewallRule -DisplayName 'TCW Receiver' -Direction Inbound -LocalPort $Port -Protocol TCP -Action Allow"
    Write-Host ""
}

Write-Host "Checking URL ACL for http://+:$Port/..."
$aclOutput = netsh http show urlacl url="http://+:$Port/" 2>&1
$hasUrlAcl = $aclOutput -match "Reserved URL"

if ($hasUrlAcl) {
    Write-Host "URL ACL already set."
}

if (-not $hasUrlAcl) {
    if ($isAdmin) {
        netsh http add urlacl url="http://+:$Port/" user=Everyone | Out-Null
        Write-Host "URL ACL added."
    }
    if (-not $isAdmin) {
        Write-Host "URL ACL not set. Run once as Admin:"
        Write-Host "  netsh http add urlacl url=http://+:$Port/ user=Everyone"
    }
}

$existingTask = Get-ScheduledTask -TaskName $TaskName -ErrorAction SilentlyContinue
if ($existingTask) {
    Write-Host "Removing existing scheduled task $TaskName..."
    Unregister-ScheduledTask -TaskName $TaskName -Confirm:$false
}

$psExe = "powershell.exe"
$taskArgument = '-ExecutionPolicy Bypass -WindowStyle Hidden -File "' + $ReceiverScript + '"'

Write-Host "Creating scheduled task $TaskName..."
$action = New-ScheduledTaskAction -Execute $psExe -Argument $taskArgument
$trigger = New-ScheduledTaskTrigger -AtLogon
$settingsArgs = @{
    ExecutionTimeLimit = New-TimeSpan -Hours 0
    RestartCount = 3
    RestartInterval = New-TimeSpan -Minutes 1
    MultipleInstances = "IgnoreNew"
}
$settings = New-ScheduledTaskSettingsSet @settingsArgs
$registerArgs = @{
    TaskName = $TaskName
    Action = $action
    Trigger = $trigger
    Settings = $settings
    Description = "Together Car Works vehicle database receiver on port $Port"
    RunLevel = "Limited"
}
Register-ScheduledTask @registerArgs | Out-Null

Write-Host "Starting $TaskName..."
Start-ScheduledTask -TaskName $TaskName
Start-Sleep -Seconds 2

Write-Host ""
Write-Host "Installed. Receiver should be reachable at:"
$ips = Get-NetIPAddress -AddressFamily IPv4 |
    Where-Object { $_.InterfaceAlias -notmatch "Loopback" -and $_.IPAddress -ne "127.0.0.1" }

foreach ($ip in $ips) {
    Write-Host "  http://$($ip.IPAddress):$Port/health"
}

Write-Host ""
Write-Host "On the tablet: Together Car Works -> Settings/Data Transfer -> PC host = the IP above, port = $Port."
Write-Host "Uploads save to: $HOME\TCWBundles"
Write-Host "To stop later: Stop-ScheduledTask -TaskName $TaskName"
