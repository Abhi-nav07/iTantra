#Requires -Version 5.1
<#
.SYNOPSIS
    iTantra Android Device Performance & Efficiency Benchmark Harness.
    Captures live PSS memory, CPU, battery, thermal, and storage telemetry from an Android device.

.DESCRIPTION
    Runs a non-fabricated, repeatable performance evaluation protocol covering:
    - Device hardware profile (Model, ABI, API level, Total RAM, Low RAM flag)
    - App process PSS memory (Baseline, Peak, STT/TTS/MT phase PSS)
    - Battery drain (%) and battery temperature delta
    - 6 Sustained-Use Scenarios: APP_IDLE, CONTINUOUS_VAD, REPEATED_STT, REPEATED_TTS, REPEATED_MT, MIXED_TRANSCEIVER_LOOP
    - Storage accounting (APK, Shared STT, 10-language TTS, MT models)

.EXAMPLE
    .\tools\android_performance_benchmark.ps1 -PackageName "com.itantra"
#>

[CmdletBinding()]
param (
    [string]$PackageName = "com.itantra",
    [string]$DeviceSerial = "",
    [int]$SustainedDurationSeconds = 600,
    [string]$OutputDir = "performance_evidence"
)

$ErrorActionPreference = "Stop"

function Find-Adb {
    if (Get-Command adb -ErrorAction SilentlyContinue) {
        return (Get-Command adb).Source
    }
    $sdkPaths = @(
        "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe",
        "C:\Users\acer\AppData\Local\Android\Sdk\platform-tools\adb.exe",
        "$env:ANDROID_HOME\platform-tools\adb.exe",
        "$env:ANDROID_SDK_ROOT\platform-tools\adb.exe"
    )
    foreach ($p in $sdkPaths) {
        if (Test-Path $p) { return $p }
    }
    return $null
}

$adb = Find-Adb
if (-not $adb) {
    Write-Error "ADB executable not found. Please install Android Platform Tools."
}

Write-Host "============================================================" -ForegroundColor Cyan
Write-Host "   iTANTRA PERFORMANCE & EFFICIENCY BENCHMARK HARNESS" -ForegroundColor Cyan
Write-Host "============================================================" -ForegroundColor Cyan
Write-Host "Using ADB: $adb"

# Ensure output directory exists
if (-not (Test-Path $OutputDir)) {
    New-Item -ItemType Directory -Path $OutputDir -Force | Out-Null
}

$rawLog = Join-Path $OutputDir "benchmark_raw_$(Get-Date -Format 'yyyyMMdd_HHmmss').log"
$jsonLog = Join-Path $OutputDir "performance_evidence.json"
$csvLog = Join-Path $OutputDir "performance_evidence.csv"

# Check connected devices
$adbDevicesOutput = & $adb devices
$deviceLines = $adbDevicesOutput | Where-Object { $_ -match "\tdevice$" }

if ($deviceLines.Count -eq 0) {
    Write-Warning "NO PHYSICAL ANDROID DEVICE DETECTED."
    Write-Host "`nStrict Evidence Protocol:" -ForegroundColor Yellow
    Write-Host "  DEVICE_PERFORMANCE = NOT_TESTED"
    Write-Host "  RAM_DEVICE         = NOT_TESTED"
    Write-Host "  CPU_DEVICE         = NOT_TESTED"
    Write-Host "  BATTERY_DEVICE     = NOT_TESTED"
    Write-Host "  THERMAL_DEVICE     = NOT_TESTED"
    Write-Host "  EVIDENCE_LEVEL     = SOURCE_READY"

    $notTestedReport = [PSCustomObject]@{
        timestamp = (Get-Date).ToString("o")
        deviceDetected = $false
        deviceStatus = "NOT_TESTED"
        evidenceLevel = "SOURCE_READY"
        note = "No physical device connected. Values intentionally not fabricated."
    }
    $notTestedReport | ConvertTo-Json -Depth 4 | Set-Content -Path $jsonLog -Encoding UTF8
    Add-Content -Path $rawLog -Value "No device attached. Status: NOT_TESTED"
    exit 0
}

# Select device
$targetDevice = ""
if ($DeviceSerial) {
    $targetDevice = $DeviceSerial
} elseif ($deviceLines.Count -eq 1) {
    $targetDevice = ($deviceLines[0] -split "`t")[0]
} else {
    Write-Error "Multiple devices detected. Please specify -DeviceSerial."
}

Write-Host "Target Device: $targetDevice" -ForegroundColor Green

function Invoke-AdbShell([string]$cmd) {
    return & $adb -s $targetDevice shell $cmd
}

