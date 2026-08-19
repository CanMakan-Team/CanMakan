param(
    [Parameter(Mandatory = $true)]
    [ValidateSet('backend', 'web', 'mobile', 'fullstack')]
    [string]$Target
)

$ErrorActionPreference = 'Continue'
$pidFile = Join-Path $PSScriptRoot 'fullstack.pids'

function Stop-ProcessTree {
    param([Parameter(Mandatory = $true)][int]$ProcessId)

    if ($ProcessId -le 0) {
        return
    }
    # /T kills the whole tree (powershell wrapper + mvnw/java/node/gradle children).
    & taskkill.exe /PID $ProcessId /T /F 2>$null | Out-Null
}

function Stop-ListenersOnPort {
    param([Parameter(Mandatory = $true)][int]$Port)

    $ownerIds = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue |
        Select-Object -ExpandProperty OwningProcess -Unique

    foreach ($ownerId in $ownerIds) {
        Write-Host "Stopping listener PID $ownerId on port $Port..."
        Stop-ProcessTree -ProcessId $ownerId
    }
}

function Stop-ByCommandMatch {
    param([Parameter(Mandatory = $true)][string]$Pattern)

    Get-CimInstance Win32_Process -ErrorAction SilentlyContinue |
        Where-Object { $_.CommandLine -and ($_.CommandLine -match $Pattern) } |
        ForEach-Object {
            Write-Host "Stopping PID $($_.ProcessId) ($($_.Name))..."
            Stop-ProcessTree -ProcessId $_.ProcessId
        }
}

function Stop-TrackedFullStackWindows {
    if (-not (Test-Path $pidFile)) {
        return
    }

    Get-Content $pidFile | ForEach-Object {
        $line = $_.Trim()
        if (-not $line) {
            return
        }
        $parts = $line -split '\|', 2
        $trackedId = 0
        if (-not [int]::TryParse($parts[0], [ref]$trackedId)) {
            return
        }
        $label = if ($parts.Length -gt 1) { $parts[1] } else { 'tracked' }
        Write-Host "Stopping tracked $label window PID $trackedId..."
        Stop-ProcessTree -ProcessId $trackedId
    }

    Remove-Item -Force $pidFile -ErrorAction SilentlyContinue
}

switch ($Target) {
    'backend' {
        Stop-ByCommandMatch -Pattern 'spring-boot:run|BackendApplication'
        Stop-ListenersOnPort -Port 8080
    }
    'web' {
        Stop-ByCommandMatch -Pattern 'npm run dev|node_modules[\\/]vite|vite.js'
        Stop-ListenersOnPort -Port 5173
        Stop-ListenersOnPort -Port 4173
    }
    'mobile' {
        Stop-ByCommandMatch -Pattern 'assembleDebug'
    }
    'fullstack' {
        Stop-TrackedFullStackWindows
        # Fallback: catch orphans if PID file missing or children re-parented.
        Stop-ByCommandMatch -Pattern 'spring-boot:run|BackendApplication|npm run dev|node_modules[\\/]vite|vite.js|assembleDebug'
        Stop-ListenersOnPort -Port 8080
        Stop-ListenersOnPort -Port 5173
        Stop-ListenersOnPort -Port 4173
    }
}

Write-Host "Stopped $Target processes."
