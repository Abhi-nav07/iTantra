@echo off
echo ===========================================
echo iTANTRA - Smart India Hackathon 2026
echo ===========================================
echo Setting JAVA_HOME to local JDK...
set JAVA_HOME=%~dp0jdk-17.0.2

echo.
echo Building and installing on connected device...
echo Using --offline flag to prevent re-downloading dependencies.
call gradlew.bat installDebug --offline

if %errorlevel% neq 0 (
    echo.
    echo Build or Installation failed!
    echo Ensure your Android device/emulator is connected via ADB and try again.
    pause
    exit /b %errorlevel%
)

echo.
echo Starting the application...
adb shell am start -n com.itantra.app/com.itantra.app.MainActivity

if %errorlevel% neq 0 (
    echo.
    echo Failed to start the application!
    echo Ensure your device is authorized and unlocked.
    pause
    exit /b %errorlevel%
)

echo.
echo ===========================================
echo Application started successfully!
echo ===========================================
