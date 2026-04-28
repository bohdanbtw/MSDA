@echo off
cd /d "%~dp0"

powershell -ExecutionPolicy Bypass -File "AndroidCppApp\tools\build-android-package.ps1"
if %errorlevel% neq 0 (
    echo Build failed, skipping emulator launch.
    exit /b %errorlevel%
)

set "APK_PATH="
if exist "AndroidCppApp\out\apk-path.txt" (
    for /f "usebackq delims=" %%i in ("AndroidCppApp\out\apk-path.txt") do set "APK_PATH=%%i"
)
if not defined APK_PATH (
    echo APK path not found. Build may have succeeded but no APK was recorded.
    exit /b 1
)
echo APK located at %APK_PATH%
echo Launching emulator, installing APK, and starting the app...
powershell -ExecutionPolicy Bypass -File "AndroidCppApp\tools\run-android-emulator.ps1" -ApkPath "%APK_PATH%"
