# Local smoke: health → validate → assess against a running CanMakan backend.
# Requires MySQL + seeded data and the Spring Boot app on BaseUrl.
param(
    [string] $BaseUrl = "http://localhost:8080",
    [string] $Barcode = "3017620422003",
    [long] $ProfileId = 1,
    [long] $UserId = 1
)

$ErrorActionPreference = "Stop"

function Write-Step([string] $Message) {
    Write-Host "`n=== $Message ===" -ForegroundColor Cyan
}

function Get-JsonOrThrow([Microsoft.PowerShell.Commands.WebResponseObject] $Response, [string] $Label) {
    if ($null -eq $Response) {
        throw "$Label returned no response"
    }
    if ($Response.StatusCode -lt 200 -or $Response.StatusCode -ge 300) {
        throw "$Label failed with HTTP $($Response.StatusCode): $($Response.Content)"
    }
    if ([string]::IsNullOrWhiteSpace($Response.Content)) {
        return $null
    }
    return $Response.Content | ConvertFrom-Json
}

Write-Step "Health"
try {
    $health = Invoke-WebRequest -Uri "$BaseUrl/actuator/health" -Method GET -UseBasicParsing
} catch {
    throw "Backend health check failed at $BaseUrl. Is the app running? $($_.Exception.Message)"
}
$healthJson = Get-JsonOrThrow $health "Health"
Write-Host "status=$($healthJson.status) HTTP=$($health.StatusCode)"

Write-Step "Validate barcode=$Barcode"
$validateBody = @{ barcode = $Barcode } | ConvertTo-Json
try {
    $validate = Invoke-WebRequest `
        -Uri "$BaseUrl/api/scan/validate" `
        -Method POST `
        -ContentType "application/json" `
        -Body $validateBody `
        -UseBasicParsing
} catch {
    throw "Validate failed: $($_.Exception.Message)"
}
$validateJson = Get-JsonOrThrow $validate "Validate"
Write-Host "HTTP=$($validate.StatusCode) validFood=$($validateJson.validFood) message=$($validateJson.message)"
if (-not $validateJson.validFood) {
    throw "Barcode is not a valid food product; assess skipped."
}

Write-Step "Assess profileId=$ProfileId userId=$UserId"
$assessBody = @{ barcode = $Barcode; profileId = $ProfileId } | ConvertTo-Json
try {
    $assess = Invoke-WebRequest `
        -Uri "$BaseUrl/api/scan/assess" `
        -Method POST `
        -ContentType "application/json" `
        -Headers @{ "X-User-Id" = "$UserId" } `
        -Body $assessBody `
        -UseBasicParsing
} catch {
    throw "Assess failed: $($_.Exception.Message)"
}
$assessJson = Get-JsonOrThrow $assess "Assess"

$level = $assessJson.verdict
$findings = $assessJson.findings

$codes = @()
if ($findings) {
    foreach ($f in $findings) {
        if ($f.restrictionCode) { $codes += $f.restrictionCode }
    }
}

Write-Host "HTTP=$($assess.StatusCode)"
Write-Host "verdictLevel=$level"
Write-Host "findingCodes=$([string]::Join(', ', $codes))"
if ($assessJson.scanId) { Write-Host "scanId=$($assessJson.scanId)" }
if ($assessJson.tier) { Write-Host "tier=$($assessJson.tier)" }

Write-Host "`nSmoke assess completed successfully." -ForegroundColor Green
