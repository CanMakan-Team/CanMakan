$ErrorActionPreference = 'Stop'

$workspace = Split-Path -Parent $PSScriptRoot
$backendDir = Join-Path $workspace 'server/backend'
$mavenWrapper = Join-Path $backendDir 'mvnw.cmd'

$listenerPid = Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue |
    Select-Object -First 1 -ExpandProperty OwningProcess

if ($listenerPid) {
    Write-Host "Stopping existing process $listenerPid listening on port 8080..."
    Stop-Process -Id $listenerPid -Force -ErrorAction SilentlyContinue
    Start-Sleep -Seconds 2
}

Write-Host "Starting backend from $backendDir"
Start-Process -FilePath $mavenWrapper -ArgumentList 'spring-boot:run' -WorkingDirectory $backendDir -WindowStyle Normal | Out-Null
Write-Host 'Backend launch command issued.'