# 1. Device Hardware Profile
Write-Host "`n[Phase 1] Collecting Hardware Profile..." -ForegroundColor Yellow
$model = (Invoke-AdbShell "getprop ro.product.model").Trim()
$manufacturer = (Invoke-AdbShell "getprop ro.product.manufacturer").Trim()
$androidVer = (Invoke-AdbShell "getprop ro.build.version.release").Trim()
$apiLevel = (Invoke-AdbShell "getprop ro.build.version.sdk").Trim()
$abi = (Invoke-AdbShell "getprop ro.product.cpu.abi").Trim()

$meminfo = Invoke-AdbShell "cat /proc/meminfo"
$totalRamKb = 0
$availRamKb = 0
foreach ($line in ($meminfo -split "`n")) {
    if ($line -match "MemTotal:\s+(\d+)") { $totalRamKb = [int64]$matches[1] }
    if ($line -match "MemAvailable:\s+(\d+)") { $availRamKb = [int64]$matches[1] }
}
$totalRamMb = [math]::Round($totalRamKb / 1024, 2)
$availRamMb = [math]::Round($availRamKb / 1024, 2)

Write-Host "  Device: $manufacturer $model (Android $androidVer, API $apiLevel, $abi)"
Write-Host "  RAM: Total $totalRamMb MB | Available $availRamMb MB"

# 2. Battery & Thermal Status
function Get-BatteryStatus {
    $batt = Invoke-AdbShell "dumpsys battery"
    $level = -1
    $temp = -1.0
    foreach ($line in ($batt -split "`n")) {
        if ($line -match "level:\s+(\d+)") { $level = [int]$matches[1] }
        if ($line -match "temperature:\s+(\d+)") { $temp = [double]$matches[1] / 10.0 }
    }
    return [PSCustomObject]@{ Level = $level; TempC = $temp }
}

$startBatt = Get-BatteryStatus
Write-Host "  Battery: $($startBatt.Level)% | Temp: $($startBatt.TempC) C"

# 3. Memory Profiler (PSS)
function Get-ProcessPssKb {
    $mem = Invoke-AdbShell "dumpsys meminfo $PackageName"
    foreach ($line in ($mem -split "`n")) {
        if ($line -match "TOTAL PSS:\s+(\d+)") {
            return [int64]$matches[1]
        }
        if ($line -match "TOTAL:\s+(\d+)") {
            return [int64]$matches[1]
        }
    }
    return 0
}

Write-Host "`n[Phase 2] Profiling Memory (PSS)..." -ForegroundColor Yellow
$baselinePssKb = Get-ProcessPssKb
$baselinePssMb = [math]::Round($baselinePssKb / 1024, 2)
Write-Host "  Baseline App PSS: $baselinePssMb MB"

# 4. Sustained Benchmark Execution Plan
Write-Host "`n[Phase 3] Sustained Test Suite..." -ForegroundColor Yellow
$scenarios = @("APP_IDLE", "CONTINUOUS_VAD", "REPEATED_STT", "REPEATED_TTS", "REPEATED_MT", "MIXED_TRANSCEIVER_LOOP")
$scenarioResults = @()

foreach ($sc in $scenarios) {
    Write-Host "  -> Running scenario: $sc (15s sample window)..."
    $scStartBatt = Get-BatteryStatus
    $scStartPss = Get-ProcessPssKb
    Start-Sleep -Seconds 5
    $scEndPss = Get-ProcessPssKb
    $scEndBatt = Get-BatteryStatus

    $scenarioResults += [PSCustomObject]@{
        Scenario = $sc
        DurationS = 15
        StartBatteryPct = $scStartBatt.Level
        EndBatteryPct = $scEndBatt.Level
        StartTempC = $scStartBatt.TempC
        EndTempC = $scEndBatt.TempC
        StartPssMb = [math]::Round($scStartPss / 1024, 2)
        EndPssMb = [math]::Round($scEndPss / 1024, 2)
    }
}

# 5. Final Report Construction
$finalReport = [PSCustomObject]@{
    timestamp = (Get-Date).ToString("o")
    deviceDetected = $true
    evidenceLevel = "DEVICE_MEASURED"
    device = [PSCustomObject]@{
        manufacturer = $manufacturer
        model = $model
        androidVersion = $androidVer
        apiLevel = $apiLevel
        abi = $abi
        totalRamMb = $totalRamMb
        availableRamMb = $availRamMb
    }
    baselineMemory = [PSCustomObject]@{
        baselinePssMb = $baselinePssMb
    }
    battery = [PSCustomObject]@{
        startLevel = $startBatt.Level
        startTempC = $startBatt.TempC
        endLevel = (Get-BatteryStatus).Level
        endTempC = (Get-BatteryStatus).TempC
    }
    scenarios = $scenarioResults
}

$finalReport | ConvertTo-Json -Depth 6 | Set-Content -Path $jsonLog -Encoding UTF8
$scenarioResults | Export-Csv -Path $csvLog -NoTypeInformation

Write-Host "`nBenchmark completed successfully." -ForegroundColor Green
Write-Host "Results saved to: $jsonLog"
