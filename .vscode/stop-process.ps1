param(
    [Parameter(Mandatory = $true)]
    [string]$Target
)

$ErrorActionPreference = 'Stop'

switch ($Target) {
    'backend' {
        Get-CimInstance Win32_Process | Where-Object { $_.Name -match 'java|mvnw|maven' -and $_.CommandLine -match 'spring-boot:run|mvnw' } | ForEach-Object { Stop-Process -Id $_.ProcessId -Force }
    }
    'web' {
        Get-CimInstance Win32_Process | Where-Object { $_.Name -match 'node|vite' -and $_.CommandLine -match 'vite|npm run dev' } | ForEach-Object { Stop-Process -Id $_.ProcessId -Force }
    }
    'mobile' {
        Get-CimInstance Win32_Process | Where-Object { $_.Name -match 'java|gradle|gradlew' -and $_.CommandLine -match 'gradlew|assembleDebug' } | ForEach-Object { Stop-Process -Id $_.ProcessId -Force }
    }
    'fullstack' {
        Get-CimInstance Win32_Process | Where-Object { $_.CommandLine -match 'run-full-stack.ps1|spring-boot:run|npm run dev|assembleDebug' } | ForEach-Object { Stop-Process -Id $_.ProcessId -Force }
    }
    default {
        Write-Error "Unknown target: $Target"
    }
}

Write-Host "Stopped $Target processes."
