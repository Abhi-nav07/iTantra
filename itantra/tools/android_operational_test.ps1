# iTantra Operational Reliability & Background/Lock-Screen Test Harness
# Pass E: Tests foreground service persistence, screen-off listening, and emergency state restoration.

$adb = "C:\Users\acer\AppData\Local\Android\Sdk\platform-tools\adb.exe"
$packageName = "com.itantra.app"
$serviceName = "com.itantra.core.service.OperationalForegroundService"
$timestamp = Get-Date -Format "yyyyMMdd_HHmmss"
$outDir = "performance_evidence/operational_$timestamp"

New-Item -ItemType Directory -Force -Path $outDir | Out-Null

Write-Host "============================================================" -ForegroundColor Cyan
Write-Host " iTantra Operational Hardening Test Harness (Pass E)" -ForegroundColor Cyan
Write-Host "============================================================" -ForegroundColor Cyan

if (-not (Test-Path $adb)) {
    Write-Warning "ADB not found at $adb. Skipping physical execution."
    exit 0
}

$devices = & $adb devices | Select-String -Pattern "device$"
if ($devices.Count -eq 0) {
    Write-Host "No physical Android devices attached." -ForegroundColor Yellow
    Write-Host "STATUS: FRAMEWORK PASS_SOURCE, PHYSICAL NOT_TESTED." -ForegroundColor Yellow
    exit 0
}

$deviceSerial = ($devices[0].Line -split "`t")[0].Trim()
Write-Host "Target Device: $deviceSerial" -ForegroundColor Green

# 1. App Foreground Launch
Write-Host "[1/8] Launching iTantra to Foreground..."
& $adb -s $deviceSerial shell am start -n "$packageName/$packageName.MainActivity"
Start-Sleep -Seconds 3

# 2. Check Package State
Write-Host "[2/8] Inspecting Package State..."
& $adb -s $deviceSerial shell dumpsys package $packageName > "$outDir/package_state.txt"

# 3. Trigger Continuous Mode Foreground Service
Write-Host "[3/8] Triggering Continuous Mode Foreground Service..."
& $adb -s $deviceSerial shell am start-foreground-service -n "$packageName/$serviceName" -a "com.itantra.action.START_CONTINUOUS"
Start-Sleep -Seconds 2
& $adb -s $deviceSerial shell dumpsys activity services $serviceName > "$outDir/continuous_service_state.txt"

# 4. App to Background (Home key)
Write-Host "[4/8] Sending App to Background..."
& $adb -s $deviceSerial shell input keyevent 3
Start-Sleep -Seconds 2

# 5. Screen Off Simulation (Power key)
Write-Host "[5/8] Simulating Screen Off..."
& $adb -s $deviceSerial shell input keyevent 26
Start-Sleep -Seconds 3

# 6. Verify Service Survives Screen-Off
Write-Host "[6/8] Verifying Foreground Service Survives Screen-Off..."
& $adb -s $deviceSerial shell dumpsys activity services $serviceName > "$outDir/screen_off_service_state.txt"

# 7. Screen Back On & Trigger Emergency Alert
Write-Host "[7/8] Screen Back On & Injecting Emergency Alert..."
& $adb -s $deviceSerial shell input keyevent 26
& $adb -s $deviceSerial shell am start-foreground-service -n "$packageName/$serviceName" -a "com.itantra.action.TRIGGER_EMERGENCY" --es "extra_emergency_text" "CRITICAL MEDICAL EMERGENCY"
Start-Sleep -Seconds 2
& $adb -s $deviceSerial shell dumpsys notification > "$outDir/emergency_notification_state.txt"

# 8. Activity Recreation / App Restart & SOS State Restoration
Write-Host "[8/8] Testing Activity Recreation & Persistent SOS Restoration..."
& $adb -s $deviceSerial shell am kill $packageName
Start-Sleep -Seconds 2
& $adb -s $deviceSerial shell am start -n "$packageName/$packageName.MainActivity"
Start-Sleep -Seconds 3
& $adb -s $deviceSerial logcat -d -s "iTantra:*" "OperationalForegroundService:*" > "$outDir/logcat_restoration.txt"

Write-Host "Operational test complete. Artifacts stored in $outDir" -ForegroundColor Green
