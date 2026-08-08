$ErrorActionPreference = 'Stop'

$workspace = Split-Path -Parent $PSScriptRoot
$pidFile = Join-Path $PSScriptRoot 'fullstack.pids'

$backendDir = Join-Path $workspace 'server/backend'
$webDir = Join-Path $workspace 'client/web'
$mobileDir = Join-Path $workspace 'client/mobile'

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
        [string]$Label
    )

    Write-Host "Starting $Label..."
    $process = Start-Process powershell -PassThru -WindowStyle Normal -ArgumentList @(
        '-NoExit',
        '-NoProfile',
        '-ExecutionPolicy', 'Bypass',
        '-Command',
        "Set-Location -LiteralPath '$WorkingDirectory'; $Command"
    )
    Add-Content -Path $pidFile -Value "$($process.Id)|$Label"
    return $process.Id
}

Start-TrackedWindow -WorkingDirectory $backendDir -Command './mvnw.cmd spring-boot:run' -Label 'backend'
Start-TrackedWindow -WorkingDirectory $webDir -Command 'npm run dev' -Label 'web'
Start-TrackedWindow -WorkingDirectory $mobileDir -Command './gradlew.bat assembleDebug' -Label 'mobile'

Write-Host "Full stack launch started. Tracked PIDs: $pidFile"
