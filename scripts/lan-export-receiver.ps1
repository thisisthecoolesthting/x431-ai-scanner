# TCW Receiver — listens on port 8765 for tablet vehicle database uploads.
# Usage: powershell -ExecutionPolicy Bypass -File scripts\lan-export-receiver.ps1
#
# If you see "Access is denied" on startup, run once as Administrator:
#   netsh http add urlacl url=http://+:8765/ user=Everyone
#
# Set TCW_SAVE_PATH env var to override the default save location.

param(
    [int]$Port = 8765
)

$ErrorActionPreference = "Stop"

# ---- Save path ---------------------------------------------------------------
$savePath = if ($env:TCW_SAVE_PATH) { $env:TCW_SAVE_PATH } else { Join-Path $HOME "TCWBundles" }
if (-not (Test-Path $savePath)) {
    New-Item -ItemType Directory -Force -Path $savePath | Out-Null
}

# ---- Logging helper ----------------------------------------------------------
$logFile = Join-Path $env:TEMP "tcw-receiver.log"

function Write-Log {
    param([string]$Message)
    $line = "[$(Get-Date -Format 'yyyy-MM-ddTHH:mm:ss')] $Message"
    Write-Host $line
    try { Add-Content -Path $logFile -Value $line -Encoding UTF8 } catch {}
}

# ---- Bind HTTP listener ------------------------------------------------------
$listener = New-Object System.Net.HttpListener

try {
    $listener.Prefixes.Add("http://+:$Port/")
    $listener.Start()
} catch [System.Net.HttpListenerException] {
    Write-Host ""
    Write-Host "ERROR: Run once as admin:"
    Write-Host "  netsh http add urlacl url=http://+:$Port/ user=Everyone"
    Write-Host ""
    Write-Host "Then re-run this script (no admin needed after the urlacl is set)."
    exit 1
}

Write-Log "TCW Receiver LISTENING on 0.0.0.0:$Port -> $savePath"
Write-Log "Tablet should push to: http://<this-pc-ip>:$Port/upload"
Write-Log "Press Ctrl+C to stop."

# ---- Graceful shutdown on Ctrl+C --------------------------------------------
$null = [Console]::CancelKeyPress.add({
    $listener.Stop()
    Write-Log "Stopped by user."
    [Console]::CancelKeyPress.remove({})
})

# ---- SHA-256 helper ----------------------------------------------------------
function Compute-Sha256File {
    param([string]$Path)
    $sha = [System.Security.Cryptography.SHA256]::Create()
    $stream = [System.IO.File]::OpenRead($Path)
    try {
        $hash = $sha.ComputeHash($stream)
        return [BitConverter]::ToString($hash).Replace("-", "").ToLower()
    } finally {
        $stream.Close()
        $sha.Dispose()
    }
}

# ---- JSON response helpers ---------------------------------------------------
function Send-Json {
    param($Response, [int]$Code, [string]$Body)
    $bytes = [System.Text.Encoding]::UTF8.GetBytes($Body)
    $Response.StatusCode = $Code
    $Response.ContentType = "application/json; charset=utf-8"
    $Response.ContentLength64 = $bytes.Length
    $Response.OutputStream.Write($bytes, 0, $bytes.Length)
}

