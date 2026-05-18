# Listens on the office PC for tablet cnlaunch zip uploads (push mode).
# Usage: powershell -ExecutionPolicy Bypass -File scripts\lan-export-receiver.ps1
# If "Access is denied", run once as Administrator:
#   netsh http add urlacl url=http://192.168.1.129:8766/ user=Everyone listen=yes
param(
    [string]$BindHost = "192.168.1.129",
    [int]$Port = 8766
)

$ErrorActionPreference = "Stop"
$outDir = Join-Path $PSScriptRoot "cnlaunch-inbox"
New-Item -ItemType Directory -Force -Path $outDir | Out-Null

$prefix = "http://${BindHost}:${Port}/"
$listener = New-Object System.Net.HttpListener

function Start-Listener {
    param([string]$UrlPrefix)
    $script:listener = New-Object System.Net.HttpListener
    $listener.Prefixes.Add($UrlPrefix)
    $listener.Start()
    $script:activePrefix = $UrlPrefix
}

$started = $false
foreach ($tryPrefix in @($prefix, "http://+:${Port}/", "http://127.0.0.1:${Port}/")) {
    try {
        Start-Listener -UrlPrefix $tryPrefix
        $started = $true
        break
    } catch {
        Write-Host "Could not bind $tryPrefix : $($_.Exception.Message)"
    }
}

if (-not $started) {
    Write-Host ""
    Write-Host "FAILED to start HTTP listener. Run PowerShell as Administrator, then:"
    Write-Host "  netsh http add urlacl url=$prefix user=Everyone listen=yes"
    Write-Host "  netsh advfirewall firewall add rule name=`"Together LAN export`" dir=in action=allow protocol=TCP localport=$Port"
    Write-Host "Then re-run this script."
    exit 1
}

Write-Host "Together LAN receiver listening on $activePrefix"
Write-Host "Saving uploads to: $outDir"
Write-Host "Tablet should push to: http://${BindHost}:${Port}/upload"
Write-Host "Press Ctrl+C to stop."

try {
    while ($listener.IsListening) {
        $ctx = $listener.GetContext()
        $req = $ctx.Request
        $resp = $ctx.Response
        try {
            if ($req.HttpMethod -eq "GET" -and $req.Url.AbsolutePath -eq "/health") {
                $bytes = [System.Text.Encoding]::UTF8.GetBytes("ok")
                $resp.StatusCode = 200
                $resp.ContentType = "text/plain"
                $resp.OutputStream.Write($bytes, 0, $bytes.Length)
            }
            elseif ($req.HttpMethod -eq "POST" -and $req.Url.AbsolutePath -eq "/upload") {
                $name = "cnlaunch-bundle-{0}.zip" -f (Get-Date -Format "yyyyMMdd-HHmmss")
                $path = Join-Path $outDir $name
                $fs = [System.IO.File]::Create($path)
                try {
                    $req.InputStream.CopyTo($fs)
                } finally {
                    $fs.Close()
                }
                $msg = "saved $name ($((Get-Item $path).Length) bytes)"
                Write-Host $msg
                $bytes = [System.Text.Encoding]::UTF8.GetBytes($msg)
                $resp.StatusCode = 200
                $resp.ContentType = "text/plain"
                $resp.OutputStream.Write($bytes, 0, $bytes.Length)
            }
            else {
                $resp.StatusCode = 404
            }
        } catch {
            Write-Host "Error: $_"
            $resp.StatusCode = 500
        } finally {
            $resp.Close()
        }
    }
} finally {
    $listener.Stop()
}
