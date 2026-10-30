param(
    [ValidateSet(5173, 5174)]
    [int]$Port = 5173
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$frontendRoot = Join-Path $projectRoot 'frontend'
$viteEntry = Join-Path $frontendRoot 'node_modules/vite/bin/vite.js'
$nodeExecutable = (Get-Command node.exe -ErrorAction Stop).Source

if (-not (Test-Path -LiteralPath $viteEntry -PathType Leaf)) {
    throw 'Project Vite dependencies are missing. This script does not install dependencies.'
}
if (Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue) {
    throw "Port $Port is occupied. Verify the current process before stopping it."
}

# Validate a real backend response without writing its CSRF token to output.
try {
    $backendResponse = Invoke-WebRequest -Uri 'http://127.0.0.1:8080/api/auth/csrf' -UseBasicParsing -TimeoutSec 5
    $backendBody = $backendResponse.Content | ConvertFrom-Json
    if ($backendResponse.StatusCode -ne 200 -or $backendBody.code -ne 'OK' -or
        [string]::IsNullOrWhiteSpace($backendBody.requestId) -or
        [string]::IsNullOrWhiteSpace($backendBody.data.token)) {
        throw 'Invalid backend CSRF response.'
    }
} catch {
    throw 'The dev backend on 127.0.0.1:8080 is unavailable. Start it with local-dev.yml first.'
}

$logRoot = Join-Path $projectRoot 'logs'
New-Item -ItemType Directory -Path $logRoot -Force | Out-Null
$logPrefix = Join-Path $logRoot ("frontend-dev-$Port-" + (Get-Date -Format 'yyyyMMdd-HHmmss-fff'))
$previousBackendPort = $env:CAMPUS_BACKEND_PORT
try {
    $env:CAMPUS_BACKEND_PORT = '8080'
    $frontendProcess = Start-Process -FilePath $nodeExecutable -ArgumentList @(
        ('"' + $viteEntry + '"'), '--host', '127.0.0.1', '--port', $Port, '--strictPort'
    ) -WorkingDirectory $frontendRoot -WindowStyle Hidden -PassThru `
        -RedirectStandardOutput ($logPrefix + '.out.log') -RedirectStandardError ($logPrefix + '.err.log')
} finally {
    $env:CAMPUS_BACKEND_PORT = $previousBackendPort
}

$ready = $false
for ($attempt = 0; $attempt -lt 40; $attempt++) {
    $frontendProcess.Refresh()
    if ($frontendProcess.HasExited) { break }
    $listener = Get-NetTCPConnection -LocalAddress '127.0.0.1' -LocalPort $Port -State Listen -ErrorAction SilentlyContinue
    if ($listener -and $listener.OwningProcess -eq $frontendProcess.Id) {
        $ready = $true
        break
    }
    Start-Sleep -Milliseconds 250
}
if (-not $ready) {
    throw "Vite did not become ready. Inspect $logPrefix.err.log and the current process."
}

[pscustomobject]@{
    FrontendUrl = "http://127.0.0.1:$Port"
    BackendUrl = 'http://127.0.0.1:8080'
    ProcessId = $frontendProcess.Id
    StandardOutput = $logPrefix + '.out.log'
    StandardError = $logPrefix + '.err.log'
}
