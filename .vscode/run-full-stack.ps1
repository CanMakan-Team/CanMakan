$ErrorActionPreference = 'Stop'

$workspace = Split-Path -Parent $PSScriptRoot
$pidFile = Join-Path $PSScriptRoot 'fullstack.pids'
. (Join-Path $PSScriptRoot 'backend-env.ps1')

# Clear stale PID tracking from a previous launch.
if (Test-Path $pidFile) {
    Remove-Item -Force $pidFile
}

function Start-TrackedWindow {
    param(
        [Parameter(Mandatory = $true)]
        [string]$WorkingDirectory,
        [Parameter(Mandatory = $true)]
        [string]$Command,
        [Parameter(Mandatory = $true)]
        [string]$Label,
        [string]$PrefixCommand = ''
    )

    Write-Host "Starting $Label..."
    $launch = if ($PrefixCommand) {
        "$PrefixCommand; Set-Location -LiteralPath '$WorkingDirectory'; $Command"
    } else {
        "Set-Location -LiteralPath '$WorkingDirectory'; $Command"
    }
    $process = Start-Process powershell -PassThru -WindowStyle Normal -ArgumentList @(
        '-NoExit',
        '-NoProfile',
        '-ExecutionPolicy', 'Bypass',
        '-Command',
        $launch
    )
    Add-Content -Path $pidFile -Value "$($process.Id)|$Label"
    return $process.Id
}

$backendDir = Join-Path $workspace 'server/backend'
$webDir = Join-Path $workspace 'client/web'
$mobileDir = Join-Path $workspace 'client/mobile'

Assert-JwtSigningSecretPresent
$backendEnvBootstrap = Get-BackendEnvBootstrapCommand

# Free backend port if a previous instance is still listening (same as Run Backend).
$listenerPid = Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue |
    Select-Object -First 1 -ExpandProperty OwningProcess
if ($listenerPid) {
    Write-Host "Stopping existing process $listenerPid listening on port 8080..."
    Stop-Process -Id $listenerPid -Force -ErrorAction SilentlyContinue
    Start-Sleep -Seconds 2
}

Start-TrackedWindow -WorkingDirectory $backendDir -Command './mvnw.cmd spring-boot:run' -Label 'backend' -PrefixCommand $backendEnvBootstrap
Start-TrackedWindow -WorkingDirectory $webDir -Command 'npm run dev' -Label 'web'
Start-TrackedWindow -WorkingDirectory $mobileDir -Command './gradlew.bat assembleDebug' -Label 'mobile'

Write-Host "Full stack launch started. Tracked PIDs: $pidFile"