function Drive-FreeBytesForPath {
    param([string]$Path)
    try {
        $root = [System.IO.Path]::GetPathRoot($Path)
        $drive = Get-PSDrive -Name $root.TrimEnd('\').TrimEnd(':') -ErrorAction Stop
        return $drive.Free
    } catch {
        return 0
    }
}

# ---- Request loop ------------------------------------------------------------
try {
    while ($listener.IsListening) {
        $ctx  = $listener.GetContext()
        $req  = $ctx.Request
        $resp = $ctx.Response

        $method = $req.HttpMethod
        $path   = $req.Url.AbsolutePath
        $query  = $req.QueryString

        Write-Log "$method $($req.Url.PathAndQuery)"

        try {
            # GET /health
            if ($method -eq "GET" -and $path -eq "/health") {
                $freeBytes = Drive-FreeBytesForPath $savePath
                $body = "{`"ok`":true,`"name`":`"TCW Receiver 1.0`",`"savePath`":`"$($savePath.Replace('\','\\'))`",`"freeBytes`":$freeBytes,`"version`":`"1.0`"}"
                Send-Json $resp 200 $body
            }

            # GET /process/status — PC-assisted processing stub (Wave2 DX7)
            elseif ($method -eq "GET" -and $path -eq "/process/status") {
                $body = '{"ok":true,"state":"idle","capabilities":["upload","health","process_stub"],"activeJobId":null,"progress":0,"message":"Receiver ready; full PC processing worker not wired yet."}'
                Send-Json $resp 200 $body
            }

            # POST /process/start — accept job; no worker yet
            elseif ($method -eq "POST" -and $path -eq "/process/start") {
                $jobId = "stub-" + [guid]::NewGuid().ToString("N").Substring(0, 12)
                $body = "{`"ok`":true,`"jobId`":`"$jobId`",`"state`":`"queued`",`"message`":`"Accepted (stub — no worker running yet)`"}"
                Send-Json $resp 202 $body
            }

            # HEAD /upload?name=<fn>  — resume probe
            elseif ($method -eq "HEAD" -and $path -eq "/upload") {
                $name = $query["name"]
                if (-not $name) { Send-Json $resp 400 '{"ok":false,"reason":"missing_name"}' }
                else {
                    $part  = Join-Path $savePath "$name.part"
                    $final = Join-Path $savePath $name
                    $have  = 0
                    if (Test-Path $final) { $have = (Get-Item $final).Length }
                    elseif (Test-Path $part) { $have = (Get-Item $part).Length }
                    $resp.StatusCode = 200
                    $resp.AddHeader("X-TCW-Have", $have)
                    $body = "{`"have`":$have}"
                    $bytes = [System.Text.Encoding]::UTF8.GetBytes($body)
                    $resp.ContentLength64 = $bytes.Length
                    $resp.OutputStream.Write($bytes, 0, $bytes.Length)
                }
            }

            # POST /upload?name=<fn>&size=<n>&sha256=<hex>  — full raw upload
            elseif ($method -eq "POST" -and $path -eq "/upload") {
                $name   = $query["name"]
                $size   = [long]($query["size"])
                $expect = $query["sha256"]
                if (-not $name) { Send-Json $resp 400 '{"ok":false,"reason":"missing_name"}'; continue }
                if (-not $req.Headers["Content-Length"]) {
                    Send-Json $resp 411 '{"ok":false,"reason":"content_length_required"}'
                }
                else {
                    $partPath  = Join-Path $savePath "$name.part"
                    $finalPath = Join-Path $savePath $name

                    # Stream body to .part file
                    $sha    = [System.Security.Cryptography.SHA256]::Create()
                    $outFs  = [System.IO.File]::Create($partPath)
                    $hashStream = New-Object System.Security.Cryptography.CryptoStream($outFs, $sha, [System.Security.Cryptography.CryptoStreamMode]::Write)
                    try {
                        $req.InputStream.CopyTo($hashStream)
                        $hashStream.FlushFinalBlock()
                    } finally {
                        $hashStream.Close()
                        $outFs.Close()
                    }

                    $actual = [BitConverter]::ToString($sha.Hash).Replace("-", "").ToLower()
                    $sha.Dispose()

                    if ($expect -and $actual -ne $expect.ToLower()) {
                        Remove-Item $partPath -Force -ErrorAction SilentlyContinue
                        Send-Json $resp 422 "{`"ok`":false,`"reason`":`"sha256_mismatch`",`"expected`":`"$expect`",`"actual`":`"$actual`"}"
                        Write-Log "sha256 MISMATCH expected=$expect actual=$actual"
                    } else {
                        Move-Item $partPath $finalPath -Force
                        $bytes = (Get-Item $finalPath).Length
                        $body = "{`"ok`":true,`"path`":`"$($finalPath.Replace('\','\\'))`",`"bytes`":$bytes,`"sha256`":`"$actual`"}"
                        Send-Json $resp 200 $body
                        Write-Log "Saved $name ($bytes bytes) sha256=$actual"
                    }
                }
            }

            # POST /upload-multipart  — legacy fallback for older builds
            elseif ($method -eq "POST" -and $path -eq "/upload-multipart") {
                $name = "tcw-bundle-$(Get-Date -Format 'yyyyMMdd-HHmmss').zip"
                $destPath = Join-Path $savePath $name
                $fs = [System.IO.File]::Create($destPath)
                try { $req.InputStream.CopyTo($fs) } finally { $fs.Close() }
                $bytes = (Get-Item $destPath).Length
                $body = "{`"ok`":true,`"path`":`"$($destPath.Replace('\','\\'))`",`"bytes`":$bytes,`"sha256`":`"multipart`"}"
                Send-Json $resp 200 $body
                Write-Log "Multipart saved $name ($bytes bytes)"
            }

            # PATCH /upload?name=<fn>&offset=<n>  — resume chunk
            elseif ($method -eq "PATCH" -and $path -eq "/upload") {
                $name   = $query["name"]
                $offset = [long]($query["offset"])
                if (-not $name) { Send-Json $resp 400 '{"ok":false,"reason":"missing_name"}' }
                else {
                    $partPath = Join-Path $savePath "$name.part"
                    $have = 0
                    if (Test-Path $partPath) { $have = (Get-Item $partPath).Length }
                    if ($have -lt $offset) {
                        Send-Json $resp 416 "{`"ok`":false,`"reason`":`"offset_mismatch`",`"have`":$have,`"requested`":$offset}"
                    } else {
                        $fs = [System.IO.File]::Open($partPath, [System.IO.FileMode]::OpenOrCreate, [System.IO.FileAccess]::Write)
                        try {
                            $fs.Seek($offset, [System.IO.SeekOrigin]::Begin) | Out-Null
                            $req.InputStream.CopyTo($fs)
                            $newTotal = $fs.Position
                        } finally { $fs.Close() }
                        $body = "{`"ok`":true,`"bytes`":$newTotal}"
                        Send-Json $resp 200 $body
                        Write-Log "PATCH $name offset=$offset newTotal=$newTotal"
                    }
                }
            }

            # 404 catch-all
            else {
                Send-Json $resp 404 '{"ok":false,"reason":"not_found"}'
            }
        } catch {
            Write-Log "ERROR handling request: $_"
            try { Send-Json $resp 500 "{`"ok`":false,`"reason`":`"$($_.ToString().Replace('"','`"'))`"}" } catch {}
        } finally {
            try { $resp.Close() } catch {}
        }
    }
} finally {
    try { $listener.Stop() } catch {}
    Write-Log "TCW Receiver stopped."
}
