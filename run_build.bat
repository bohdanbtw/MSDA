@echo off
cd /d "%~dp0"

powershell -ExecutionPolicy Bypass -File "AndroidCppApp\tools\build-android-package.ps1"
if %errorlevel% neq 0 (
    echo Build failed, skipping emulator launch.
    exit /b %errorlevel%
)

set "FOUND_APK="
for /f "delims=" %%i in ('dir /s /b "%~dp0AndroidCppApp\packaging\app\build\outputs\apk\*app-*.apk" 2^>nul') do (
    if not defined FOUND_APK set "FOUND_APK=%%i"
)
if not defined FOUND_APK (
    echo APK not found in packaging\app\build\outputs\apk. Build may have failed or output path is wrong.
    exit /b 1
)
set "APK_PATH=%FOUND_APK%"
echo APK located at %APK_PATH%
echo Launching emulator, installing APK, and starting the app...
powershell -ExecutionPolicy Bypass -File "AndroidCppApp\tools\run-android-emulator.ps1" -ApkPath "%APK_PATH%"
