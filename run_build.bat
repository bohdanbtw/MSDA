@echo off
cd /d "%~dp0"

powershell -ExecutionPolicy Bypass -File "AndroidCppApp\tools\build-android-package.ps1"
if %errorlevel% neq 0 (
    echo Build failed, skipping emulator launch.
    exit /b %errorlevel%
)

set "APK_PATH=%~dp0AndroidCppApp\packaging\app\build\outputs\apk\debug\app-debug.apk"
if not exist "%APK_PATH%" (
    echo APK not found at %APK_PATH%, build may have failed or path is wrong.
    exit /b 1
)

echo Launching emulator, installing APK, and starting the app...
powershell -ExecutionPolicy Bypass -File "AndroidCppApp\tools\run-android-emulator.ps1" -ApkPath "%APK_PATH%"
