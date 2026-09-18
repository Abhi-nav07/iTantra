.\gradlew.bat --stop
.\gradlew.bat clean
.\gradlew.bat :app:compileDebugKotlin
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleDebug

$apk = "app\build\outputs\apk\debug\app-debug.apk"
if (Test-Path $apk) {
    Write-Output "APK GENERATED:"
    Get-Item $apk | Select-Object FullName,Length
    Get-FileHash $apk -Algorithm SHA256
    Write-Output "JNI LIBS IN APK:"
    jar tf $apk | Select-String "lib/arm64-v8a"
} else {
    Write-Output "APK NOT FOUND"
}
