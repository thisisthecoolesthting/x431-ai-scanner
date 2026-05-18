# Listens on the office PC for tablet cnlaunch zip uploads (task 203 push mode).
# Usage (elevated if Windows asks): .\scripts\lan-export-receiver.ps1
param(
    [string]$BindHost = "192.168.1.129",
    [int]$Port = 8766
)

$ErrorActionPreference = "Stop"
$outDir = Join-Path $PSScriptRoot "cnlaunch-inbox"
New-Item -ItemType Directory -Force -Path $outDir | Out-Null

$prefix = "http://${BindHost}:${Port}/"
$listener = New-Object System.Net.HttpListener
$listener.Prefixes.Add($prefix)
$listener.Start()
Write-Host "Together LAN receiver listening on $prefix"
Write-Host "Saving uploads to: $outDir"
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
