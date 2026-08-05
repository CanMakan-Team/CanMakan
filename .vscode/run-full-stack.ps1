$ErrorActionPreference = 'Stop'

$workspace = Split-Path -Parent $PSScriptRoot

$backendDir = Join-Path $workspace 'server/backend'
$webDir = Join-Path $workspace 'client/web'
$mobileDir = Join-Path $workspace 'client/mobile'

Write-Host 'Starting backend...'
Start-Process powershell -ArgumentList "-NoExit", "-Command", "Set-Location '$backendDir'; ./mvnw spring-boot:run" -WindowStyle Normal

Write-Host 'Starting web app...'
Start-Process powershell -ArgumentList "-NoExit", "-Command", "Set-Location '$webDir'; npm run dev" -WindowStyle Normal

Write-Host 'Building mobile app...'
Start-Process powershell -ArgumentList "-NoExit", "-Command", "Set-Location '$mobileDir'; ./gradlew assembleDebug" -WindowStyle Normal

Write-Host 'Full stack launch started.'
