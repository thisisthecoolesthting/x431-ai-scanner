# install-tcw-receiver.ps1 — One-time setup: Scheduled Task + Firewall rule for TCW Receiver.
# Run as the user who will receive files (no admin needed for Task Scheduler if running for self;
# admin IS needed for New-NetFirewallRule — script will warn if not elevated).
#
# Usage:
#   powershell -ExecutionPolicy Bypass -File scripts\install-tcw-receiver.ps1
#
# After install, the receiver starts at logon and runs hidden.
# Test it: http://<your-ip>:8765/health

$ErrorActionPreference = "Stop"
$ScriptDir = $PSScriptRoot
$ReceiverScript = Join-Path $ScriptDir "lan-export-receiver.ps1"
$TaskName = "TCWReceiver"
$Port = 8765

# ---- Verify receiver script exists ------------------------------------------
if (-not (Test-Path $ReceiverScript)) {
    Write-Host "ERROR: lan-export-receiver.ps1 not found at $ReceiverScript"
    Write-Host "Make sure you run this from the scripts\ folder or from the repo root."
    exit 1
}

Write-Host "=== Together Car Works — Receiver Setup ==="
Write-Host "Receiver script : $ReceiverScript"
Write-Host "Scheduled Task  : $TaskName"
Write-Host "Port            : $Port"
Write-Host ""

# ---- Firewall rule (requires elevation) -------------------------------------
$isAdmin = ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole(
    [Security.Principal.WindowsBuiltInRole]::Administrator)

if ($isAdmin) {
    $existingRule = Get-NetFirewallRule -DisplayName "TCW Receiver" -ErrorAction SilentlyContinue
    if ($existingRule) {
        Write-Host "Firewall rule 'TCW Receiver' already exists — skipping."
    } else {
        Write-Host "Adding inbound firewall rule for port $Port..."
        New-NetFirewallRule `
            -DisplayName "TCW Receiver" `
            -Direction Inbound `
            -LocalPort $Port `
            -Protocol TCP `
            -Action Allow `
            -Profile Any `
            -Description "Allow Together Car Works tablet to push vehicle database zips" `
            | Out-Null
        Write-Host "Firewall rule added."
    }
} else {
    Write-Host "WARNING: Not running as Administrator — skipping firewall rule."
    Write-Host "To add the rule manually (run PowerShell as Admin):"
    Write-Host "  New-NetFirewallRule -DisplayName 'TCW Receiver' -Direction Inbound -LocalPort $Port -Protocol TCP -Action Allow"
    Write-Host ""
}

# ---- URL ACL (required for HttpListener to bind on any interface) -----------
Write-Host "Ensuring URL ACL for http://+:$Port/..."
$aclOutput = netsh http show urlacl url="http://+:$Port/" 2>&1
if ($aclOutput -notmatch "Reserved URL") {
    if ($isAdmin) {
        netsh http add urlacl url="http://+:$Port/" user=Everyone | Out-Null
        Write-Host "URL ACL added."
    } else {
        Write-Host "WARNING: URL ACL not set. Run once as Admin:"
        Write-Host "  netsh http add urlacl url=http://+:$Port/ user=Everyone"
    }
} else {
    Write-Host "URL ACL already set."
}

# ---- Scheduled Task ---------------------------------------------------------
$psExe = "powershell.exe"
$taskArgs = "-ExecutionPolicy Bypass -WindowStyle Hidden -File `"$ReceiverScript`""

# Remove existing task if present (idempotent)
$existing = Get-ScheduledTask -TaskName $TaskName -ErrorAction SilentlyContinue
if ($existing) {
    Write-Host "Removing existing task $TaskName..."
    Unregister-ScheduledTask -TaskName $TaskName -Confirm:$false
}

Write-Host "Creating Scheduled Task '$TaskName'..."
$action  = New-ScheduledTaskAction -Execute $psExe -Argument $taskArgs
$trigger = New-ScheduledTaskTrigger -AtLogon
$settings = New-ScheduledTaskSettingsSet `
    -ExecutionTimeLimit (New-TimeSpan -Hours 0) `
    -RestartCount 3 `
    -RestartInterval (New-TimeSpan -Minutes 1) `
    -MultipleInstances IgnoreNew

Register-ScheduledTask `
    -TaskName $TaskName `
    -Action $action `
    -Trigger $trigger `
    -Settings $settings `
    -Description "Together Car Works vehicle database receiver — listens on port $Port" `
    -RunLevel Limited `
    | Out-Null

Write-Host "Task registered."

# ---- Start the task now -----------------------------------------------------
Write-Host "Starting $TaskName now..."
Start-ScheduledTask -TaskName $TaskName
Start-Sleep -Seconds 2

# ---- Show reachable IPs ------------------------------------------------------
Write-Host ""
Write-Host "Installed. Receiver is running. Reachable at:"
$ips = Get-NetIPAddress -AddressFamily IPv4 |
    Where-Object { $_.InterfaceAlias -notmatch "Loopback" -and $_.IPAddress -ne "127.0.0.1" }
foreach ($ip in $ips) {
    Write-Host "  http://$($ip.IPAddress):$Port/health"
}
Write-Host ""
Write-Host "On the tablet, open Together Car Works -> Data Transfer and enter the IP above."
Write-Host "The receiver starts automatically at every logon. To stop: Stop-ScheduledTask -TaskName $TaskName"
